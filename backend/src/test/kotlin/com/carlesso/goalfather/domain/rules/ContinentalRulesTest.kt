package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.ContinentalCup
import com.carlesso.goalfather.domain.model.Countries
import com.carlesso.goalfather.domain.model.CupTie
import com.carlesso.goalfather.domain.model.Matchday
import com.carlesso.goalfather.domain.model.PenaltyScore
import com.carlesso.goalfather.domain.model.QualificationRule
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundMatch
import com.carlesso.goalfather.domain.model.RoundStage
import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.domain.model.Slot
import com.carlesso.goalfather.domain.model.StandingRow
import com.carlesso.goalfather.domain.model.Venue
import com.carlesso.goalfather.domain.model.WorldQualificationRules
import com.carlesso.goalfather.test.COPA_DO_BRASIL
import com.carlesso.goalfather.test.SERIE_A
import com.carlesso.goalfather.test.foreignContinentalClubs
import com.carlesso.goalfather.test.libertadoresGame
import com.carlesso.goalfather.test.mvpCatalog
import com.carlesso.goalfather.test.mvpClubs
import com.carlesso.goalfather.test.testStandings
import java.time.LocalDate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A Libertadores do MVP (issue #132) como regra pura: sorteio sem
 * compatriotas no grupo, grupos → mata-mata, final única em sede neutra,
 * campeão e a cota da edição seguinte. Sem Spring.
 */
class ContinentalRulesTest {

    private val game = libertadoresGame()
    private val brazilians = mvpClubs().filter { it.country == Countries.BRAZIL }
    private val foreign = foreignContinentalClubs()
    private val entrants = brazilians.take(6) + foreign
    private val venue = Venue("Estadio Monumental U", 80_093)

    private fun draw(seed: Long): ContinentalCup {
        val result = drawContinental(2026, game, seededForContinental(entrants, null), venue, Random(seed))
        return assertIs<ContinentalDraw.Drawn>(result).edition
    }

    @Test
    fun `o sorteio nunca poe dois clubes do mesmo pais no mesmo grupo e e deterministico`() {
        repeat(50) { seed ->
            val edition = draw(seed.toLong())
            assertEquals(8, edition.groups.size)
            assertEquals(32, edition.clubs.size)
            edition.groups.forEach { group ->
                assertEquals(4, group.clubs.size)
                assertEquals(4, group.clubs.map { it.country }.distinct().size, "compatriotas no ${group.name} (seed $seed)")
            }
        }
        assertEquals(draw(7), draw(7))
    }

    @Test
    fun `cada grupo tem um cabeca de chave de cada pote`() {
        val seeded = seededForContinental(entrants, null).map { it.id }
        val pots = seeded.chunked(8).map { it.toSet() }
        draw(3).groups.forEach { group ->
            assertEquals(List(pots.size) { 1 }, pots.map { pot -> group.clubs.count { it.id in pot } })
        }
    }

    @Test
    fun `pais com mais clubes que grupos torna o sorteio impossivel`() {
        val argentines = foreign.filter { it.country == Countries.ARGENTINA }
        val tooMany = (1..9L).map { argentines.first().copy(id = ClubId(5_000 + it)) } +
            entrants.filterNot { it.country == Countries.ARGENTINA }.take(23)
        assertEquals(ContinentalDraw.Impossible, drawContinental(2026, game, tooMany, venue, Random(1)))
        assertEquals(ContinentalDraw.WrongSize(32, 31), drawContinental(2026, game, entrants.drop(1), venue, Random(1)))
    }

    @Test
    fun `seis rodadas de grupo, mata-mata com 1o contra 2o de outro grupo e final em sede neutra`() {
        var edition = draw(11)
        assertEquals(6, edition.groupRounds)
        assertEquals(13, edition.calendarRounds)

        var round = 0
        var matchday = Matchday.of(LocalDate.of(2026, 3, 4), Slot.MIDWEEK)
        val prizes = mutableMapOf<ClubId, Long>()
        while (edition.champion == null) {
            round++
            val matches = continentalMatches(matchday, edition, round)
            assertTrue(matches.isNotEmpty(), "rodada $round sem jogo")
            val clubsInRound = matches.flatMap { listOf(it.homeClubId, it.awayClubId) }
            assertEquals(clubsInRound.size, clubsInRound.toSet().size, "clube duas vezes na rodada $round")
            if (round <= 6) assertTrue(matches.all { it.stage is RoundStage.Group } && matches.size == 16)

            val outcome = edition.recordRound(play(matchday, matches), game.competition.tiebreakers)
            outcome.prizes.forEach { (club, prize) -> prizes.merge(club, prize.cents, Long::plus) }
            edition = outcome.edition

            if (round == 6) {
                val knockout = assertNotNull(edition.knockout, "mata-mata montado ao fim dos grupos")
                val groupOf = edition.groups.flatMap { g -> g.clubs.map { it.id to g.name } }.toMap()
                val tables = edition.groups.associate { it.name to edition.groupTable(it, game.competition.tiebreakers) }
                val winners = tables.values.map { it.first().clubId }.toSet()
                val ties = knockout.stages.single().ties
                assertEquals(8, ties.size)
                ties.forEach { tie ->
                    assertIs<CupTie.TwoLegged>(tie)
                    // O 2º recebe a ida; o 1º decide em casa.
                    assertTrue(tie.away.id in winners && tie.home.id !in winners)
                    assertTrue(groupOf.getValue(tie.home.id) != groupOf.getValue(tie.away.id))
                }
                // 3º e 4º estão fora.
                val thirds = tables.values.map { it[2].clubId }
                assertTrue(thirds.none { third -> knockout.phases.first().entrants.any { it.id == third } })
            }
            if (edition.knockout?.isNeutralFinal == true) {
                val final = continentalMatches(matchday.next(), edition, round + 1)
                assertEquals(listOf(venue.name), final.map { it.neutralVenue })
            }
            matchday = matchday.next().next()
        }
        assertEquals(13, round, "6 de grupo + 2+2+2 + final única")
        // O campeão venceu oitavas, quartas, semi e final: o dobro da escada da copa.
        val champion = assertNotNull(edition.champion)
        assertEquals((0..3).sumOf { cupPhasePrize(it).cents * CONTINENTAL_PRIZE_FACTOR }, prizes.getValue(champion.id))
        assertEquals(champion, edition.title()?.champion)
    }

    /** Joga o encaixe: o mandante vence por 2 × 1 (e os pênaltis, se o agregado empatar). */
    private fun play(matchday: Matchday, matches: List<RoundMatch>): Round = Round(
        matchday = matchday,
        season = 2026,
        status = RoundStatus.Finished,
        matches = matches.map { m ->
            m.copy(
                status = RoundStatus.Finished,
                homeGoals = 2,
                awayGoals = 1,
                // Volta que empata o agregado: decide nos pênaltis.
                penalties = if (m.decidesOnPenalties(2, 1)) PenaltyScore(5, 4) else null,
            )
        },
    )

    @Test
    fun `edicao seguinte repete a cota de cada pais e o campeao entra mesmo fora do G-6`() {
        val previous = draw(5)
        // Campeão brasileiro que terminou em 15º: entra pela vaga do título.
        val titleholder = brazilians[14]
        val qualified = brazilians.take(7).map { it.id }
        val next = continentalEntrants(
            previous = previous,
            qualified = qualified,
            clubs = brazilians + foreign,
            leagueCountries = setOf(Countries.BRAZIL.code),
            titleholder = titleholder.id,
        )
        assertEquals(32, next.size)
        assertEquals(previous.quotas, next.groupingBy { it.country.code }.eachCount())
        val brazil = next.filter { it.country == Countries.BRAZIL }.map { it.id }
        assertEquals(listOf(titleholder.id) + qualified.take(5), brazil)
        // Cabeça de chave nº 1: o campeão.
        assertEquals(titleholder.id, seededForContinental(next, titleholder.id).first().id)
    }

    @Test
    fun `classificados pela cascata - campeao da copa, G-4 e 5o e 6o sem preliminar`() {
        val rows = brazilians.take(20).mapIndexed { i, c -> StandingRow(position = i + 1, clubId = c.id, clubName = c.name) }
        val table = testStandings(rows = rows)
        val cupChampion = brazilians[9].id
        val rules = listOf<QualificationRule>(
            QualificationRule.Champion(COPA_DO_BRASIL, WorldQualificationRules.CONMEBOL_LIBERTADORES, "Fase de grupos"),
            QualificationRule.TopN(SERIE_A, 4, WorldQualificationRules.CONMEBOL_LIBERTADORES, "Fase de grupos"),
            QualificationRule.TopN(SERIE_A, 2, WorldQualificationRules.CONMEBOL_LIBERTADORES, "Segunda fase"),
            QualificationRule.Relegation(SERIE_A, 4, com.carlesso.goalfather.test.SERIE_B),
        )
        val qualified = continentalQualifiers(
            WorldQualificationRules.CONMEBOL_LIBERTADORES, rules, listOf(table), mapOf(COPA_DO_BRASIL to cupChampion),
        )
        assertEquals(listOf(cupChampion) + brazilians.take(6).map { it.id }, qualified)
        // Copa sem campeão (não chegou à final): a regra cai, sem exception.
        assertEquals(6, continentalQualifiers(WorldQualificationRules.CONMEBOL_LIBERTADORES, rules, listOf(table), emptyMap()).size)
    }

    @Test
    fun `no calendario a Libertadores joga so no meio de semana sem colidir com Brasileirao e Copa do Brasil`() {
        val catalog = mvpCatalog() + game
        val clubs = mvpClubs() + foreign
        val edition = draw(9)
        val cups = mapOf(
            COPA_DO_BRASIL to drawCup(2026, catalog.single { it.id == COPA_DO_BRASIL }.competition, seededForCup(mvpClubs().filter { it.country == Countries.BRAZIL }), Random(1)),
        )
        val plan = worldPlan(catalog, clubs, mapOf(Countries.BRAZIL.code to 2026), cups, mapOf(game.id to edition))
        val libertadores = plan.schedule.rounds.filter { it.competition == game.id }
        assertEquals(13, libertadores.size)
        assertTrue(libertadores.all { it.matchday.slot == Slot.MIDWEEK })
        plan.schedule.occupancy() // sem exceção
        plan.schedule.rounds.groupBy { it.matchday }.forEach { (matchday, rounds) ->
            val all = rounds.flatMap { it.clubs }
            assertEquals(all.size, all.toSet().size, "clube em dois jogos em ${matchday.key}")
        }
        // Os jogos de grupo aparecem no encaixe, com a competição indicada.
        val first = plan.roundAt(libertadores.first().matchday, clubs)
        assertTrue(first.matches.any { it.competition == game.id && it.stage == RoundStage.Group("Grupo A", 1) })
        // A final sai antes de dezembro (a janela vai até novembro), antes da virada do Brasil.
        assertNull(plan.schedule.rounds.firstOrNull { it.competition == game.id && it.matchday.date.monthValue == 12 })
    }
}
