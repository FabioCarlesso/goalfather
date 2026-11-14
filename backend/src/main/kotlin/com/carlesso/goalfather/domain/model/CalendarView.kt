package com.carlesso.goalfather.domain.model

/**
 * O que a tela mostra do relógio do jogo (issue #125): o encaixe corrente, a
 * temporada e o próximo jogo do clube — com a competição dele, que vem na
 * própria partida.
 *
 * [season]/[seasonLabel] (issue #131): com um clube, a temporada da LIGA dele
 * (`2026` e `2026/27` na Europa); sem clube, o ano civil do encaixe e nenhum
 * rótulo — o mundo não tem "a" temporada, cada país tem a sua.
 */
data class CalendarView(
    val current: Matchday?,
    val season: Int?,
    val nextMatch: NextMatch?,
    val seasonLabel: String? = null,
)

/** O próximo jogo de um clube e o encaixe em que ele cai. */
data class NextMatch(val matchday: Matchday, val match: RoundMatch)
