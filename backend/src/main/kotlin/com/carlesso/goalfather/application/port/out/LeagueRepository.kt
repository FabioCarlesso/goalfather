package com.carlesso.goalfather.application.port.out

import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.Standings

interface LeagueRepository {
    suspend fun findRound(number: Int): Round?

    /** Rodada de maior número (a "atual" do calendário). `null` se não há rodadas. */
    suspend fun findLatest(): Round?

    /** Tabela da temporada ativa (a de maior `season`). */
    suspend fun currentStandings(): Standings

    /** Tabela de uma temporada específica (histórico); `null` se não existir. */
    suspend fun findStandings(season: Int): Standings?

    suspend fun saveRound(round: Round)
    suspend fun saveStandings(standings: Standings)
}
