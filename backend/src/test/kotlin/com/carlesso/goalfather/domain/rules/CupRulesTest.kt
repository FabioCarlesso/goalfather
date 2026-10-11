package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Cup
import com.carlesso.goalfather.domain.model.CupTie
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.KnockoutPhase
import com.carlesso.goalfather.domain.model.PenaltyScore
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundStage
import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.domain.model.TieResult
import com.carlesso.goalfather.test.makeClub
import org.junit.jupiter.api.assertThrows
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CupRulesTest {

    /** Liga do seed: 12 clubes, metade em cada divisão. */
    private val twelve = (1L..12L).map {
        makeClub(id = it, name = "Club $it", division = Division(if (it <= 6) 1 else 2))
    }
    private val eight = (1L..8L).map { makeClub(id = it, name = "Club $it") }

    private fun draw(clubs: List<com.carlesso.goalfather.domain.model.Club>, season: Int = 2026) =
        drawCup(season, clubs, Random(cupDrawSeed(season)))

    // ─── Sorteio ──────────────────────────────────────────────────────────

    @Test
    fun `mesma seed sorteia a mesma chave, em qualquer ordem de entrada`() {
        assertEquals(draw(twelve), draw(twelve))
        assertEquals(draw(twelve), draw(twelve.shuffled(Random(99))))
    }

    @Test
    fun `temporadas diferentes sorteiam chaves diferentes`() {
        assertNotEquals(draw(twelve, 2026).stages, draw(twelve, 2027).stages)
    }

    @Test
    fun `12 clubes viram oitavas com 4 folgas e todo clube aparece uma vez`() {
        val stage = draw(twelve).stages.single()
        assertEquals(KnockoutPhase.ROUND_OF_16, stage.phase)
        assertEquals(8, stage.ties.size)
        assertEquals(4, stage.ties.count { it is CupTie.Bye })

        val clubs = stage.ties.flatMap { tie ->
            when (tie) {
                is CupTie.Bye -> listOf(tie.club.id)
                is CupTie.Fixture -> listOf(tie.home.id, tie.away.id)
            }
        }
        assertEquals(twelve.map { it.id }.toSet(), clubs.toSet())
        assertEquals(12, clubs.size)
    }

    @Test
    fun `folgas ficam nos indices pares e nunca se cruzam na fase seguinte`() {
        val stage = draw(twelve).stages.single()
        val byeIndexes = stage.ties.filterIsInstance<CupTie.Bye>().map { it.index }
        assertEquals(listOf(0, 2, 4, 6), byeIndexes)
        // Pares (0,1), (2,3)… da fase seguinte: no máximo uma folga em cada.
        assertTrue(stage.ties.chunked(2).all { pair -> pair.count { it is CupTie.Bye } <= 1 })
    }

    @Test
    fun `potencia de 2 nao gera folga`() {
        val stage = draw(eight).stages.single()
        assertEquals(KnockoutPhase.QUARTER_FINALS, stage.phase)
        assertTrue(stage.ties.all { it is CupTie.Fixture })
    }

    @Test
    fun `liga pequena demais nao tem copa`() {
        val two = eight.take(2)
        assertTrue(!canHoldCup(two))
        assertThrows<IllegalArgumentException> { draw(two) }
    }

    @Test
    fun `fases da copa vao da abertura a final`() {
        assertEquals(
            listOf(
                KnockoutPhase.ROUND_OF_16,
                KnockoutPhase.QUARTER_FINALS,
                KnockoutPhase.SEMI_FINALS,
                KnockoutPhase.FINAL,
            ),
            draw(twelve).phases,
        )
    }

    // ─── Avanço da chave ──────────────────────────────────────────────────

    /** Rodada de copa encerrada em que o mandante de cada jogo vence por 1×0. */
    private fun homeWins(cup: Cup, number: Int = 2): Round {
        val round = cupRound(number, cup.season, cup.currentStage!!.phase, cup, twelve)
        return round.copy(
            status = RoundStatus.Finished,
            matches = round.matches.map { it.copy(status = RoundStatus.Finished, homeGoals = 1, awayGoals = 0) },
        )
    }

    @Test
    fun `rodada de copa tem so os confrontos em aberto da fase corrente`() {
        val cup = draw(twelve)
        val round = cupRound(2, 2026, KnockoutPhase.ROUND_OF_16, cup, twelve)

        assertEquals(RoundStage.Cup(KnockoutPhase.ROUND_OF_16), round.stage)
        assertTrue(round.isKnockout)
        // 4 jogos (as folgas não entram em campo), matchId = data × 1000 + índice + 1.
        val fixtures = cup.stages.single().ties.filterIsInstance<CupTie.Fixture>()
        assertEquals(fixtures.map { 2000L + it.index + 1 }, round.matches.map { it.matchId })
    }

    @Test
    fun `vencedores avancam pela chave e a fase seguinte nasce sorteada`() {
        val cup = draw(twelve)
        val outcome = cup.recordRound(homeWins(cup))

        assertEquals(2, outcome.cup.stages.size)
        val quarters = outcome.cup.stages.last()
        assertEquals(KnockoutPhase.QUARTER_FINALS, quarters.phase)
        assertEquals(4, quarters.ties.size)

        // Confronto k das quartas = vencedor de 2k × vencedor de 2k+1, nessa ordem.
        val r16 = outcome.cup.stages.first()
        quarters.ties.forEachIndexed { k, tie ->
            assertIs<CupTie.Fixture>(tie)
            assertEquals(r16.ties[2 * k].winner, tie.home)
            assertEquals(r16.ties[2 * k + 1].winner, tie.away)
        }
    }

    @Test
    fun `vencedor do confronto leva o premio da fase e folga nao premia`() {
        val cup = draw(twelve)
        val outcome = cup.recordRound(homeWins(cup))

        val fixtures = outcome.cup.stages.first().ties.filterIsInstance<CupTie.Fixture>()
        assertEquals(fixtures.map { it.home.id }.toSet(), outcome.prizes.keys)
        assertTrue(outcome.prizes.values.all { it == KnockoutPhase.ROUND_OF_16.prizeCents })
        val byeClubs = outcome.cup.stages.first().ties.filterIsInstance<CupTie.Bye>().map { it.club.id }
        assertTrue(byeClubs.none { it in outcome.prizes })
    }

    @Test
    fun `reaplicar a mesma rodada nao premia de novo nem muda a chave`() {
        val cup = draw(twelve)
        val round = homeWins(cup)
        val once = cup.recordRound(round)
        // Mesma rodada contra a copa já atualizada: a fase corrente agora é a
        // seguinte, onde esses pares não existem.
        val twice = once.cup.recordRound(round)
        assertEquals(once.cup, twice.cup)
        assertTrue(twice.prizes.isEmpty())
    }

    @Test
    fun `empate com penaltis classifica quem venceu a disputa`() {
        val cup = draw(eight)
        val round = cupRound(2, 2026, KnockoutPhase.QUARTER_FINALS, cup, eight)
        val finished = round.copy(
            matches = round.matches.map {
                it.copy(status = RoundStatus.Finished, homeGoals = 1, awayGoals = 1, penalties = PenaltyScore(3, 4))
            },
        )
        val stage = cup.recordRound(finished).cup.stages.first()
        stage.ties.filterIsInstance<CupTie.Fixture>().forEach { tie ->
            assertEquals(tie.away, tie.winner)
            assertEquals(TieResult(1, 1, PenaltyScore(3, 4)), tie.result)
        }
    }

    @Test
    fun `empate sem penaltis deixa o confronto em aberto em vez de estourar`() {
        val cup = draw(eight)
        val round = cupRound(2, 2026, KnockoutPhase.QUARTER_FINALS, cup, eight)
        val drawn = round.copy(matches = round.matches.map { it.copy(status = RoundStatus.Finished) })

        val outcome = cup.recordRound(drawn)
        assertEquals(cup, outcome.cup)
        assertTrue(outcome.prizes.isEmpty())
    }

    @Test
    fun `copa jogada ate o fim tem campeao e nenhuma fase em disputa`() {
        var cup = draw(twelve)
        var date = 2
        while (cup.currentStage != null) {
            cup = cup.recordRound(homeWins(cup, date++)).cup
        }
        assertEquals(KnockoutPhase.FINAL, cup.stages.last().phase)
        assertEquals(4, cup.stages.size)
        val champion = assertNotNull(cup.champion)
        assertEquals(cup.stages.last().ties.single().winner, champion)
    }

    @Test
    fun `copa em andamento nao tem campeao`() {
        assertNull(draw(twelve).champion)
    }

    @Test
    fun `resultado de mata-mata nao admite empate sem penaltis`() {
        assertThrows<IllegalArgumentException> { TieResult(2, 2) }
        assertThrows<IllegalArgumentException> { TieResult(2, 1, PenaltyScore(5, 4)) }
        assertThrows<IllegalArgumentException> { TieResult(0, 0, PenaltyScore(4, 4)) }
    }

    @Test
    fun `mandante e divisao da partida vem do clube da casa`() {
        val cup = draw(twelve)
        val round = cupRound(2, 2026, KnockoutPhase.ROUND_OF_16, cup, twelve)
        val divisionOf = twelve.associate { it.id to it.division }
        round.matches.forEach { assertEquals(divisionOf.getValue(it.homeClubId), it.division) }
        // Nenhuma partida de copa colide com a fórmula das de liga (× 1000 + 101..).
        assertTrue(round.matches.all { it.matchId % 1000 in 1L..99L })
        assertTrue(round.matches.map { it.homeClubId }.none { it == ClubId(0) })
    }
}
