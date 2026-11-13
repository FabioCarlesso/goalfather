package com.carlesso.goalfather.domain.model

import kotlinx.serialization.Serializable

/**
 * Linha da tabela. [zone] é a faixa da POSIÇÃO (issue #128) — vaga, acesso,
 * playoff, rebaixamento —, gravada junto da linha no snapshot: a temporada
 * histórica mostra a faixa da época, e a tabela não precisa de coluna nova.
 * Quem reordena a tabela (`applyRoundToStandings`) devolve a faixa à posição.
 */
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
    val zone: TableZone = TableZone.NONE,
)

/**
 * Tabela de UMA divisão em uma temporada (issue #47). Uma liga com N
 * divisões tem N `Standings` por temporada.
 *
 * `promotionSpots`/`relegationSpots` dizem quantas posições do topo/fundo
 * trocam de divisão na virada. São 0 quando não há para onde subir (elite) ou
 * descer (última divisão). Desde a issue #128 a UI pinta pela [StandingRow.zone]
 * de cada linha (que também cobre vaga continental e playoff); os contadores
 * ficam por compatibilidade do contrato e saem das mesmas regras.
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
