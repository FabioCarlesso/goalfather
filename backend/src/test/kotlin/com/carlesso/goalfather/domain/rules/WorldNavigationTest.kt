package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Countries
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.FifaWindow
import com.carlesso.goalfather.domain.model.GameWeek
import com.carlesso.goalfather.domain.model.Matchday
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.Slot
import com.carlesso.goalfather.test.COPA_DO_BRASIL
import com.carlesso.goalfather.test.SERIE_A
import com.carlesso.goalfather.test.brazilCatalog
import com.carlesso.goalfather.test.leagueMatch
import com.carlesso.goalfather.test.makeClub
import com.carlesso.goalfather.test.testCup
import java.time.LocalDate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Leituras da navegação pelo mundo (issue #133). Domínio puro, sem Spring. */
class WorldNavigationTest {

    private val clubs: List<Club> = (1L..12L).map { id ->
        makeClub(id = id, name = "Clube $id", overall = 80 - id.toInt(), division = Division(if (id <= 6) 1 else 2))
            .copy(country = Countries.BRAZIL)
    }
    private val catalog = brazilCatalog()
    private val cup = testCup(2026, clubs, Random(7))
    private val plan = worldPlan(catalog, clubs, mapOf(Countries.BRAZIL.code to 2026), mapOf(COPA_DO_BRASIL to cup))

    private fun cupMatchdays(rounds: List<Round>) =
        rounds.filter { round -> plan.schedule.at(round.matchday).any { it.competition == COPA_DO_BRASIL } }

    @Test
    fun `da copa so se conhece o primeiro encaixe adiante, como no nextMatchOf`() {
        val start = plan.first!!
        val rounds = plan.visibleRoundsAfter(start, clubs)

        assertEquals(plan.schedule.matchdays.drop(1), rounds.map { it.matchday })
        val cupDays = cupMatchdays(rounds)
        assertTrue(cupDays.size > 1)
        assertTrue(cupDays.first().matches.any { it.competition == COPA_DO_BRASIL })
        assertTrue(cupDays.drop(1).all { day -> day.matches.none { it.competition == COPA_DO_BRASIL } })
        // A liga é conhecida inteira: todas as rodadas da Série A estão lá.
        val expected = plan.schedule.rounds.count { it.competition == SERIE_A && it.matchday > start }
        val seen = rounds.flatMap { it.matches }.filter { it.competition == SERIE_A }.map { it.stage }.distinct()
        assertEquals(expected, seen.size)
    }

    @Test
    fun `copa com fase em jogo no encaixe atual nao aparece em nenhum adiante`() {
        val rounds = plan.visibleRoundsAfter(plan.first!!, clubs, knockoutsInPlay = setOf(COPA_DO_BRASIL))
        assertTrue(rounds.flatMap { it.matches }.none { it.competition == COPA_DO_BRASIL })
    }

    @Test
    fun `semanas do clube com os jogos dele e a data FIFA`() {
        val club = ClubId(1)
        val week = GameWeek(2026, 12)
        val midweek = Round(Matchday(week, Slot.MIDWEEK), 2026, matches = listOf(leagueMatch(1, club, ClubId(2), "A", "B")))
        val weekend = Round(Matchday(week + 1, Slot.WEEKEND), 2026, matches = listOf(leagueMatch(2, ClubId(3), ClubId(4), "C", "D")))
        val fifa = listOf(FifaWindow(LocalDate.of(2026, 3, 23), LocalDate.of(2026, 3, 31)))

        val weeks = clubScheduleWeeks(club, week, 3, listOf(weekend, midweek), fifa)

        assertEquals(listOf(week, week + 1, week + 2), weeks.map { it.week })
        assertEquals(listOf(1L), weeks[0].matches.map { it.match.matchId })
        assertTrue(weeks[1].matches.isEmpty(), "jogo de outros clubes não entra")
        assertEquals(listOf(false, true, true), weeks.map { it.fifaWindow }) // W13 = 23–29/03, W14 toca 30–31/03
    }

    @Test
    fun `artilharia so de quem marcou, por gols, desempate pelo nome`() {
        val a = clubs[0].let { c -> c.copy(squad = c.squad.mapIndexed { i, p -> p.copy(id = PlayerId(100L + i), name = "Zé $i", goals = if (i < 2) 3 else 0) }) }
        val b = clubs[1].let { c -> c.copy(squad = c.squad.mapIndexed { i, p -> p.copy(id = PlayerId(200L + i), name = "Ana $i", goals = if (i == 0) 3 else if (i == 1) 5 else 0) }) }

        val scorers = listOf(a, b).topScorers(limit = 3)

        assertEquals(listOf("Ana 1", "Ana 0", "Zé 0"), scorers.map { it.playerName })
        assertEquals(listOf(5, 3, 3), scorers.map { it.goals })
        assertEquals(b.id, scorers.first().clubId)
    }
}
