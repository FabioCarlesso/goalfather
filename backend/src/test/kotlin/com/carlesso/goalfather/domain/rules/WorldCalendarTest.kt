package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.CompetitionKind
import com.carlesso.goalfather.domain.model.Countries
import com.carlesso.goalfather.domain.model.CountryCode
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.GameWeek
import com.carlesso.goalfather.domain.model.RoundMatch
import com.carlesso.goalfather.domain.model.RoundStage
import com.carlesso.goalfather.domain.model.TableZone
import com.carlesso.goalfather.test.MVP_LEAGUE_SIZES
import com.carlesso.goalfather.test.SERIE_A
import com.carlesso.goalfather.test.SERIE_B
import com.carlesso.goalfather.test.mvpCatalog
import com.carlesso.goalfather.test.mvpClubs
import java.time.LocalDate
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * O calendário do mundo do MVP em tamanho real (issue #131): Série A e B com
 * 20 clubes, Premier League, La Liga e Serie A com 20, Bundesliga e Ligue 1
 * com 18 — turno e returno, cada país na sua janela e com a sua regra de data
 * FIFA. Domínio puro, sem Spring.
 */
class WorldCalendarTest {

    private val catalog = mvpCatalog()
    private val clubs = mvpClubs()
    private val start2026 = catalog.associate { it.country.code to 2026 }
    private val plan = worldPlan(catalog, clubs, start2026)

    /** Todos os jogos do plano, encaixe a encaixe. */
    private val allMatches: List<Pair<LocalDate, List<RoundMatch>>> =
        plan.schedule.matchdays.map { it.date to plan.roundAt(it, clubs).matches }

    private val brazil = Countries.BRAZIL.code
    private val europe: Set<CountryCode> = setOf(
        Countries.ENGLAND.code, Countries.SPAIN.code, Countries.ITALY.code, Countries.GERMANY.code, Countries.FRANCE.code,
    )

    private fun countryOf(competition: CompetitionId) = catalog.single { it.id == competition }.country.code

    @Test
    fun `o mundo tem 136 clubes em 7 ligas`() {
        assertEquals(136, clubs.size)
        assertEquals(7, plan.competitions.size) // sem copa: o motor atual não comporta 40 inscritos
    }

    @Test
    fun `cada liga tem n(n-1) jogos e cada clube n-1 em casa e n-1 fora`() {
        val byLeague = allMatches.flatMap { it.second }.groupBy { it.competition }
        val leagues = catalog.filter { it.kind is CompetitionKind.League }
        assertEquals(leagues.map { it.id }.toSet(), byLeague.keys)
        for ((country, tier, n) in MVP_LEAGUE_SIZES) {
            val league = leagues.single {
                it.country == country && (it.kind as CompetitionKind.League).tier.value == tier
            }
            val matches = byLeague.getValue(league.id)
            assertEquals(n * (n - 1), matches.size, "jogos de ${league.id.value}") // 380 ou 306
            val home = matches.groupingBy { it.homeClubId }.eachCount()
            val away = matches.groupingBy { it.awayClubId }.eachCount()
            assertEquals(n, home.size)
            assertTrue(home.values.all { it == n - 1 } && away.values.all { it == n - 1 }, "mando de ${league.id.value}")
            // Cada confronto exatamente uma vez em cada mando.
            assertEquals(n * (n - 1), matches.map { it.homeClubId to it.awayClubId }.toSet().size)
            assertEquals((1..2 * (n - 1)).toList(), matches.map { (it.stage as RoundStage.League).matchday }.distinct().sorted())
        }
    }

    @Test
    fun `nenhum clube joga duas vezes no mesmo encaixe e os matchIds sao unicos`() {
        for ((date, matches) in allMatches) {
            val inAction = matches.flatMap { listOf(it.homeClubId, it.awayClubId) }
            assertEquals(inAction.size, inAction.toSet().size, "clube em dois jogos em $date")
        }
        val ids = allMatches.flatMap { it.second }.map { it.matchId }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `Brasil joga jan-dez de 2026 sem respeitar data FIFA`() {
        val brazilDates = allMatches.flatMap { (date, ms) -> ms.filter { countryOf(it.competition) == brazil }.map { date } }
        assertTrue(brazilDates.all { it in LocalDate.of(2026, 1, 1)..LocalDate.of(2026, 12, 31) })
        val fifa = interimFifaWindows(2026)
        assertTrue(brazilDates.any { fifa.reserves(GameWeek.of(it)) }, "o Brasileirão segue nas datas FIFA")
        assertTrue(plan.competitions.getValue(SERIE_A).season.pausesOnFifaWindows.not())
    }

    @Test
    fun `Europa joga ago-2026 a mai-2027 sem jogo em semana de data FIFA`() {
        val fifa = interimFifaWindows(2026..2027)
        val europeMatches = allMatches.flatMap { (date, ms) -> ms.filter { countryOf(it.competition) in europe }.map { date } }
        assertTrue(europeMatches.isNotEmpty())
        assertTrue(europeMatches.all { it in LocalDate.of(2026, 8, 1)..LocalDate.of(2027, 5, 31) })
        assertTrue(europeMatches.none { fifa.reserves(GameWeek.of(it)) })
    }

    @Test
    fun `o primeiro encaixe do mundo e do Brasil em janeiro e a Europa estreia em agosto`() {
        val first = assertNotNull(plan.first)
        assertEquals(LocalDate.of(2026, 1, 4), first.date)
        assertTrue(plan.roundAt(first, clubs).matches.all { countryOf(it.competition) == brazil })
        val europeFirst = plan.schedule.rounds.filter { countryOf(it.competition) in europe }.minOf { it.matchday }
        assertEquals(8, europeFirst.date.monthValue)
    }

    @Test
    fun `cada pais vira no seu tempo - Brasil em dezembro, Europa em maio`() {
        val brazilLast = plan.schedule.rounds.filter { countryOf(it.competition) == brazil }.maxOf { it.matchday }
        assertEquals(setOf(brazil), plan.countriesEndingBy(brazilLast))
        assertEquals(2026, brazilLast.date.year)
        val europeLast = plan.schedule.rounds.filter { countryOf(it.competition) in europe }.maxOf { it.matchday }
        assertEquals(2027, europeLast.date.year)
        assertTrue(europeLast.date.monthValue <= 5)
        assertEquals(europe + brazil, plan.countriesEndingBy(europeLast))
    }

    @Test
    fun `Brasil de 2027 convive com a Europa de 2026-27 sem mexer no calendario europeu`() {
        val mixed = worldPlan(catalog, clubs, start2026 + (brazil to 2027))
        fun europeRounds(p: SeasonPlan) = p.schedule.rounds.filter { countryOf(it.competition) in europe }.toSet()
        assertEquals(europeRounds(plan), europeRounds(mixed))
        val brazil2027 = mixed.schedule.rounds.filter { countryOf(it.competition) == brazil }
        assertTrue(brazil2027.all { it.matchday.date.year == 2027 })
        assertEquals(mapOf(brazil to 2027) + europe.associateWith { 2026 }, mixed.seasons)
    }

    @Test
    fun `startWorld abre as 7 tabelas com as faixas do mundo e o primeiro encaixe`() {
        val world = startWorld(2026, clubs, catalog)
        assertEquals(7, world.standings.size)
        assertEquals(listOf(20, 20, 20, 20, 20, 18, 18), world.standings.map { it.rows.size })
        val serieA = world.standings.single { it.competitionId == SERIE_A }
        assertEquals("2026", serieA.seasonLabel)
        assertEquals(List(6) { TableZone.QUALIFICATION } + List(10) { TableZone.NONE } + List(4) { TableZone.RELEGATION }, serieA.rows.map { it.zone })
        val serieB = world.standings.single { it.competitionId == SERIE_B }
        assertEquals(4, serieB.promotionSpots)
        val premier = world.standings.single { it.competitionId == CompetitionId("eng-premier-league") }
        assertEquals("2026/27", premier.seasonLabel)
        assertTrue(premier.rows.all { it.zone == TableZone.NONE })
        assertEquals(plan.first, world.firstRound?.matchday)
    }

    @Test
    fun `montar o plano do mundo inteiro e barato`() {
        // O serviço recalcula o plano a cada encaixe encerrado: tem de ser coisa
        // de milissegundos, não de segundos. Aquece uma vez e mede a média.
        worldPlan(catalog, clubs, start2026)
        val runs = 5
        val elapsed = measureTimeMillis { repeat(runs) { worldPlan(catalog, clubs, start2026) } }
        println("worldPlan (136 clubes, ${plan.schedule.rounds.size} rodadas): ${elapsed / runs} ms")
        assertTrue(elapsed / runs < 1_000, "plano do mundo levou ${elapsed / runs} ms")
    }

    @Test
    fun `clube sem liga nao entra no plano`() {
        val stray = clubs.first().copy(id = ClubId(999), division = Division(3))
        assertEquals(plan.schedule, worldPlan(catalog, clubs + stray, start2026).schedule)
    }
}
