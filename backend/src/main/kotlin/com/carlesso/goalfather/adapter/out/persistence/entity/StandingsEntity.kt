package com.carlesso.goalfather.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable

/**
 * Standings é um snapshot de UMA liga em uma temporada (issues #47 e #131).
 * 1 linha por (competição, temporada): a Série A de 2026, a Premier League de
 * 2026/27 (`season = 2026`)… `rows_json` carrega a tabela inteira serializada
 * (StandingRow[]) e `results_json` os placares já disputados (insumo do
 * confronto direto).
 *
 * Trade-off: simplicidade do snapshot vs. normalização. Nenhuma consulta
 * filtra por dentro da tabela, e a UI a lê inteira.
 *
 * Nome da competição, país, rótulo da temporada, tier e
 * `promotion_spots`/`relegation_spots` são FOTO da temporada, gravados junto
 * do snapshot: a tabela histórica continua legível (e com as zonas da época)
 * mesmo que o catálogo ou as regras mudem.
 */
@Entity
@Table(name = "standings")
@IdClass(StandingsId::class)
class StandingsEntity(
    @Id
    @Column(name = "competition_id", length = 64)
    var competitionId: String = "",

    @Id
    @Column(name = "season")
    var season: Int = 0,

    @Column(name = "competition_name", nullable = false)
    var competitionName: String = "",

    @Column(name = "country_code", nullable = false, length = 3)
    var countryCode: String = "BRA",

    @Column(name = "season_label", nullable = false, length = 16)
    var seasonLabel: String = "",

    @Column(name = "division", nullable = false)
    var division: Int = 1,

    @Column(name = "round_number", nullable = false)
    var round: Int = 0,

    @Column(name = "promotion_spots", nullable = false)
    var promotionSpots: Int = 0,

    @Column(name = "relegation_spots", nullable = false)
    var relegationSpots: Int = 0,

    @Column(name = "rows_json", columnDefinition = "TEXT", nullable = false)
    var rowsJson: String = "[]",

    @Column(name = "results_json", columnDefinition = "TEXT", nullable = false)
    var resultsJson: String = "[]",
)

/**
 * Chave composta (competition_id, season) via `@IdClass` — requisito JPA:
 * default constructor + equals/hashCode, que a `data class` dá de graça.
 */
data class StandingsId(
    var competitionId: String = "",
    var season: Int = 0,
) : Serializable
