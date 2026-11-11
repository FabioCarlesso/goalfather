package com.carlesso.goalfather.domain.model

/**
 * Ids e nomes das competições do mundo ATUAL do jogo (issue #125): uma liga de
 * pontos corridos por divisão + a copa. Com o calendário semanal cada partida
 * diz a que competição pertence, e estes são os ids que o seed usa.
 *
 * **Interino** (mesmo padrão de `Countries`): quando o mundo do MVP sair do
 * pack (issue #131), as competições vêm do `WorldDataProvider` com os ids do
 * pack (`bra-serie-a`, `bra-copa-do-brasil`…) e este objeto deixa de existir.
 *
 * `object` porque é um catálogo sem estado — o singleton do Kotlin, sem a
 * classe com construtor privado + `static` do Java.
 */
object SeasonCompetitions {
    /** A copa da temporada (issue #61). */
    val CUP_ID: CompetitionId = CompetitionId("copa")
    const val CUP_NAME: String = "Copa"

    /** A liga da [division]: `liga-d1`, `liga-d2`… */
    fun leagueId(division: Division): CompetitionId = CompetitionId("liga-d${division.value}")

    fun leagueName(division: Division): String = "Liga — Divisão ${division.value}"
}
