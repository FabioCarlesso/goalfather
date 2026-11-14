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
    /**
     * Cartões do clube NESTA competição (issue #131) — insumo dos critérios
     * `FEWER_RED_CARDS`/`FEWER_YELLOW_CARDS` do Brasileirão. Moram na linha
     * (e no snapshot `rows_json`) porque são a "disciplina" da tabela; o
     * contrato não os expõe.
     */
    val yellowCards: Int = 0,
    val redCards: Int = 0,
)

/**
 * Tabela de UMA liga do mundo em uma temporada (issue #131): Série A, Série B,
 * Premier League… — cada uma com a sua, na SUA temporada ([season] é o ano de
 * início; [seasonLabel] é `2026` ou `2026/27`).
 *
 * [competitionName] e [country] são FOTO, como o nome do clube na linha: a
 * tabela histórica continua legível mesmo que o catálogo mude. [division] é o
 * tier da liga no país (a Série B é 2; a Premier League é 1 — a divisão só faz
 * sentido junto do país).
 *
 * [results] guarda os placares já disputados na liga: é o insumo do confronto
 * direto (`HEAD_TO_HEAD`), que não sai das linhas — depende de QUEM está
 * empatado. Persistido junto do snapshot.
 *
 * `promotionSpots`/`relegationSpots` dizem quantas posições do topo/fundo
 * trocam de divisão na virada. Desde a issue #128 a UI pinta pela
 * [StandingRow.zone] de cada linha (que também cobre vaga continental e
 * playoff); os contadores ficam por compatibilidade do contrato e saem das
 * mesmas regras.
 *
 * Não é `@Serializable`: a persistência grava só as linhas e os resultados
 * (`rows_json`/`results_json`) e a API tem o próprio DTO.
 */
data class Standings(
    val competitionId: CompetitionId,
    val competitionName: String,
    val country: Country,
    val season: Int,
    val seasonLabel: String,
    val round: Int,
    val rows: List<StandingRow>,
    val division: Division = Division.FIRST,
    val promotionSpots: Int = 0,
    val relegationSpots: Int = 0,
    val results: List<PlayedMatch> = emptyList(),
)
