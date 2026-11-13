package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Competition
import com.carlesso.goalfather.domain.model.CompetitionFormat
import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.CompetitionSeason
import com.carlesso.goalfather.domain.model.KnockoutStage
import com.carlesso.goalfather.domain.model.Matchday
import com.carlesso.goalfather.domain.model.SeasonSpan
import com.carlesso.goalfather.domain.model.Slot
import java.time.LocalDate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

class MatchdaySchedulerTest {

    private fun clubs(range: LongRange): List<ClubId> = range.map(::ClubId)

    private fun league(id: String, legs: Int = 2) =
        Competition(CompetitionId(id), id, CompetitionFormat.RoundRobin(legs))

    private fun cup(id: String) = Competition(
        CompetitionId(id),
        id,
        CompetitionFormat.Knockout(listOf(KnockoutStage("SF", 4, newEntrants = 4), KnockoutStage("F", 2))),
    )

    /** Rodadas de pontos corridos como o agendador as vê: os clubes de cada rodada. */
    private fun leagueRounds(clubs: List<ClubId>, legs: Int = 2): List<Set<ClubId>> =
        roundRobinSchedule(clubs, legs).map { round -> round.flatMap { listOf(it.home, it.away) }.toSet() }

    private fun scheduled(result: ScheduleResult): Schedule = when (result) {
        is ScheduleResult.Scheduled -> result.schedule
        is ScheduleResult.NoRoomLeft -> fail("Calendário não fechou: $result")
    }

    private fun assertNoClubTwiceInASlot(schedule: Schedule) {
        for ((matchday, rounds) in schedule.rounds.groupBy { it.matchday }) {
            val all = rounds.flatMap { it.clubs }
            assertEquals(all.size, all.toSet().size, "Clube repetido em $matchday: ${rounds.map { it.competition }}")
        }
    }

    // ─── Propriedade ──────────────────────────────────────────────────────

    @Test
    fun `propriedade - dezenas de competicoes geradas nunca poem um clube duas vezes no mesmo encaixe`() {
        // Seeds fixas: o teste é reprodutível, mas cobre 20 mundos diferentes.
        for (seed in 1..20) {
            val rng = Random(seed)
            val fifa = interimFifaWindows(2026..2027)
            fun span() = if (rng.nextBoolean()) SeasonSpan.CALENDAR_YEAR else SeasonSpan.AUGUST_TO_MAY

            // Mundo plausível: cada clube numa liga do seu país (ligas de 4 a 20
            // clubes, turno único ou turno e returno) e as copas sorteando
            // participantes de todas elas — o mesmo clube em liga + várias copas.
            var nextClub = 1L
            val leagues = (1..rng.nextInt(18, 26)).map { i ->
                val size = rng.nextInt(2, 11) * 2
                val members = clubs(nextClub until nextClub + size).also { nextClub += size }
                val legs = rng.nextInt(1, 3)
                ScheduleRequest(CompetitionSeason.of(league("league-$i", legs), span(), 2026, rng.nextBoolean()), leagueRounds(members, legs))
            }
            val everyone = clubs(1L until nextClub)
            val cups = (1..rng.nextInt(8, 14)).map { i ->
                // Copa: cada fase reserva TODOS os clubes que ainda podem jogar.
                val entrants = everyone.shuffled(rng).take(rng.nextInt(4, 33)).toSet()
                ScheduleRequest(CompetitionSeason.of(cup("cup-$i"), span(), 2026, rng.nextBoolean()), List(rng.nextInt(2, 7)) { entrants })
            }
            val requests = (leagues + cups).shuffled(rng)

            val schedule = scheduled(scheduleSeasons(requests, fifa))

            assertTrue(requests.size >= 26, "dezenas de competições: ${requests.size}")
            assertNoClubTwiceInASlot(schedule)
            for (request in requests) {
                val mine = schedule.rounds.filter { it.competition == request.season.competition.id }
                assertEquals(request.rounds.size, mine.size)
                // Ordem preservada, dentro da janela, longe das datas FIFA quando pausa.
                assertEquals(mine.sortedBy { it.round }.map { it.matchday }, mine.map { it.matchday }.sorted())
                assertTrue(mine.all { it.matchday in request.season })
                if (request.season.pausesOnFifaWindows) {
                    assertTrue(mine.none { fifa.reserves(it.matchday.week) }, "seed $seed: ${request.season.competition.id}")
                }
            }
        }
    }

    // ─── Datas FIFA ───────────────────────────────────────────────────────

    @Test
    fun `liga que pausa nas datas FIFA nao joga nas semanas reservadas e a que nao pausa joga`() {
        val fifa = interimFifaWindows(2026..2027)
        val pausing = ScheduleRequest(
            CompetitionSeason.of(league("eng"), SeasonSpan.AUGUST_TO_MAY, 2026, pausesOnFifaWindows = true),
            leagueRounds(clubs(1L..20L)),
        )
        val nonPausing = ScheduleRequest(
            CompetitionSeason.of(league("bra"), SeasonSpan.CALENDAR_YEAR, 2026, pausesOnFifaWindows = false),
            leagueRounds(clubs(21L..40L)),
        )

        val schedule = scheduled(scheduleSeasons(listOf(pausing, nonPausing), fifa))

        val eng = schedule.rounds.filter { it.competition.value == "eng" }
        val bra = schedule.rounds.filter { it.competition.value == "bra" }
        assertEquals(38, eng.size)
        assertEquals(38, bra.size)
        assertTrue(eng.none { fifa.reserves(it.matchday.week) }, "Liga que pausa jogou em data FIFA")
        assertTrue(bra.any { fifa.reserves(it.matchday.week) }, "Liga que não pausa deveria jogar em data FIFA")
    }

