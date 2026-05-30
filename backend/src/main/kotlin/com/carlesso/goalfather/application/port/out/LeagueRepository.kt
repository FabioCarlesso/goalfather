package com.carlesso.goalfather.application.port.out

import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.Standings

interface LeagueRepository {
    suspend fun findRound(number: Int): Round?

    /** Rodada de maior número (a "atual" do calendário). `null` se não há rodadas. */
    suspend fun findLatest(): Round?

    suspend fun currentStandings(): Standings
    suspend fun saveRound(round: Round)
    suspend fun saveStandings(standings: Standings)
}
