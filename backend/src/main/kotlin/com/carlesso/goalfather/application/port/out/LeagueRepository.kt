package com.carlesso.goalfather.application.port.out

import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.Standings

interface LeagueRepository {
    suspend fun findRound(number: Int): Round?

    /**
     * A "atual" do calendário: a data de maior número da temporada MAIS
     * RECENTE. `null` se não há rodadas. Datas de temporadas encerradas
     * continuam na tabela (a PK é só o número), então a temporada entra na
     * ordenação antes do número.
     */
    suspend fun findLatest(): Round?

    /**
     * Tabelas da temporada ativa (a de maior `season`) — uma por divisão,
     * ordenadas da elite para baixo (issue #47).
     */
    suspend fun currentStandings(): List<Standings>

    /**
     * Tabelas de uma temporada específica (histórico), uma por divisão;
     * lista vazia se a temporada não existir.
     */
    suspend fun findStandings(season: Int): List<Standings>

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