    // ─── Preferência de encaixe ───────────────────────────────────────────

    @Test
    fun `liga prefere o fim de semana e a copa o meio de semana`() {
        val members = clubs(1L..4L)
        val schedule = scheduled(
            scheduleSeasons(
                listOf(
                    ScheduleRequest(CompetitionSeason.of(cup("copa"), SeasonSpan.CALENDAR_YEAR, 2026), List(2) { members.toSet() }),
                    ScheduleRequest(CompetitionSeason.of(league("liga", 1), SeasonSpan.CALENDAR_YEAR, 2026), leagueRounds(members, 1)),
                ),
                interimFifaWindows(2026),
            ),
        )

        assertTrue(schedule.rounds.filter { it.competition.value == "liga" }.all { it.matchday.slot == Slot.WEEKEND })
        assertTrue(schedule.rounds.filter { it.competition.value == "copa" }.all { it.matchday.slot == Slot.MIDWEEK })
        assertNoClubTwiceInASlot(schedule)
    }

    @Test
    fun `conflito empurra a rodada para o outro encaixe da semana ou adiante`() {
        val members = clubs(1L..2L).toSet()
        // Duas "ligas" com os MESMOS clubes, 1 rodada cada, mirando a mesma semana.
        // Janela começando numa segunda: a 1ª semana tem os dois encaixes.
        fun season(id: String) = CompetitionSeason(league(id, 1), 2026, LocalDate.of(2026, 1, 5), LocalDate.of(2026, 12, 31))
        val a = ScheduleRequest(season("a"), listOf(members))
        val b = ScheduleRequest(season("b"), listOf(members))

        val schedule = scheduled(scheduleSeasons(listOf(a, b), emptyList()))

        val (first, second) = schedule.rounds.map { it.matchday }
        assertTrue(first != second)
        assertEquals(first.week, second.week, "o outro encaixe da mesma semana estava livre")
    }

    @Test
    fun `temporada sem espaco vira valor e nao exception`() {
        val members = clubs(1L..2L).toSet()
        val tiny = CompetitionSeason(
            league("curta"),
            2026,
            start = Matchday.of(LocalDate.of(2026, 3, 2), Slot.MIDWEEK).date,
            end = Matchday.of(LocalDate.of(2026, 3, 2), Slot.WEEKEND).date,
        )
        val result = scheduleSeasons(listOf(ScheduleRequest(tiny, List(3) { members })), emptyList())
        assertIs<ScheduleResult.NoRoomLeft>(result)
        assertEquals(3, result.round)
    }

    // ─── Temporadas por país ──────────────────────────────────────────────

    @Test
    fun `temporadas europeia e brasileira coexistem e cada uma vira sozinha`() {
        val fifa = interimFifaWindows(2026..2028)
        val braClubs = clubs(1L..20L)
        val engClubs = clubs(101L..120L)
        val bra = CompetitionSeason.of(league("bra"), SeasonSpan.CALENDAR_YEAR, 2026, pausesOnFifaWindows = false)
        val eng = CompetitionSeason.of(league("eng"), SeasonSpan.AUGUST_TO_MAY, 2026)

        var schedule = scheduled(
            scheduleSeasons(
                listOf(ScheduleRequest(bra, leagueRounds(braClubs)), ScheduleRequest(eng, leagueRounds(engClubs))),
                fifa,
            ),
        )
        val seasons = mutableMapOf(bra.competition.id to bra, eng.competition.id to eng)
        // Quando cada competição virou, e em que ano estava a OUTRA nessa hora.
        val turnovers = mutableListOf<Pair<String, Matchday>>()

        // Relógio do jogo: encaixe a encaixe, de janeiro de 2026 a junho de 2027.
        var now: Matchday? = schedule.first
        while (now != null && now.date.year * 100 + now.date.monthValue <= 202706) {
            val current = now
            for (ending in schedule.endingAt(current)) {
                val season = seasons.getValue(ending)
                turnovers += "${ending.value} ${season.label}" to current
                val nextClubs = if (ending.value == "bra") braClubs else engClubs
                schedule = scheduled(schedule.rollOver(season, leagueRounds(nextClubs), fifa, current))
                seasons[ending] = season.next()
            }
            now = schedule.nextAfter(current)
        }

        // Brasileiro 2026 vira em dezembro; a Premier 2026/27 segue sem ser tocada
        // e vira sozinha em maio de 2027, com o Brasileiro 2027 já em andamento.
        val (braTurn, braAt) = turnovers.first { it.first.startsWith("bra") }
        val (engTurn, engAt) = turnovers.first { it.first.startsWith("eng") }
        assertEquals("bra 2026", braTurn)
        assertEquals(2026, braAt.date.year)
        assertEquals("eng 2026/27", engTurn)
        assertEquals(2027, engAt.date.year)
        assertTrue(engAt.date.monthValue <= 5)
        // No dia em que a Premier vira, o Brasileiro 2027 já jogou e ainda tem jogo.
        val bra2027 = schedule.rounds.filter { it.competition.value == "bra" && it.seasonYear == 2027 }
        assertTrue(bra2027.any { it.matchday <= engAt } && bra2027.any { it.matchday > engAt })
        // E a Premier 2027/28 só começa em agosto.
        val eng2027 = schedule.rounds.filter { it.competition.value == "eng" && it.seasonYear == 2027 }
        assertTrue(eng2027.isNotEmpty() && eng2027.all { it.matchday.date >= LocalDate.of(2027, 8, 1) })
        assertNoClubTwiceInASlot(schedule)
    }
}
