package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.`in`.CompetitionLookup
import com.carlesso.goalfather.application.port.`in`.CompetitionMatchday
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.CompetitionCatalogRepository
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.Countries
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.Matchday
import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.domain.rules.worldPlan
import com.carlesso.goalfather.test.COPA_DO_BRASIL
import com.carlesso.goalfather.test.InMemoryCupRepository
import com.carlesso.goalfather.test.SERIE_A
import com.carlesso.goalfather.test.SERIE_B
import com.carlesso.goalfather.test.brazilCatalog
import com.carlesso.goalfather.test.makeClub
import com.carlesso.goalfather.test.testCup
import com.carlesso.goalfather.test.testStandings
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Navegação pelo mundo (issue #133) sobre ports mockados. Sem Spring. */
class CompetitionQueryServiceTest {

    private val clubs: List<Club> = (1L..12L).map { id ->
        makeClub(id = id, name = "Clube $id", overall = 80 - id.toInt(), division = Division(if (id <= 6) 1 else 2))
            .copy(country = Countries.BRAZIL)
    }
    private val catalog = brazilCatalog()
    private val cup = testCup(2026, clubs, Random(7))
    private val plan = worldPlan(catalog, clubs, mapOf(Countries.BRAZIL.code to 2026), mapOf(COPA_DO_BRASIL to cup))

    /** O 3º encaixe é o corrente; os dois anteriores foram jogados (placar 2 × 1 em tudo). */
    private val current: Matchday = plan.schedule.matchdays[2]
    private val played = plan.schedule.matchdays.take(2).associateWith { md ->
        plan.roundAt(md, clubs).let { r -> r.copy(status = RoundStatus.Finished, matches = r.matches.map { it.copy(status = RoundStatus.Finished, homeGoals = 2, awayGoals = 1) }) }
    }

    private val serieA = testStandings(rows = emptyList(), division = Division(1))
    private val leagueRepo = mockk<LeagueRepository> {
        coEvery { currentStandings() } returns listOf(serieA, testStandings(rows = emptyList(), division = Division(2)))
        coEvery { findLatest() } returns plan.roundAt(current, clubs)
        coEvery { findRound(any()) } answers { played[firstArg()] }
    }
    private val service = CompetitionQueryService(
        mockk<CompetitionCatalogRepository> { coEvery { findAll() } returns catalog.reversed() },
        mockk<ClubRepository> {
            coEvery { findAll() } returns clubs
            // Stubs por valor: o MockK entrega a `value class` ClubId desembrulhada
            // (Long) a um `answers { firstArg<ClubId>() }`.
            coEvery { findById(ClubId(1)) } returns clubs.first()
            coEvery { findById(ClubId(999)) } returns null
        },
        leagueRepo,
        InMemoryCupRepository(cup),
    )

    @Test
    fun `lista na ordem do mundo, com temporada e clubes de cada competicao`() = runTest {
        val list = service.competitions()

        assertEquals(listOf(SERIE_A, SERIE_B, COPA_DO_BRASIL), list.map { it.game.id })
        assertEquals(listOf(6, 6, 12), list.map { it.clubCount })
        assertTrue(list.all { it.season == 2026 && it.seasonLabel == "2026" })
    }

    @Test
    fun `tabela e chave distinguem competicao desconhecida de recurso ausente`() = runTest {
        assertEquals(CompetitionLookup.UnknownCompetition, service.standings(CompetitionId("xyz")))
        assertEquals(CompetitionLookup.NotAvailable, service.standings(COPA_DO_BRASIL))
        assertEquals(serieA, assertIs<CompetitionLookup.Found<*>>(service.standings(SERIE_A)).value)
        assertEquals(CompetitionLookup.NotAvailable, service.bracket(SERIE_A))
        assertEquals(cup, assertIs<CompetitionLookup.Found<*>>(service.bracket(COPA_DO_BRASIL)).value)
    }

    @Test
    fun `jogos da liga - passados com placar gravado, o corrente e os futuros do plano`() = runTest {
        val days = assertIs<CompetitionLookup.Found<List<CompetitionMatchday>>>(service.matches(SERIE_A)).value

        val expected = plan.schedule.rounds.filter { it.competition == SERIE_A }.map { it.matchday }.sorted()
        assertEquals(expected, days.map { it.matchday })
        assertTrue(days.all { day -> day.matches.isNotEmpty() && day.matches.all { it.competition == SERIE_A } })
        days.filter { it.matchday < current }.flatMap { it.matches }.forEach {
            assertEquals(RoundStatus.Finished, it.status)
            assertEquals(2, it.homeGoals)
        }
        assertTrue(days.filter { it.matchday > current }.flatMap { it.matches }.all { it.status == RoundStatus.Scheduled })
    }

    @Test
    fun `jogos da copa - so o primeiro encaixe adiante tem confrontos`() = runTest {
        val days = assertIs<CompetitionLookup.Found<List<CompetitionMatchday>>>(service.matches(COPA_DO_BRASIL)).value
        val ahead = days.filter { it.matchday > current }
        assertTrue(ahead.size > 1)
        assertTrue(ahead.first().matches.isNotEmpty())
        assertTrue(ahead.drop(1).all { it.matches.isEmpty() })
    }

    @Test
    fun `calendario do clube a partir da semana corrente`() = runTest {
        assertNull(service.clubSchedule(ClubId(999), 4))

        val schedule = assertNotNull(service.clubSchedule(ClubId(1), 6))

        assertEquals(current, schedule.current)
        assertEquals(6, schedule.weeks.size)
        assertEquals(current.week, schedule.weeks.first().week)
        val matches = schedule.weeks.flatMap { it.matches }
        assertTrue(matches.isNotEmpty())
        assertTrue(matches.all { it.match.involves(ClubId(1)) })
        assertTrue(matches.all { it.matchday.week in schedule.weeks.first().week..schedule.weeks.last().week })
    }

    @Test
    fun `artilharia so de quem marcou nos clubes da liga`() = runTest {
        val scorers = assertIs<CompetitionLookup.Found<*>>(service.scorers(SERIE_B)).value
        assertEquals(emptyList<Any>(), scorers)
    }
}
