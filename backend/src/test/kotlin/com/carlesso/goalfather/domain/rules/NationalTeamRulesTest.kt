package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Availability
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.Countries
import com.carlesso.goalfather.domain.model.Country
import com.carlesso.goalfather.domain.model.CountryCode
import com.carlesso.goalfather.domain.model.FifaWindow
import com.carlesso.goalfather.domain.model.GameWeek
import com.carlesso.goalfather.domain.model.NationalMatch
import com.carlesso.goalfather.domain.model.NationalMatchKind
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.Position
import com.carlesso.goalfather.domain.model.Confederation
import com.carlesso.goalfather.test.SERIE_A
import com.carlesso.goalfather.test.makeClub
import com.carlesso.goalfather.test.makePlayer
import com.carlesso.goalfather.test.mvpCatalog
import com.carlesso.goalfather.test.mvpClubs
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Seleções nas datas FIFA (issue #134). Domínio puro: nenhuma linha de Spring.
 */
class NationalTeamRulesTest {

    private val march2026 = interimFifaWindows(2026).first()
    private val brazil = Countries.BRAZIL
    private val nigeria = Country(CountryCode("NGA"), "Nigéria", Confederation.CAF, "NG")

    /** [n] jogadores de [country], ids a partir de [firstId], overall decrescente e posições em rodízio. */
    private fun players(country: Country, n: Int, firstId: Long = 1, overall: Int = 80): List<Player> =
        (0 until n).map { i ->
            makePlayer(firstId + i, position = Position.entries[i % 4], overall = (overall - i / 4).coerceAtLeast(40))
                .copy(nationality = country)
        }

    private fun clubWith(id: Long, squad: List<Player>): Club = makeClub(id = id, name = "Clube $id").copy(squad = squad)

    // ─── Calendário ───────────────────────────────────────────────────────

    @Test
    fun `as datas de 2026 sao as do calendario FIFA e todas comecam na segunda`() {
        assertEquals(
            listOf(
                LocalDate.of(2026, 3, 23) to LocalDate.of(2026, 3, 31),
                LocalDate.of(2026, 6, 1) to LocalDate.of(2026, 6, 9),
                LocalDate.of(2026, 9, 21) to LocalDate.of(2026, 10, 6),
                LocalDate.of(2026, 11, 9) to LocalDate.of(2026, 11, 17),
            ),
            interimFifaWindows(2026).map { it.start to it.end },
        )
        assertTrue(interimFifaWindows(2026..2030).all { it.start.dayOfWeek == DayOfWeek.MONDAY })
        // 2027: 22/03, 20/09 e 08/11, como no calendário publicado.
        assertEquals(LocalDate.of(2027, 3, 22), interimFifaWindows(2027)[0].start)
        assertEquals(LocalDate.of(2027, 9, 20), interimFifaWindows(2027)[2].start)
        assertEquals(LocalDate.of(2027, 11, 8), interimFifaWindows(2027)[3].start)
        assertEquals(listOf(2, 2, 4, 2), interimFifaWindows(2027).map { it.matchSlots })
    }

    // ─── Convocação ───────────────────────────────────────────────────────

    @Test
    fun `a convocacao tem de 23 a 26 jogadores com as cotas por posicao e so aptos`() {
        val pool = players(brazil, 60) +
            makePlayer(500, position = Position.FW, overall = 99, availability = Availability.Injured(2)).copy(nationality = brazil)
        val called = pool.callUp(Random(1))
        assertTrue(called.size in NATIONAL_SQUAD_SIZE, "${called.size}")
        for ((position, quota) in NATIONAL_SQUAD_QUOTAS) {
            assertTrue(called.count { it.position == position } >= quota, "$position abaixo da cota")
        }
        assertTrue(called.none { it.id.value == 500L }, "lesionado não é convocado")
        // Os melhores de cada posição estão na lista.
        val bestGk = pool.filter { it.position == Position.GK && !it.injured }.maxBy { it.overall }
        assertTrue(bestGk in called)
    }

    @Test
    fun `a convocacao e deterministica pela seed e o sorteio so desempata overalls iguais`() {
        val pool = players(brazil, 60, overall = 75).map { it.copy(overall = 75) } // todos iguais: só o sorteio decide
        assertEquals(pool.callUp(Random(7)), pool.shuffled(Random(3)).callUp(Random(7)), "mesma seed, mesma lista")
        val seeds = (1..10).map { pool.callUp(Random(it.toLong())).map { p -> p.id }.toSet() }.toSet()
        assertTrue(seeds.size > 1, "com overalls iguais, seeds diferentes desempatam diferente")
    }

    @Test
    fun `tem selecao quem tem 11 aptos ou joga as Eliminatorias`() {
        val clubs = listOf(
            clubWith(1, players(brazil, 30, firstId = 1_001)),
            clubWith(2, players(nigeria, 10, firstId = 2_001)), // abaixo do corte
            clubWith(3, players(Countries.ARGENTINA, 3, firstId = 3_001)),
        )
        val squads = worldCallUps(march2026, clubs).associateBy { it.country }
        assertNotNull(squads[brazil.code])
        assertNull(squads[nigeria.code], "10 jogadores não formam seleção")
        assertEquals(3, squads.getValue(Countries.ARGENTINA.code).players.size, "CONMEBOL joga mesmo com 3")
        assertEquals(emptyList(), squads.getValue(Countries.BOLIVIA.code).players, "Bolívia sem jogador no mundo")
        assertEquals(CONMEBOL_QUALIFIERS.toSet(), squads.keys, "Nigéria fora; as 10 da CONMEBOL dentro")
        // A foto do convocado aponta o clube dele.
        assertTrue(squads.getValue(brazil.code).players.all { it.clubId.value == 1L && it.clubName == "Clube 1" })
        assertEquals(worldCallUps(march2026, clubs), worldCallUps(march2026, clubs.reversed()), "ordem dos clubes não importa")
    }

    // ─── Indisponibilidade ───────────────────────────────────────────────

    @Test
    fun `convocado fica fora de todos os jogos do clube na janela e volta depois`() {
        val squad = players(brazil, 16)
        val star = squad.maxBy { it.overall }
        val club = clubWith(1, squad)
        val competitions = listOf(SERIE_A, CompetitionId("bra-copa-do-brasil"), null)
        assertTrue(competitions.all { c -> star in club.startingLineup(c).players })

        val onDuty = club.copy(squad = club.squad.map { if (it.id == star.id) it.calledUpFor(brazil.code, march2026) else it })
        for (competition in competitions) {
            val lineup = onDuty.startingLineup(competition)
            assertTrue(lineup.players.none { it.id == star.id }, "convocado em campo por $competition")
            assertEquals(11, lineup.players.size, "o clube joga desfalcado, mas com 11")
        }
        // Desgaste da rodada do clube não devolve o convocado nem o lesiona.
        val afterClubRound = applyRoundFitness(onDuty.squad, starterIds = emptySet(), rng = Random(1))
        assertTrue(afterClubRound.single { it.id == star.id }.onInternationalDuty)

        // Fim da janela: volta e joga de novo.
        val back = onDuty.copy(squad = afterClubRound.map { it.returnFromDuty(DutyReport(starts = 2), Random(5)) })
        assertTrue(competitions.all { c -> back.startingLineup(c).players.any { it.id == star.id } })
    }

    @Test
    fun `a volta cobra a stamina dos jogos da selecao e traz a lesao sofrida la`() {
        val called = makePlayer(1, stamina = 100).calledUpFor(brazil.code, march2026)
        val tired = called.returnFromDuty(DutyReport(starts = 2), Random(1))
        assertEquals(Availability.Available, tired.availability)
        assertTrue(tired.stamina in (100 - 2 * STARTER_STAMINA_LOSS.last)..(100 - 2 * STARTER_STAMINA_LOSS.first))

        val hurt = called.returnFromDuty(DutyReport(starts = 1, injuryRounds = 3), Random(1))
        assertEquals(Availability.Injured(3), hurt.availability)

        val benched = called.returnFromDuty(null, Random(1))
        assertEquals(100, benched.stamina)
        assertEquals(Availability.Available, benched.availability)

        val notCalled = makePlayer(2, stamina = 70)
        assertEquals(notCalled, notCalled.returnFromDuty(DutyReport(starts = 4), Random(1)))
    }

    @Test
    fun `na data FIFA o Brasileirao joga desfalcado e as ligas europeias param`() {
        val catalog = mvpCatalog()
        val clubs = mvpClubs()
        val plan = worldPlan(catalog, clubs, catalog.associate { it.country.code to 2026 })
        val countryOf = catalog.associate { it.id to it.country.code }
        val fifaWeeks = interimFifaWindows(2026..2027).flatMap { w ->
            generateSequence(w.firstWeek) { it.next() }.takeWhile { it <= w.lastWeek }.toList()
        }.toSet()
        val inWindow = plan.schedule.matchdays.filter { it.week in fifaWeeks }
        val countries = inWindow.flatMap { md -> plan.roundAt(md, clubs).matches.map { countryOf.getValue(it.competition) } }.toSet()
        assertEquals(setOf(brazil.code), countries, "só o Brasil joga nas semanas de data FIFA")

        // O clube brasileiro dessas semanas entra sem o convocado.
        val md = inWindow.first()
        val match = plan.roundAt(md, clubs).matches.first()
        val home = clubs.single { it.id == match.homeClubId }
        val starter = home.startingLineup(match.competition).players.first()
        val window = interimFifaWindows(2026..2027).first { it.touches(md.week) }
        val depleted = home.copy(squad = home.squad.map { if (it.id == starter.id) it.calledUpFor(brazil.code, window) else it })
        assertFalse(depleted.startingLineup(match.competition).players.any { it.id == starter.id })
    }

    // ─── Eliminatórias ───────────────────────────────────────────────────

    @Test
    fun `as 18 rodadas das Eliminatorias ocupam as datas FIFA de dois anos`() {
        val rounds = interimFifaWindows(2026..2027).flatMap { qualifierRoundsOf(it, ::interimFifaWindows) }
        assertEquals((1..QUALIFIER_ROUNDS).toList(), rounds.filterNotNull())
        assertEquals(listOf(null, null), rounds.takeLast(2), "novembro do 2º ano: amistosos")
        // Ciclo seguinte recomeça em 2028.
        assertEquals(listOf(1, 2), qualifierRoundsOf(interimFifaWindows(2028).first(), ::interimFifaWindows))
        // 2026 = 10 jogos (2 + 2 + 4 + 2): rodadas 1–10; set/out tem 5–8.
        assertEquals(listOf(5, 6, 7, 8), qualifierRoundsOf(interimFifaWindows(2026)[2], ::interimFifaWindows))
    }

    @Test
    fun `turno e returno - cada par se enfrenta duas vezes com mando invertido`() {
        val schedule = qualifierSchedule(2026)
        assertEquals(QUALIFIER_ROUNDS, schedule.size)
        assertTrue(schedule.all { round -> round.size == 5 && round.flatMap { listOf(it.first, it.second) }.toSet().size == 10 })
        val games = schedule.flatten()
        assertEquals(90, games.size)
        assertEquals(games.size, games.toSet().size, "nenhum mando repetido")
        assertTrue(games.all { (home, away) -> (away to home) in games })
    }

    @Test
    fun `um ciclo inteiro jogado da a classificacao com 18 jogos por selecao`() {
        val clubs = listOf(clubWith(1, players(brazil, 40)), clubWith(2, players(Countries.ARGENTINA, 26, firstId = 2_001)))
        val matches = interimFifaWindows(2026..2027).flatMap { window ->
            val squads = worldCallUps(window, clubs)
            val byCountry = squads.associate { s ->
                s.country to clubs.flatMap { it.squad }.filter { p -> s.players.any { it.playerId == p.id } }
            }
            playWindow(window, byCountry, windowFixtures(window, squads.map { it.country })).matches
        }
        val qualifiers = matches.filter { it.kind == NationalMatchKind.QUALIFIER }
        assertEquals(90, qualifiers.size)
        val table = nationalTable(CONMEBOL_QUALIFIERS, qualifiers)
        assertEquals(10, table.size)
        assertTrue(table.all { it.played == 18 })
        assertEquals(table.sumOf { it.goalsFor }, table.sumOf { it.goalsAgainst })
        // Ordem por pontos (desc).
        assertEquals(table.sortedByDescending { it.points }.map { it.points }, table.map { it.points })
    }

    @Test
    fun `os jogos da janela sao deterministicos e ninguem joga duas vezes no mesmo jogo da janela`() {
        val teams = CONMEBOL_QUALIFIERS + listOf("ENG", "ESP", "FRA", "GER", "ITA").map(::CountryCode)
        val fixtures = windowFixtures(interimFifaWindows(2026)[2], teams)
        assertEquals(fixtures, windowFixtures(interimFifaWindows(2026)[2], teams.reversed()))
        for ((number, games) in fixtures.groupBy { it.matchNumber }) {
            val playing = games.flatMap { listOf(it.home, it.away) }
            assertEquals(playing.size, playing.toSet().size, "seleção em dois jogos no jogo $number")
            assertEquals(5, games.count { it.kind == NationalMatchKind.QUALIFIER })
        }
        assertEquals((1..4).toSet(), fixtures.map { it.matchNumber }.toSet())
    }

    @Test
    fun `playWindow so reporta convocados reais e e deterministico`() {
        val window = march2026
        val squad = players(brazil, 26)
        val fixtures = listOf(NationalFixture(brazil.code, CountryCode("BOL"), NationalMatchKind.FRIENDLY, 1))
        val a = playWindow(window, mapOf(brazil.code to squad), fixtures)
        val b = playWindow(window, mapOf(brazil.code to squad), fixtures)
        assertEquals(a, b)
        assertTrue(a.duty.keys.all { it.value > 0 }, "reforço estimado não tem efeito")
        assertEquals(11, a.duty.values.sumOf { it.starts })
    }

    // ─── Ranking ─────────────────────────────────────────────────────────

    @Test
    fun `o ranking soma 3-1-0 e a Eliminatoria vale o dobro`() {
        val bra = CountryCode("BRA")
        val arg = CountryCode("ARG")
        val eng = CountryCode("ENG")
        val ranking = nationalRanking(
            listOf(
                NationalMatch(bra, arg, NationalMatchKind.QUALIFIER, 1, homeGoals = 2, awayGoals = 0, qualifierRound = 1),
                NationalMatch(eng, arg, NationalMatchKind.FRIENDLY, 2, homeGoals = 1, awayGoals = 1),
            ),
        )
        assertEquals(mapOf(bra to 6, arg to 1, eng to 1), ranking.associate { it.country to it.points })
        // Empate em pontos: saldo (ARG −2, ENG 0) decide.
        assertEquals(listOf(bra, eng, arg), ranking.map { it.country })
    }

    @Test
    fun `semanas de uma janela`() {
        assertEquals(GameWeek(2026, 13), march2026.firstWeek)
        assertEquals(GameWeek(2026, 14), march2026.lastWeek)
        assertEquals(FifaWindow(march2026.start, march2026.end), march2026)
    }
}
