package com.carlesso.goalfather.adapter.out.persistence

import com.carlesso.goalfather.adapter.out.persistence.mapper.toDomain
import com.carlesso.goalfather.adapter.out.persistence.mapper.toEntity
import com.carlesso.goalfather.adapter.out.persistence.repository.CupJpaRepository
import com.carlesso.goalfather.application.port.out.CupRepository
import com.carlesso.goalfather.domain.model.Cup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Repository

@Repository
class CupPersistenceAdapter(
    private val cupRepo: CupJpaRepository,
    private val json: Json,
) : CupRepository {

    override suspend fun findBySeason(season: Int): Cup? = withContext(Dispatchers.IO) {
        cupRepo.findById(season).orElse(null)?.toDomain(json)
    }

    override suspend fun findLatest(): Cup? = withContext(Dispatchers.IO) {
        cupRepo.findTopByOrderBySeasonDesc()?.toDomain(json)
    }

    override suspend fun save(cup: Cup) {
        withContext(Dispatchers.IO) {
            cupRepo.save(cup.toEntity(json))
        }
    }
}
