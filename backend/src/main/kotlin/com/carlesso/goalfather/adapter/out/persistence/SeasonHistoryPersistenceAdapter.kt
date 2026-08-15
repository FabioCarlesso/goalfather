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
     * nada, e a colisão de PK resolve a corrida real entre duas réplicas. O
     * `saveAndFlush` é obrigatório para a segunda funcionar — com `save`, o
     * INSERT só sairia no commit, FORA deste `try`, e a violação subiria como
     * erro em vez de virar `false`.
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
