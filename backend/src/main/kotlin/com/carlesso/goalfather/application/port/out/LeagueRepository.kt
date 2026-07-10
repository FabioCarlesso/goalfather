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

    /**
     * Transição atômica `Scheduled → InProgress` (issue #46).
     *
     * `true` = este chamador destravou a rodada. `false` = ela já não estava
     * `Scheduled` — outra instância chegou antes, ou a rodada não existe. O
     * chamador trata `false` como sucesso idempotente: o efeito desejado
     * (rodada liberada) já vale.
     */
    suspend fun startRound(roundNumber: Int): Boolean

    /**
     * Transição atômica `→ Finished`, gravando os placares de [round] (issue
     * #46). É o **ponto de serialização** do encerramento da rodada.
     *
     * `true` = este chamador venceu a corrida e é o único autorizado a aplicar
     * os efeitos (caixa, estatísticas, tabela, próxima rodada). `false` = outra
     * instância já finalizou; o perdedor deve apenas fazer replay, sem tocar em
     * nada. Substitui o `Mutex` in-JVM, que só valia com instância única.
     */
    suspend fun finishRound(round: Round): Boolean
}
