package com.carlesso.goalfather.adapter.out.persistence.mapper

import com.carlesso.goalfather.domain.model.Competition
import com.carlesso.goalfather.domain.model.CompetitionFormat
import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.CompetitionKind
import com.carlesso.goalfather.domain.model.Countries
import com.carlesso.goalfather.domain.model.DisciplineRules
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.GameCompetition
import com.carlesso.goalfather.domain.model.KnockoutStage
import com.carlesso.goalfather.domain.model.Legs
import com.carlesso.goalfather.domain.model.SeasonSpan
import com.carlesso.goalfather.domain.model.Tiebreaker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Catálogo do save (issue #131): `GameCompetition` → linha → `GameCompetition` sem perda. */
class CompetitionCatalogMapperTest {

    private val final = KnockoutStage("Final", 2, Legs.SINGLE, newEntrants = 0)

    private fun competition(id: String, format: CompetitionFormat) = Competition(
        id = CompetitionId(id),
        name = "Competição $id",
        format = format,
        tiebreakers = listOf(Tiebreaker.POINTS, Tiebreaker.WINS, Tiebreaker.HEAD_TO_HEAD),
        discipline = DisciplineRules(yellowsForSuspension = 2, matchesPerRedCard = 2, resetsYellowsAtKnockout = true),
        foreignPlayerLimit = 9,
    )

    @Test
    fun `liga e copa voltam iguais do banco`() {
        val serieA = GameCompetition(
            competition = competition("bra-serie-a", CompetitionFormat.RoundRobin(2)),
            country = Countries.BRAZIL,
            kind = CompetitionKind.League(Division(1)),
            span = SeasonSpan.CALENDAR_YEAR,
            pausesOnFifaWindows = false,
        )
        val copa = GameCompetition(
            competition = competition(
                "bra-copa-do-brasil",
                CompetitionFormat.Knockout(
                    listOf(KnockoutStage("Quarta fase", 4, Legs.SINGLE, newEntrants = 4), KnockoutStage("Semifinal", 4, Legs.TWO_LEGGED, newEntrants = 2), final),
                ),
            ).copy(foreignPlayerLimit = null),
            country = Countries.ENGLAND,
            kind = CompetitionKind.NationalCup,
            span = SeasonSpan.AUGUST_TO_MAY,
            pausesOnFifaWindows = true,
        )

        val serieARow = serieA.toEntity(order = 0)
        val copaRow = copa.toEntity(order = 1)

        assertEquals(serieA, serieARow.toDomain())
        assertEquals(copa, copaRow.toDomain())
        assertEquals("LEAGUE", serieARow.kind)
        assertEquals(1, serieARow.tier)
        assertEquals("NATIONAL_CUP", copaRow.kind)
        assertNull(copaRow.tier)
        assertEquals("POINTS,WINS,HEAD_TO_HEAD", serieARow.tiebreakers)
    }

    @Test
    fun `os formatos de grupos e suico tambem fazem a volta`() {
        val groups = CompetitionFormat.GroupsThenKnockout(
            groups = 1,
            perGroup = 4,
            qualifiersPerGroup = 2,
            knockout = CompetitionFormat.Knockout(listOf(final.copy(newEntrants = 2))),
        )
        val swiss = CompetitionFormat.SwissLeague(teams = 8, matchesPerTeam = 4, directQualifiers = 2, playoffRange = 3..6)
        val swissNoPlayoff = swiss.copy(playoffRange = IntRange.EMPTY)

        for (format in listOf(groups, swiss, swissNoPlayoff)) {
            val game = GameCompetition(competition("x", format), Countries.BRAZIL, CompetitionKind.NationalCup, SeasonSpan.CALENDAR_YEAR, true)
            assertEquals(game, game.toEntity(0).toDomain())
        }
    }
}
