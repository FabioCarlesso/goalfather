package com.carlesso.goalfather.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MatchdayTest {

    @Test
    fun `semana ISO ancora na segunda e conhece anos de 53 semanas`() {
        assertEquals(LocalDate.of(2025, 12, 29), GameWeek(2026, 1).monday)
        assertEquals(53, GameWeek.weeksInYear(2026))
        assertEquals(52, GameWeek.weeksInYear(2027))
        // 01/01/2027 ainda é a semana 53 de 2026 (ano baseado em semana).
        assertEquals(GameWeek(2026, 53), GameWeek.of(LocalDate.of(2027, 1, 1)))
        assertEquals(GameWeek(2027, 1), GameWeek(2026, 53).next())
    }

    @Test
    fun `encaixes caem na quarta e no domingo e seguem a ordem do tempo`() {
        val week = GameWeek(2026, 5)
        val mid = Matchday(week, Slot.MIDWEEK)
        val end = Matchday(week, Slot.WEEKEND)

        assertEquals(DayOfWeek.WEDNESDAY, mid.date.dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, end.date.dayOfWeek)
        assertTrue(mid < end)
        assertEquals(end, mid.next())
        assertEquals(Matchday(GameWeek(2026, 6), Slot.MIDWEEK), end.next())
        assertEquals(mid.serial + 1, end.serial)
        assertEquals(end.serial + 1, end.next().serial)
    }

    @Test
    fun `key faz ida e volta e parse recusa lixo`() {
        val matchday = Matchday(GameWeek(2026, 5), Slot.WEEKEND)
        assertEquals("2026-W05-WEEKEND", matchday.key)
        assertEquals(matchday, Matchday.parse(matchday.key))
        assertNull(Matchday.parse("2027-W53-WEEKEND"), "2027 tem 52 semanas")
        assertNull(Matchday.parse("rodada-3"))
        assertNull(Matchday.parse("2026-W05-SATURDAY"))
    }

    @Test
    fun `between devolve os encaixes cuja data cai na janela`() {
        val all = Matchday.between(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))
        assertTrue(all.all { it.date.year == 2026 })
        assertEquals(all.sorted(), all)
        // 2026 tem 52 quartas e 52 domingos dentro do ano civil.
        assertEquals(104, all.size)
    }

    @Test
    fun `temporada europeia atravessa o ano e a brasileira nao`() {
        val league = Competition(CompetitionId("x"), "X", CompetitionFormat.RoundRobin())
        val bra = CompetitionSeason.of(league, SeasonSpan.of(Confederation.CONMEBOL), 2026)
        val eng = CompetitionSeason.of(league, SeasonSpan.of(Confederation.UEFA), 2026)

        assertEquals("2026", bra.label)
        assertEquals("2026/27", eng.label)
        assertEquals(LocalDate.of(2027, 5, 31), eng.end)
        assertEquals(2027, eng.next().year)
        assertEquals(Slot.WEEKEND, eng.preferredSlot)
    }
}
