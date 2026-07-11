package com.carlesso.goalfather.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable

/**
 * Standings é um snapshot de UMA divisão em uma temporada (issue #47).
 * 1 linha por (temporada, divisão), `rows_json` carrega a tabela inteira
 * serializada (StandingRow[]).
 *
 * Trade-off: simplicidade do snapshot vs. normalização. Para poucas
 * divisões com ~10 clubes, snapshot é trivial e instantâneo. Quando virar
 * liga com 20 times + histórico longo, vale normalizar.
 *
 * `promotion_spots`/`relegation_spots` são persistidos junto do snapshot:
 * derivam do nº de divisões NO MOMENTO da temporada — recomputar na leitura
 * daria zona errada para temporadas históricas se a liga mudar de formato.
 */
@Entity
@Table(name = "standings")
@IdClass(StandingsId::class)
class StandingsEntity(
    @Id
    @Column(name = "season")
    var season: Int = 0,

    @Id
    @Column(name = "division")
    var division: Int = 1,

    @Column(name = "round_number", nullable = false)
    var round: Int = 0,

    @Column(name = "promotion_spots", nullable = false)
    var promotionSpots: Int = 0,

    @Column(name = "relegation_spots", nullable = false)
    var relegationSpots: Int = 0,

    @Column(name = "rows_json", columnDefinition = "TEXT", nullable = false)
    var rowsJson: String = "[]",
)

/**
 * Chave composta (season, division) via `@IdClass` — requisito JPA:
 * default constructor + equals/hashCode, que a `data class` dá de graça.
 */
data class StandingsId(
    var season: Int = 0,
    var division: Int = 1,
) : Serializable
