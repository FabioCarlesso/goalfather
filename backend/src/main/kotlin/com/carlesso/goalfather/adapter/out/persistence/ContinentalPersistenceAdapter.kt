package com.carlesso.goalfather.adapter.out.persistence

import com.carlesso.goalfather.adapter.out.persistence.entity.ContinentalEditionEntity
import com.carlesso.goalfather.adapter.out.persistence.entity.ContinentalEditionId
import com.carlesso.goalfather.adapter.out.persistence.repository.ContinentalEditionJpaRepository
import com.carlesso.goalfather.application.port.out.ContinentalRepository
import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.ContinentalCup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Repository

/** Adapter do port [ContinentalRepository] (issue #132): a edição em JSON, como a copa. */
@Repository
class ContinentalPersistenceAdapter(
    private val repo: ContinentalEditionJpaRepository,
    private val json: Json,
) : ContinentalRepository {

    override suspend fun find(competition: CompetitionId, season: Int): ContinentalCup? = withContext(Dispatchers.IO) {
        repo.findById(ContinentalEditionId(competition.value, season)).orElse(null)?.toDomain(json)
    }

    override suspend fun findLatest(competition: CompetitionId): ContinentalCup? = withContext(Dispatchers.IO) {
        repo.findTopByCompetitionIdOrderBySeasonDesc(competition.value)?.toDomain(json)
    }

    override suspend fun save(edition: ContinentalCup) {
        withContext(Dispatchers.IO) { repo.save(edition.toEntity(json)) }
    }
}

/** Entidade → domínio. */
fun ContinentalEditionEntity.toDomain(json: Json): ContinentalCup =
    json.decodeFromString(ContinentalCup.serializer(), editionJson)

/** Domínio → entidade. Público: o `SeedWriter` grava a 1ª edição com ele. */
fun ContinentalCup.toEntity(json: Json): ContinentalEditionEntity = ContinentalEditionEntity(
    competitionId = competitionId.value,
    season = season,
    editionJson = json.encodeToString(ContinentalCup.serializer(), this),
)
