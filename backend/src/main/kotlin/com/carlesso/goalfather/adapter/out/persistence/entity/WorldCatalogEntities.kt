package com.carlesso.goalfather.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * País do save (issue #131, V19). Entidade separada do `Country` de domínio
 * (regra de ouro do CLAUDE.md); o mapper traduz.
 */
@Entity
@Table(name = "countries")
class CountryEntity(
    @Id
    @Column(name = "code", length = 3)
    var code: String = "",

    @Column(name = "name", nullable = false)
    var name: String = "",

    /** Nome do enum `Confederation` como texto — o enum do domínio não desce até aqui. */
    @Column(name = "confederation", nullable = false, length = 16)
    var confederation: String = "",

    @Column(name = "iso_alpha2", nullable = false, length = 16)
    var isoAlpha2: String = "",
)

/**
 * Uma competição do catálogo do save (issue #131, V19): o `GameCompetition`
 * achatado em colunas, com o formato em JSON (`format_json`). Ver o
 * `CompetitionCatalogMapper` para a tradução nos dois sentidos.
 */
@Entity
@Table(name = "competition_catalog")
class CompetitionCatalogEntity(
    @Id
    @Column(name = "id", length = 64)
    var id: String = "",

    /** Ordem do pack: o catálogo é lido de volta nesta ordem. */
    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,

    @Column(name = "name", nullable = false)
    var name: String = "",

    @Column(name = "country", nullable = false, length = 3)
    var country: String = "",

    /** `LEAGUE` ou `NATIONAL_CUP` — o `sealed CompetitionKind` como texto. */
    @Column(name = "kind", nullable = false, length = 16)
    var kind: String = "",

    /** Divisão da liga; `null` na copa. */
    @Column(name = "tier")
    var tier: Int? = null,

    @Column(name = "season_span", nullable = false, length = 16)
    var seasonSpan: String = "",

    @Column(name = "pauses_on_fifa_windows", nullable = false)
    var pausesOnFifaWindows: Boolean = true,

    @Column(name = "format_json", nullable = false, columnDefinition = "TEXT")
    var formatJson: String = "",

    /** Critérios de desempate na ordem, separados por vírgula (`POINTS,WINS,...`). */
    @Column(name = "tiebreakers", nullable = false)
    var tiebreakers: String = "",

    @Column(name = "foreign_player_limit")
    var foreignPlayerLimit: Int? = null,

    @Column(name = "yellows_for_suspension", nullable = false)
    var yellowsForSuspension: Int = 3,

    @Column(name = "matches_per_red_card", nullable = false)
    var matchesPerRedCard: Int = 1,

    @Column(name = "resets_yellows_at_knockout", nullable = false)
    var resetsYellowsAtKnockout: Boolean = false,
)
