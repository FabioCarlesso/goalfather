package com.carlesso.goalfather.domain.model

/**
 * Linha da tabela — estatísticas acumuladas de um clube na temporada.
 */
data class StandingRow(
    val position: Int,
    val clubId: ClubId,
    val clubName: String,
    val played: Int = 0,
    val wins: Int = 0,
    val draws: Int = 0,
    val losses: Int = 0,
    val goalsFor: Int = 0,
    val goalsAgainst: Int = 0,
    val goalDifference: Int = 0,
    val points: Int = 0,
)

/**
 * Tabela de classificação da temporada após `round` rodadas.
 * `rows` já vem ordenada (posição = índice + 1).
 */
data class Standings(
    val season: Int,
    val round: Int,
    val rows: List<StandingRow>,
)
