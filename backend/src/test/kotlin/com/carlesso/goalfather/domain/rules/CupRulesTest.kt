package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Competition
import com.carlesso.goalfather.domain.model.CompetitionFormat
import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.Cup
import com.carlesso.goalfather.domain.model.CupLeg
import com.carlesso.goalfather.domain.model.CupTie
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.KnockoutStage
import com.carlesso.goalfather.domain.model.LegScore
import com.carlesso.goalfather.domain.model.Legs
import com.carlesso.goalfather.domain.model.Money
import com.carlesso.goalfather.domain.model.PenaltyScore
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundMatch
import com.carlesso.goalfather.domain.model.RoundStage
import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.domain.model.SeasonCompetitions
import com.carlesso.goalfather.domain.model.TieResult
import com.carlesso.goalfather.test.cupRound
import com.carlesso.goalfather.test.makeClub
import com.carlesso.goalfather.test.md
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.assertThrows
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CupRulesTest {

    // ─── Formatos do pack (issue #131) ────────────────────────────────────

    /** Corte do MVP da Copa do Brasil 2026: da 4ª fase (entrada da Série B) à final. */
    private val copaDoBrasil = Competition(
        CompetitionId("bra-copa-do-brasil"),
        "Copa do Brasil",
        CompetitionFormat.Knockout(
            listOf(
                KnockoutStage("Quarta fase", 16, Legs.SINGLE, newEntrants = 16),
                KnockoutStage("Quinta fase", 32, Legs.TWO_LEGGED, newEntrants = 24),
                KnockoutStage("Oitavas de final", 16, Legs.TWO_LEGGED),
                KnockoutStage("Quartas de final", 8, Legs.TWO_LEGGED),
                KnockoutStage("Semifinal", 4, Legs.TWO_LEGGED),
                KnockoutStage("Final", 2, Legs.SINGLE),
            ),
        ),
    )

    /** Pack reduzido do E2E: 12 clubes. */
    private val e2eCup = Competition(
        CompetitionId("bra-copa-do-brasil"),
        "Copa do Brasil",
        CompetitionFormat.Knockout(
            listOf(
                KnockoutStage("Primeira fase", 8, Legs.SINGLE, newEntrants = 8),
                KnockoutStage("Quartas de final", 8, Legs.TWO_LEGGED, newEntrants = 4),
                KnockoutStage("Semifinal", 4, Legs.TWO_LEGGED),
                KnockoutStage("Final", 2, Legs.SINGLE),
            ),
        ),
    )

    /** 40 clubes do pack, JÁ em ordem de força: id 1 é o mais forte; 1..20 Série A. */
    private val forty: List<Club> = (1L..40L).map {
        makeClub(id = it, name = "Club $it", division = Division(if (it <= 20) 1 else 2))
    }
    private val twelve: List<Club> = forty.take(12)

    private fun drawMvp(seed: Long = cupDrawSeed(2026)) = drawCup(2026, copaDoBrasil, forty, Random(seed))

    private fun drawE2e() = drawCup(2026, e2eCup, twelve, Random(cupDrawSeed(2026)))

    /**
     * Encerra o encaixe de copa da semana [week]: cada jogo termina com o placar
     * de [score]; se a perna for aos pênaltis (`decidesOnPenalties`), o
     * mandante vence a disputa 4 × 3.
     */
    private fun play(cup: Cup, week: Int, clubs: List<Club>, score: (RoundMatch) -> Pair<Int, Int>): Round {
        val round = cupRound(week, cup.season, cup, clubs)
        return round.copy(
            status = RoundStatus.Finished,
            matches = round.matches.map { match ->
                val (home, away) = score(match)
                match.copy(
                    status = RoundStatus.Finished,
                    homeGoals = home,
                    awayGoals = away,
                    penalties = PenaltyScore(4, 3).takeIf { match.decidesOnPenalties(home, away) },
                )
            },
        )
    }

    /** O mais forte (menor id) vence CADA jogo por 1 × 0 — em casa ou fora. */
    private val strongerWins: (RoundMatch) -> Pair<Int, Int> = { m ->
        if (m.homeClubId.value < m.awayClubId.value) 1 to 0 else 0 to 1
    }

    /** Joga a copa até o fim; devolve a copa final, os jogos e os encaixes usados. */
    private fun playToTheEnd(start: Cup, clubs: List<Club>): Triple<Cup, List<RoundMatch>, Int> {
        var cup = start
        val matches = mutableListOf<RoundMatch>()
        var slots = 0
        while (cup.currentStage != null) {
            val round = play(cup, week = 2 + slots, clubs = clubs, score = strongerWins)
            matches += round.matches
            slots++
            cup = cup.recordRound(round).cup
        }
        return Triple(cup, matches, slots)
    }

    // ─── Sorteio e entrantes ──────────────────────────────────────────────

    @Test
    fun `os 24 mais fortes entram na quinta fase e os 16 mais fracos jogam a quarta`() {
        val cup = drawMvp()

        assertEquals(listOf(16, 24, 0, 0, 0, 0), cup.phases.map { it.entrants.size })
        assertEquals((25L..40L).map(::ClubId), cup.phases[0].entrants.map { it.id })
        assertEquals((1L..24L).map(::ClubId), cup.phases[1].entrants.map { it.id })

        val opening = cup.stages.single()
        assertEquals("Quarta fase", opening.name)
        assertEquals(8, opening.ties.size)
        assertTrue(opening.ties.all { it is CupTie.Single })
        val inOpening = opening.ties.flatMap { listOf(it.home.id, it.away.id) }
        assertEquals((25L..40L).map(::ClubId).toSet(), inOpening.toSet())
        assertEquals(16, inOpening.size)
    }

    @Test
    fun `no formato do E2E os 4 mais fortes entram nas quartas`() {
        val cup = drawE2e()
        assertEquals((5L..12L).map(::ClubId), cup.phases[0].entrants.map { it.id })
        assertEquals((1L..4L).map(::ClubId), cup.phases[1].entrants.map { it.id })
        assertEquals("Primeira fase", cup.stages.single().name)
        assertEquals(4, cup.stages.single().ties.size)
    }

    @Test
    fun `mesma seed sorteia a mesma copa e seeds diferentes mudam o sorteio`() {
        assertEquals(drawMvp(), drawMvp())
        assertNotEquals(drawMvp(cupDrawSeed(2026)).stages, drawMvp(cupDrawSeed(2027)).stages)
        // Determinismo vale para a copa inteira, inclusive os sorteios das fases seguintes.
        assertEquals(playToTheEnd(drawMvp(), forty).first, playToTheEnd(drawMvp(), forty).first)
    }

    @Test
    fun `sorteio recusa formato que nao e mata-mata e numero de clubes que nao fecha`() {
        val league = copaDoBrasil.copy(format = CompetitionFormat.RoundRobin(legs = 2))
        assertThrows<IllegalArgumentException> { drawCup(2026, league, forty, Random(1)) }
        assertThrows<IllegalArgumentException> { drawCup(2026, copaDoBrasil, forty.drop(1), Random(1)) }
        assertThrows<IllegalArgumentException> {
            drawCup(2026, copaDoBrasil, forty.dropLast(1) + forty.first(), Random(1))
        }
    }

    @Test
    fun `encaixes do calendario somam uma perna por encaixe`() {
        assertEquals(1 + 2 + 2 + 2 + 2 + 1, drawMvp().calendarRounds)
        assertEquals(1 + 2 + 2 + 1, drawE2e().calendarRounds)
    }

    // ─── Copa inteira ─────────────────────────────────────────────────────

    @Test
    fun `copa do brasil do MVP tem 69 jogos em 10 encaixes e termina com campeao`() {
        val (cup, matches, slots) = playToTheEnd(drawMvp(), forty)

        assertEquals(8 + 32 + 16 + 8 + 4 + 1, matches.size)
        assertEquals(69, matches.size)
        assertEquals(cup.calendarRounds, slots)
        assertEquals(
            listOf("Quarta fase", "Quinta fase", "Oitavas de final", "Quartas de final", "Semifinal", "Final"),
            cup.stages.map { it.name },
        )
        // Com o mais forte vencendo todo jogo, o campeão é o 1º da lista.
        assertEquals(ClubId(1), assertNotNull(cup.champion).id)
        assertNull(cup.currentStage)
        assertTrue(matches.all { it.competition == copaDoBrasil.id && it.competitionName == "Copa do Brasil" })
        // Nenhum clube aparece duas vezes no mesmo encaixe.
        matches.groupBy { it.matchId / 1000 }.values.forEach { slot ->
            val clubs = slot.flatMap { listOf(it.homeClubId, it.awayClubId) }
            assertEquals(clubs.size, clubs.toSet().size)
        }
    }

    @Test
    fun `copa do E2E tem 4 + 8 + 4 + 1 jogos em 6 encaixes`() {
        val (cup, matches, slots) = playToTheEnd(drawE2e(), twelve)
        assertEquals(4 + 8 + 4 + 1, matches.size)
        assertEquals(6, slots)
        assertEquals(ClubId(1), cup.champion?.id)
    }

    @Test
    fun `fase completa sorteia a seguinte com os vencedores mais os entrantes dela`() {
        val cup = drawMvp()
        val outcome = cup.recordRound(play(cup, 2, forty, strongerWins))

        val winners = outcome.cup.stages.first().ties.map { assertNotNull(it.winner).id }
        val fifth = outcome.cup.stages.last()
        assertEquals("Quinta fase", fifth.name)
        assertEquals(16, fifth.ties.size)
        assertTrue(fifth.ties.all { it is CupTie.TwoLegged })
        val inFifth = fifth.ties.flatMap { listOf(it.home.id, it.away.id) }.toSet()
        assertEquals(winners.toSet() + (1L..24L).map(::ClubId), inFifth)
        assertEquals(32, inFifth.size)
    }

    // ─── Ida e volta ──────────────────────────────────────────────────────

    /** A copa do E2E com a 1ª fase jogada: as quartas (ida e volta) em disputa. */
    private fun quarterFinals(): Cup {
        val cup = drawE2e()
        return cup.recordRound(play(cup, 2, twelve, strongerWins)).cup.also {
            assertEquals("Quartas de final", it.currentStage?.name)
        }
    }

    @Test
    fun `ida empatada nao vai aos penaltis e so guarda o placar`() {
        val cup = quarterFinals()
        val firstLeg = cupRound(3, 2026, cup, twelve)
        assertTrue(firstLeg.matches.all { it.stage == RoundStage.Cup("Quartas de final", CupLeg.First) })
        assertTrue(firstLeg.matches.none { it.decidesOnPenalties(1, 1) })

        val outcome = cup.recordRound(play(cup, 3, twelve) { 1 to 1 })
        assertTrue(outcome.prizes.isEmpty())
        assertNotNull(outcome.cup.currentStage).ties.forEach { tie ->
            val twoLegged = assertIs<CupTie.TwoLegged>(tie)
            assertEquals(LegScore(1, 1), twoLegged.firstLeg)
            assertNull(twoLegged.winner)
            assertNull(twoLegged.penalties)
        }
    }

    @Test
    fun `volta tem o mando invertido e carrega o placar da ida`() {
        val cup = quarterFinals()
        val afterFirst = cup.recordRound(play(cup, 3, twelve) { 2 to 1 }).cup
        val secondLeg = cupRound(4, 2026, afterFirst, twelve)

        assertNotNull(afterFirst.currentStage).ties.forEach { tie ->
            val match = secondLeg.matches.single { it.matchId % 1000 == tie.index + 1L }
            assertEquals(tie.away.id, match.homeClubId)
            assertEquals(tie.home.id, match.awayClubId)
            assertEquals(RoundStage.Cup("Quartas de final", CupLeg.Second(LegScore(2, 1))), match.stage)
        }
    }

    @Test
    fun `volta com agregado empatado vai aos penaltis e decide por eles`() {
        val cup = quarterFinals()
        // Ida 2 × 1 para o mandante; volta 1 × 0 para o outro: agregado 2 × 2.
        val afterFirst = cup.recordRound(play(cup, 3, twelve) { 2 to 1 }).cup
        val secondLeg = cupRound(4, 2026, afterFirst, twelve)
        assertTrue(secondLeg.matches.all { it.decidesOnPenalties(1, 0) })
        assertTrue(secondLeg.matches.none { it.decidesOnPenalties(2, 0) || it.decidesOnPenalties(0, 0) })

        val outcome = afterFirst.recordRound(play(afterFirst, 4, twelve) { 1 to 0 })
        // Pênaltis 4 × 3 para o mandante da VOLTA — o `away` do confronto.
        outcome.cup.stages[1].ties.forEach { tie ->
            val twoLegged = assertIs<CupTie.TwoLegged>(tie)
            assertEquals(2, twoLegged.homeAggregate)
            assertEquals(2, twoLegged.awayAggregate)
            assertEquals(PenaltyScore(4, 3), twoLegged.penalties)
            assertEquals(tie.away, tie.winner)
        }
        assertEquals(outcome.cup.stages[1].ties.map { it.away.id }.toSet(), outcome.prizes.keys)
        assertEquals("Semifinal", outcome.cup.currentStage?.name)
    }

    @Test
    fun `volta com agregado empatado sem penaltis deixa o confronto em aberto`() {
        val cup = quarterFinals()
        val afterFirst = cup.recordRound(play(cup, 3, twelve) { 2 to 1 }).cup
        val round = cupRound(4, 2026, afterFirst, twelve)
        val noShootout = round.copy(matches = round.matches.map { it.copy(status = RoundStatus.Finished, homeGoals = 1) })

        val outcome = afterFirst.recordRound(noShootout)
        assertEquals(afterFirst, outcome.cup)
        assertTrue(outcome.prizes.isEmpty())
    }

    @Test
    fun `volta decidida no agregado passa quem fez mais gols nos dois jogos`() {
        val cup = quarterFinals()
        // Ida 3 × 0; volta 1 × 0 para o outro: agregado 3 × 1 — passa o mandante da ida.
        val afterFirst = cup.recordRound(play(cup, 3, twelve) { 3 to 0 }).cup
        val outcome = afterFirst.recordRound(play(afterFirst, 4, twelve) { 1 to 0 })
        outcome.cup.stages[1].ties.forEach { tie ->
            assertEquals(tie.home, tie.winner)
            assertNull(assertIs<CupTie.TwoLegged>(tie).penalties)
        }
    }

    @Test
    fun `final e jogo unico e o empate dela vai aos penaltis`() {
        var cup = drawE2e()
        var week = 2
        while (cup.currentStage?.name != "Final") cup = cup.recordRound(play(cup, week++, twelve, strongerWins)).cup

        val final = cupRound(week, 2026, cup, twelve).matches.single()
        assertEquals(RoundStage.Cup("Final", CupLeg.Single), final.stage)
        assertTrue(final.decidesOnPenalties(0, 0))
        assertFalse(final.decidesOnPenalties(1, 0))

        val outcome = cup.recordRound(play(cup, week, twelve) { 0 to 0 })
        val champion = assertNotNull(outcome.cup.champion)
        assertEquals(final.homeClubId, champion.id)
        assertEquals(mapOf(champion.id to CUP_FINAL_PRIZE), outcome.prizes)
    }

    @Test
    fun `jogo de liga nunca vai aos penaltis`() {
        val league = RoundMatch(1, ClubId(1), ClubId(2), "A", "B")
        assertFalse(league.decidesOnPenalties(0, 0))
    }

    // ─── Prêmios ──────────────────────────────────────────────────────────

    @Test
    fun `premio dobra a cada fase ate a semifinal e o campeao leva o maior cheque`() {
        val cup = drawMvp()
        val prizes = cup.phases.indices.map { cup.prizeOf(it) }
        assertEquals(
            listOf(125_000L, 250_000L, 500_000L, 1_000_000L, 2_000_000L, 10_000_000L).map { Money.dollars(it) },
            prizes,
        )
        assertEquals(prizes.max(), prizes.last())
        assertEquals(CUP_MIN_PRIZE, cupPhasePrize(20))
        assertEquals(CUP_MIN_PRIZE, cupPhasePrize(Int.MAX_VALUE))
        assertThrows<IllegalArgumentException> { cupPhasePrize(-1) }
    }

    @Test
    fun `quem passa de fase leva o premio dela e reaplicar a perna nao premia de novo`() {
        val cup = drawMvp()
        val round = play(cup, 2, forty, strongerWins)
        val once = cup.recordRound(round)
        val winners = once.cup.stages.first().ties.map { assertNotNull(it.winner).id }.toSet()
        assertEquals(winners, once.prizes.keys)
        assertTrue(once.prizes.values.all { it == cupPhasePrize(5) })

        val twice = once.cup.recordRound(round)
        assertEquals(once.cup, twice.cup)
        assertTrue(twice.prizes.isEmpty())
    }

    // ─── Robustez ─────────────────────────────────────────────────────────

    @Test
    fun `jogo unico empatado sem penaltis deixa o confronto em aberto`() {
        val cup = drawE2e()
        val round = cupRound(2, 2026, cup, twelve)
        val drawn = round.copy(matches = round.matches.map { it.copy(status = RoundStatus.Finished) })
        val outcome = cup.recordRound(drawn)
        assertEquals(cup, outcome.cup)
        assertTrue(outcome.prizes.isEmpty())
    }

    @Test
    fun `jogo de liga no mesmo encaixe nao decide confronto de copa (issue 125)`() {
        val cup = drawE2e()
        val cupGames = cupRound(2, 2026, cup, twelve)
        val leagueGames = cupGames.matches.map {
            it.copy(
                stage = RoundStage.League(1),
                competition = SeasonCompetitions.leagueId(Division.FIRST),
                status = RoundStatus.Finished,
                homeGoals = 2,
            )
        }
        val outcome = cup.recordRound(cupGames.copy(matches = leagueGames))
        assertEquals(cup, outcome.cup)
        assertTrue(outcome.prizes.isEmpty())
    }

    @Test
    fun `partidas da copa usam a divisao do mandante e nao colidem com a liga`() {
        val cup = drawMvp()
        val round = cupRound(2, 2026, cup, forty)
        val divisionOf = forty.associate { it.id to it.division }
        round.matches.forEach { assertEquals(divisionOf.getValue(it.homeClubId), it.division) }
        assertEquals(
            cup.stages.single().ties.map { md(2).serial * 1000L + it.index + 1 },
            round.matches.map { it.matchId },
        )
        assertTrue(round.matches.all { it.matchId % 1000 in 1L..99L })
    }

    @Test
    fun `resultados de mata-mata nao admitem confronto sem classificado`() {
        assertThrows<IllegalArgumentException> { TieResult(2, 2) }
        assertThrows<IllegalArgumentException> { TieResult(2, 1, PenaltyScore(5, 4)) }
        val tie = assertIs<CupTie.TwoLegged>(assertNotNull(quarterFinals().currentStage).ties.first())
        // Volta sem ida; agregado empatado sem pênaltis; pênaltis antes da volta.
        assertThrows<IllegalArgumentException> { tie.copy(secondLeg = LegScore(1, 0)) }
        assertThrows<IllegalArgumentException> { tie.copy(firstLeg = LegScore(1, 0), secondLeg = LegScore(1, 0)) }
        assertThrows<IllegalArgumentException> { tie.copy(firstLeg = LegScore(1, 0), penalties = PenaltyScore(4, 3)) }
    }

    @Test
    fun `copa em andamento nao tem campeao`() {
        assertNull(drawMvp().champion)
        assertEquals("Quarta fase", drawMvp().currentPhase?.name)
    }

    @Test
    fun `copa vai e volta do JSON sem perder nada`() {
        val finished = playToTheEnd(drawE2e(), twelve).first
        val halfway = drawE2e().let { it.recordRound(play(it, 2, twelve, strongerWins)).cup }
        listOf(finished, halfway).forEach {
            assertEquals(it, Json.decodeFromString(Cup.serializer(), Json.encodeToString(Cup.serializer(), it)))
        }
    }

    // ─── Ponte do mundo antigo (#131 integrador: remover com a ponte) ─────

    @Test
    fun `ponte de 12 clubes monta primeira fase com 8 e quartas com 4 entrantes`() {
        val cup = drawCup(2026, twelve, Random(cupDrawSeed(2026)))
        assertEquals(listOf("Primeira fase", "Quartas de final", "Semifinal", "Final"), cup.phases.map { it.name })
        assertEquals(listOf(8, 4, 0, 0), cup.phases.map { it.entrants.size })
        assertTrue(cup.phases.all { it.legs == Legs.SINGLE })
        assertEquals(SeasonCompetitions.CUP_ID, cup.competitionId)
        assertTrue(canHoldCup(twelve))
        assertFalse(canHoldCup(twelve.take(2)))
    }
}
