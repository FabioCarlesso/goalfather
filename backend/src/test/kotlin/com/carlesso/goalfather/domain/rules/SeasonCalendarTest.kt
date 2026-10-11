package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.KnockoutPhase
import com.carlesso.goalfather.domain.model.KnockoutPhase.FINAL
import com.carlesso.goalfather.domain.model.KnockoutPhase.QUARTER_FINALS
import com.carlesso.goalfather.domain.model.KnockoutPhase.ROUND_OF_16
import com.carlesso.goalfather.domain.model.KnockoutPhase.SEMI_FINALS
import com.carlesso.goalfather.domain.model.RoundStage
import com.carlesso.goalfather.domain.model.RoundStage.Cup
import com.carlesso.goalfather.domain.model.RoundStage.League
import com.carlesso.goalfather.test.makeClub
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SeasonCalendarTest {

    private val fourPhases = listOf(ROUND_OF_16, QUARTER_FINALS, SEMI_FINALS, FINAL)

    @Test
    fun `sem copa o calendario e so a liga, data igual a rodada`() {
        assertEquals((1..5).map { League(it) }, seasonCalendar(5))
    }

    @Test
    fun `5 rodadas e 4 fases intercalam uma fase depois de cada rodada`() {
        assertEquals(
            listOf(
                League(1), Cup(ROUND_OF_16),
                League(2), Cup(QUARTER_FINALS),
                League(3), Cup(SEMI_FINALS),
                League(4), Cup(FINAL),
                League(5),
            ),
            seasonCalendar(5, fourPhases),
        )
    }

    @Test
    fun `15 rodadas e 4 fases dao uma fase a cada 3 rodadas`() {
        val calendar = seasonCalendar(15, fourPhases)
        val leagueBeforeEachCup = calendar.withIndex()
            .filter { it.value is Cup }
            .map { (i, _) -> calendar.take(i).count { it is League } }
        assertEquals(listOf(3, 6, 9, 12), leagueBeforeEachCup)
    }

    @Test
    fun `data 1 e sempre liga, a copa termina antes da ultima rodada e nada se perde`() {
        for (matchdays in 2..30) {
            for (phases in 1..KnockoutPhase.entries.size) {
                val cupPhases = KnockoutPhase.entries.takeLast(phases)
                val calendar = seasonCalendar(matchdays, cupPhases)

                assertEquals(League(1), calendar.first())
                assertIs<League>(calendar.last(), "L=$matchdays C=$phases: última data deve ser de liga")
                assertEquals((1..matchdays).map { League(it) }, calendar.filterIsInstance<League>())
                assertEquals(cupPhases.map { Cup(it) }, calendar.filterIsInstance<Cup>())
            }
        }
    }

    @Test
    fun `liga de uma rodada joga a copa inteira depois dela`() {
        assertEquals(listOf(League(1), Cup(SEMI_FINALS), Cup(FINAL)), seasonCalendar(1, listOf(SEMI_FINALS, FINAL)))
    }

    @Test
    fun `calendario dos clubes do seed tem 9 datas e a copa sorteada`() {
        val clubs = (1L..12L).map { makeClub(id = it, division = Division(if (it <= 6) 1 else 2)) }
        val cup = drawCup(2026, clubs, Random(cupDrawSeed(2026)))
        val calendar = seasonCalendar(clubs, cup)
        assertEquals(9, calendar.size)
        assertEquals(4, calendar.count { it is RoundStage.Cup })
    }

    @Test
    fun `scheduleRound gera rodada de liga pela matchday e de copa pela chave`() {
        val clubs = (1L..12L).map { makeClub(id = it, division = Division(if (it <= 6) 1 else 2)) }
        val cup = drawCup(2026, clubs, Random(cupDrawSeed(2026)))

        val league = scheduleRound(3, 2026, League(2), clubs, cup)
        // Data 3, rodada de liga 2: pareamento da rodada 2, matchId pela data.
        assertEquals(League(2), league.stage)
        assertEquals(generateRound(2, 2026, clubs).matches.map { it.homeClubId }, league.matches.map { it.homeClubId })
        assertTrue(league.matches.all { it.matchId / 1000 == 3L })

        val cupDate = scheduleRound(2, 2026, Cup(ROUND_OF_16), clubs, cup)
        assertEquals(Cup(ROUND_OF_16), cupDate.stage)
        assertEquals(4, cupDate.matches.size)
    }
}
