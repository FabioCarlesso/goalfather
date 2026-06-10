package com.carlesso.goalfather.adapter.out.persistence

import com.carlesso.goalfather.adapter.out.persistence.entity.ClubEntity
import com.carlesso.goalfather.adapter.out.persistence.mapper.toDomain
import com.carlesso.goalfather.adapter.out.persistence.mapper.toEntity
import com.carlesso.goalfather.adapter.out.persistence.repository.ClubJpaRepository
import com.carlesso.goalfather.adapter.out.persistence.repository.PlayerJpaRepository
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Lineup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

/**
 * Implementação do port `ClubRepository` sobre Spring Data JPA.
 *
 * Spring Data não é coroutine-aware — `withContext(Dispatchers.IO)`
 * faz a ponte: chamadas suspend rodam num pool de I/O sem bloquear o
 * dispatcher principal (e mantém o port-suspend contract).
 *
 * Squad é gerenciado pela relação `Player.clubId`. Salvar um clube
 * sincroniza o `clubId` em todos os players passados (atribui se
 * estavam livres; remove se foram tirados do elenco).
 */
@Repository
class ClubPersistenceAdapter(
    private val clubRepo: ClubJpaRepository,
    private val playerRepo: PlayerJpaRepository,
    private val json: Json,
) : ClubRepository {

    override suspend fun findById(id: ClubId): Club? = withContext(Dispatchers.IO) {
        val entity = clubRepo.findById(id.value).orElse(null) ?: return@withContext null
        val players = playerRepo.findAllByClubId(id.value)
        entity.toDomain(players, json)
    }

    override suspend fun findAll(): List<Club> = withContext(Dispatchers.IO) {
        val entities = clubRepo.findAll()
        // Uma só query para o elenco de todos os clubes, agrupado em memória —
        // antes era 1 query de players por clube (N+1, issue #23).
        val playersByClub = playerRepo
            .findAllByClubIdIn(entities.map { it.id })
            .groupBy { it.clubId }
        entities.map { entity ->
            entity.toDomain(playersByClub[entity.id].orEmpty(), json)
        }
    }

    override suspend fun findAvailable(): List<Club> = withContext(Dispatchers.IO) {
        clubRepo.findAllByOwnerIdIsNull().map { entity ->
            val players = playerRepo.findAllByClubId(entity.id)
            entity.toDomain(players, json)
        }
    }

    @Transactional
    override suspend fun save(club: Club): Club = withContext(Dispatchers.IO) {
        // Carrega a entidade EXISTENTE e copia os campos do domínio nela, em vez
        // de mapear para uma entidade nova. Isso preserva o @Version (lock
        // otimista do claim, issue #19): mapear para uma instância destacada com
        // version=0 faria o Hibernate ver um update obsoleto contra a versão do
        // banco (≥1 após o claim) → StaleObjectStateException em todo save de
        // clube (ampliar estádio, escalação, compra/venda).
        val entity = clubRepo.findById(club.id.value).orElseGet { ClubEntity(id = club.id.value) }
        entity.name = club.name
        entity.cash = club.cash
        entity.stadiumCapacity = club.stadiumCapacity
        entity.lineupJson = club.lineup?.let { json.encodeToString(Lineup.serializer(), it) }
        entity.ownerId = club.ownerId
        clubRepo.save(entity)

        // Sincroniza o elenco. Players que estavam no clube e nao estao
        // mais ficam com clubId = null (vao para o mercado via SellPlayer).
        val currentPlayerIds = playerRepo.findAllByClubId(club.id.value).map { it.id }.toSet()
        val newPlayerIds = club.squad.map { it.id.value }.toSet()

        val removed = currentPlayerIds - newPlayerIds
        if (removed.isNotEmpty()) {
            for (id in removed) {
                val p = playerRepo.findById(id).orElse(null) ?: continue
                p.clubId = null
                playerRepo.save(p)
            }
        }

        // Atualiza/adiciona players do squad
        for (player in club.squad) {
            val p = player.toEntity(clubId = club.id.value)
            playerRepo.save(p)
        }

        club
    }
}
