package com.carlesso.goalfather.adapter.out.persistence

import com.carlesso.goalfather.adapter.out.persistence.mapper.toDomain
import com.carlesso.goalfather.adapter.out.persistence.mapper.toEntity
import com.carlesso.goalfather.adapter.out.persistence.repository.SeasonHistoryJpaRepository
import com.carlesso.goalfather.application.port.out.SeasonHistoryRepository
import com.carlesso.goalfather.domain.model.SeasonRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Repository

@Repository
class SeasonHistoryPersistenceAdapter(
    private val historyRepo: SeasonHistoryJpaRepository,
    private val json: Json,
) : SeasonHistoryRepository {

    override suspend fun findAll(): List<SeasonRecord> = withContext(Dispatchers.IO) {
        historyRepo.findAllByOrderBySeasonDesc().map { it.toDomain(json) }
    }

    override suspend fun findBySeason(season: Int): SeasonRecord? = withContext(Dispatchers.IO) {
        historyRepo.findById(season).orElse(null)?.toDomain(json)
    }

    /**
     * Insere se a temporada ainda não tem record.
     *
     * Duas defesas, como no claim da rodada (issue #46): o `existsById`
     * resolve o caso comum (retardatário chegando muito depois) sem escrever
     * nada, e a colisão de PK resolve a corrida real entre duas réplicas —
     * `existsById` + gravação NÃO é atômico, então sozinho ele deixaria dois
     * nós passarem e o segundo sobrescreveria o campeão do primeiro.
     *
     * Duas condições fazem a segunda defesa existir de fato:
     * - `SeasonHistoryEntity` é `Persistable` com `isNew() = true`, senão o
     *   Spring Data faria `merge` (UPDATE) em vez de INSERT e a PK nunca seria
     *   violada — era o buraco que a review do PR #76 pegou;
     * - `saveAndFlush` em vez de `save`, para o INSERT sair AQUI e não no
     *   commit, fora deste `try` (aí a violação subiria como erro).
     */
    override suspend fun append(record: SeasonRecord): Boolean = withContext(Dispatchers.IO) {
        if (historyRepo.existsById(record.season)) return@withContext false
        try {
            historyRepo.saveAndFlush(record.toEntity(json))
            true
        } catch (_: DataIntegrityViolationException) {
            false
        }
    }
}
