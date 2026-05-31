package com.carlesso.goalfather.adapter.out.persistence

import com.carlesso.goalfather.adapter.out.persistence.mapper.toDomain
import com.carlesso.goalfather.adapter.out.persistence.mapper.toEntity
import com.carlesso.goalfather.adapter.out.persistence.repository.RoundJpaRepository
import com.carlesso.goalfather.adapter.out.persistence.repository.StandingsJpaRepository
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.Standings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class LeaguePersistenceAdapter(
    private val roundRepo: RoundJpaRepository,
    private val standingsRepo: StandingsJpaRepository,
    private val json: Json,
) : LeagueRepository {

    override suspend fun findRound(number: Int): Round? = withContext(Dispatchers.IO) {
        roundRepo.findById(number).orElse(null)?.toDomain(json)
    }

    override suspend fun findLatest(): Round? = withContext(Dispatchers.IO) {
        roundRepo.findTopByOrderByNumberDesc()?.toDomain(json)
    }

    override suspend fun currentStandings(): Standings = withContext(Dispatchers.IO) {
        // Temporada ativa = maior `season`. Ao virar o ano, a tabela anterior
        // permanece no DB (PK por season) e esta passa a apontar para a nova.
        val entity = standingsRepo.findTopByOrderBySeasonDesc()
            ?: throw IllegalStateException("Standings nao inicializada — seed faltando")
        entity.toDomain(json)
    }

    override suspend fun findStandings(season: Int): Standings? = withContext(Dispatchers.IO) {
        standingsRepo.findById(season).orElse(null)?.toDomain(json)
    }

    @Transactional
    override suspend fun saveRound(round: Round) {
        withContext(Dispatchers.IO) {
            roundRepo.save(round.toEntity(json))
        }
    }

    @Transactional
    override suspend fun saveStandings(standings: Standings) {
        withContext(Dispatchers.IO) {
            standingsRepo.save(standings.toEntity(json))
        }
    }
}
