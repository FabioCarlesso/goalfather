package com.carlesso.goalfather.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * Standings é um snapshot da temporada. 1 linha por temporada,
 * `rows_json` carrega a tabela inteira serializada (StandingRow[]).
 *
 * Trade-off: simplicidade do snapshot vs. normalização. Para 1 temporada
 * com ~10 clubes, snapshot é trivial e instantaneo. Quando virar liga
 * com 20 times + histórico, vale normalizar.
 */
@Entity
@Table(name = "standings")
class StandingsEntity(
    @Id
    @Column(name = "season")
    var season: Int = 0,

    @Column(name = "round_number", nullable = false)
    var round: Int = 0,

    @Column(name = "rows_json", columnDefinition = "TEXT", nullable = false)
    var rowsJson: String = "[]",
)
