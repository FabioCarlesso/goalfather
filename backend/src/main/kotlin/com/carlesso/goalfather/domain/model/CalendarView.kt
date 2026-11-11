package com.carlesso.goalfather.domain.model

/**
 * O que a tela mostra do relógio do jogo (issue #125): o encaixe corrente, a
 * temporada e o próximo jogo do clube — com a competição dele, que vem na
 * própria partida.
 */
data class CalendarView(
    val current: Matchday?,
    val season: Int?,
    val nextMatch: NextMatch?,
)

/** O próximo jogo de um clube e o encaixe em que ele cai. */
data class NextMatch(val matchday: Matchday, val match: RoundMatch)
