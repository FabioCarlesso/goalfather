package com.carlesso.goalfather.domain.model

import kotlinx.serialization.Serializable

@Serializable
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
 * Tabela de UMA divisão em uma temporada (issue #47). Uma liga com N
 * divisões tem N `Standings` por temporada.
 *
 * `promotionSpots`/`relegationSpots` dizem quantas posições do topo/fundo
 * trocam de divisão na virada — a UI pinta as zonas a partir deles, sem
 * duplicar a regra de negócio no frontend (regra de ouro do CLAUDE.md).
 * São 0 quando não há para onde subir (elite) ou descer (última divisão).
 */
@Serializable
data class Standings(
    val season: Int,
    val round: Int,
    val rows: List<StandingRow>,
    val division: Division = Division.FIRST,
    val promotionSpots: Int = 0,
    val relegationSpots: Int = 0,
)
