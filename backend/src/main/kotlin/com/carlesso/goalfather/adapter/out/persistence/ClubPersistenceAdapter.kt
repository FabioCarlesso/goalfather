package com.carlesso.goalfather.adapter.out.persistence

import com.carlesso.goalfather.adapter.out.persistence.entity.ClubEntity
import com.carlesso.goalfather.adapter.out.persistence.mapper.applyBoard
import com.carlesso.goalfather.adapter.out.persistence.mapper.toDomain
import com.carlesso.goalfather.adapter.out.persistence.mapper.toEntity
import com.carlesso.goalfather.adapter.out.persistence.repository.ClubJpaRepository
import com.carlesso.goalfather.adapter.out.persistence.repository.PlayerJpaRepository
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.ClubSaveResult
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Lineup
import com.carlesso.goalfather.domain.model.TrainingFocus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.springframework.dao.ConcurrencyFailureException
import org.springframework.stereotype.Component
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
 * estavam livres; remove se foram tirados do elenco) — na mesma transação
 * do clube, sob o lock otimista do agregado (issue #89).
 */
@Repository
class ClubPersistenceAdapter(
    private val clubRepo: ClubJpaRepository,
    private val playerRepo: PlayerJpaRepository,
    private val saveTx: ClubSaveTransaction,
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

    /**
     * Gravação sob lock otimista (issue #89). A coroutine só faz a ponte para o
     * pool de I/O; a unidade transacional roda em [ClubSaveTransaction] (método
     * **não-suspend**) pelo mesmo motivo do claim (issue #19): `@Transactional`
     * sobre função `suspend` é frágil — o `withContext` troca de thread e a
     * transação é thread-bound. Assim clube E elenco caem juntos num rollback.
     *
     * O `catch` é a rede de segurança: o compare-and-set da transação já
     * devolve `Conflict` no caso comum, mas o banco ainda pode recusar a
     * escrita concorrente por outra via — o `@Version` no flush
     * ([org.springframework.dao.OptimisticLockingFailureException]) ou um
     * timeout de lock de linha enquanto a outra transação não comita (H2).
     * Todos descendem de [ConcurrencyFailureException] e significam a mesma
     * coisa para o chamador — "alguém gravou antes, nada foi aplicado" —, então
     * viram o mesmo valor aqui, sem exception chegando ao use case nem ao
     * controller.
     */
    override suspend fun save(club: Club): ClubSaveResult = withContext(Dispatchers.IO) {
        try {
            saveTx.save(club)
        } catch (_: ConcurrencyFailureException) {
            ClubSaveResult.Conflict(club.id)
        }
    }

    override suspend fun updateTicketPrice(id: ClubId, ticketPriceCents: Long): Club? =
        withContext(Dispatchers.IO) { saveTx.updateTicketPrice(id.value, ticketPriceCents) }

    override suspend fun updateTrainingFocus(id: ClubId, focus: TrainingFocus): Club? =
        withContext(Dispatchers.IO) { saveTx.updateTrainingFocus(id.value, focus.name) }
}

/**
 * Transação síncrona do `save` de clube (issue #89). Bean separado para que
 * `@Transactional` passe pelo proxy do Spring — self-invocation não passaria.
 */
@Component
class ClubSaveTransaction(
    private val clubRepo: ClubJpaRepository,
    private val playerRepo: PlayerJpaRepository,
    private val json: Json,
) {
    @Transactional
    fun save(club: Club): ClubSaveResult {
        val id = club.id.value

        // Lock otimista contra a versão que o USE CASE leu (`club.version`), e
        // não contra uma releitura feita aqui — esse era o bug da issue #89: o
        // adapter recarregava a entidade e o `@Version` passava a comparar com
        // a versão recém-lida, então qualquer escrita intercalada (virada da
        // rodada × ampliar estádio) sumia em silêncio.
        //
        // `when` sem argumento: cada ramo é uma condição, avaliada em ordem —
        // o `return` no meio sai da função direto, sem `if/else` aninhado.
        val entity = when {
            clubRepo.compareAndIncrementVersion(id, club.version) == 1 ->
                clubRepo.findById(id).orElseThrow()
            clubRepo.existsById(id) -> return ClubSaveResult.Conflict(club.id)
            // Clube ainda não persistido (seed, testes): INSERT com version 0.
            else -> ClubEntity(id = id)
        }

        entity.name = club.name
        entity.cash = club.cash
        entity.stadiumCapacity = club.stadiumCapacity
        entity.lineupJson = club.lineup?.let { json.encodeToString(Lineup.serializer(), it) }
        entity.ownerId = club.ownerId
        entity.division = club.division.value
        entity.trainingFocus = club.trainingFocus.name
        entity.ticketPriceCents = club.ticketPriceCents
        entity.applyBoard(club.board)
        // `saveAndFlush`: o UPDATE (e a checagem do `@Version`) acontece AGORA,
        // então `saved.version` já é a versão que fica no banco após o commit.
        val saved = clubRepo.saveAndFlush(entity)

        // Sincroniza o elenco. Players que estavam no clube e nao estao
        // mais ficam com clubId = null (vao para o mercado via SellPlayer).
        val currentPlayerIds = playerRepo.findAllByClubId(id).map { it.id }.toSet()
        val newPlayerIds = club.squad.map { it.id.value }.toSet()

        val removed = currentPlayerIds - newPlayerIds
        for (playerId in removed) {
            val p = playerRepo.findById(playerId).orElse(null) ?: continue
            p.clubId = null
            playerRepo.save(p)
        }

        // Atualiza/adiciona players do squad
        for (player in club.squad) {
            playerRepo.save(player.toEntity(clubId = id))
        }

        return ClubSaveResult.Saved(club.copy(version = saved.version))
    }

    /** UPDATE de coluna do preço + releitura, na mesma transação. */
    @Transactional
    fun updateTicketPrice(id: Long, ticketPriceCents: Long): Club? =
        reloadIfUpdated(id, clubRepo.updateTicketPrice(id, ticketPriceCents))

    /** UPDATE de coluna do foco de treino + releitura, na mesma transação. */
    @Transactional
    fun updateTrainingFocus(id: Long, focus: String): Club? =
        reloadIfUpdated(id, clubRepo.updateTrainingFocus(id, focus))

    private fun reloadIfUpdated(id: Long, updatedRows: Int): Club? {
        if (updatedRows == 0) return null
        val entity = clubRepo.findById(id).orElse(null) ?: return null
        return entity.toDomain(playerRepo.findAllByClubId(id), json)
    }
}
