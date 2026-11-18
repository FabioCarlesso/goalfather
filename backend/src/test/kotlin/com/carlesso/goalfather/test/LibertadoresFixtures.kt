package com.carlesso.goalfather.test

import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.Competition
import com.carlesso.goalfather.domain.model.CompetitionFormat
import com.carlesso.goalfather.domain.model.CompetitionKind
import com.carlesso.goalfather.domain.model.Countries
import com.carlesso.goalfather.domain.model.Country
import com.carlesso.goalfather.domain.model.GameCompetition
import com.carlesso.goalfather.domain.model.KnockoutStage
import com.carlesso.goalfather.domain.model.Legs
import com.carlesso.goalfather.domain.model.SeasonSpan
import com.carlesso.goalfather.domain.model.Tiebreaker
import com.carlesso.goalfather.domain.model.WorldQualificationRules

/** A Libertadores do pack (issue #132): 8 grupos de 4, ida e volta, oitavas a final única. */
internal val LIBERTADORES_FORMAT = CompetitionFormat.GroupsThenKnockout(
    groups = 8,
    perGroup = 4,
    qualifiersPerGroup = 2,
    groupLegs = 2,
    knockout = CompetitionFormat.Knockout(
        listOf(
            KnockoutStage("Oitavas de final", 16, Legs.TWO_LEGGED, 16),
            KnockoutStage("Quartas de final", 8, Legs.TWO_LEGGED),
            KnockoutStage("Semifinal", 4, Legs.TWO_LEGGED),
            KnockoutStage("Final", 2, Legs.SINGLE),
        ),
    ),
)

/** A Libertadores no catálogo do save: hospedada pelo calendário do Brasil. */
internal fun libertadoresGame(): GameCompetition = GameCompetition(
    competition = Competition(
        WorldQualificationRules.CONMEBOL_LIBERTADORES,
        "Copa Libertadores",
        LIBERTADORES_FORMAT,
        listOf(Tiebreaker.POINTS, Tiebreaker.HEAD_TO_HEAD, Tiebreaker.GOAL_DIFFERENCE, Tiebreaker.GOALS_FOR, Tiebreaker.DRAWING_OF_LOTS),
    ),
    country = Countries.BRAZIL,
    kind = CompetitionKind.Continental,
    span = SeasonSpan.CALENDAR_YEAR,
    pausesOnFifaWindows = true,
)

/** Cota de cada país estrangeiro na Libertadores 2026 do pack (26 clubes; o Brasil tem 6). */
internal val FOREIGN_QUOTAS: List<Pair<Country, Int>> = listOf(
    Countries.ARGENTINA to 6, Countries.BOLIVIA to 2, Countries.CHILE to 2, Countries.COLOMBIA to 4,
    Countries.ECUADOR to 3, Countries.PARAGUAY to 2, Countries.PERU to 3, Countries.URUGUAY to 2,
    Countries.VENEZUELA to 2,
)

/** Os 26 clubes estrangeiros (sem liga no save), ids a partir de [firstId], força decrescente. */
internal fun foreignContinentalClubs(firstId: Long = 1_001): List<Club> {
    var next = firstId
    return FOREIGN_QUOTAS.flatMap { (country, quota) ->
        (1..quota).map { i ->
            val id = next++
            makeClub(id = id, name = "${country.code.value}-$i", overall = 80 - i).copy(country = country)
        }
    }
}
