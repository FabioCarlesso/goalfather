package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Position
import com.carlesso.goalfather.test.makePlayer
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PenaltyRulesTest {

    private fun eleven(offset: Long, overall: Int = 75) = listOf(
        makePlayer(offset + 1, Position.GK, overall),
        makePlayer(offset + 2, Position.CB, overall),
        makePlayer(offset + 3, Position.CB, overall),
        makePlayer(offset + 4, Position.CB, overall),
        makePlayer(offset + 5, Position.CB, overall),
        makePlayer(offset + 6, Position.MF, overall),
        makePlayer(offset + 7, Position.MF, overall),
        makePlayer(offset + 8, Position.MF, overall),
        makePlayer(offset + 9, Position.MF, overall),
        makePlayer(offset + 10, Position.FW, overall),
        makePlayer(offset + 11, Position.FW, overall),
    )

    private val home = eleven(0)
    private val away = eleven(100)

    @Test
    fun `toda disputa termina com vencedor unico`() {
        repeat(500) { seed ->
            val shootout = penaltyShootout(home, away, Random(seed))
            assertNotEquals(shootout.score.home, shootout.score.away, "Seed $seed terminou empatada")
        }
    }

    @Test
    fun `placar e a contagem das cobrancas convertidas`() {
        repeat(200) { seed ->
            val shootout = penaltyShootout(home, away, Random(seed))
            assertEquals(shootout.kicks.count { it.home && it.scored }, shootout.score.home)
            assertEquals(shootout.kicks.count { !it.home && it.scored }, shootout.score.away)
        }
    }

    @Test
    fun `mesma seed produz a mesma disputa`() {
        assertEquals(penaltyShootout(home, away, Random(7)), penaltyShootout(home, away, Random(7)))
    }

    @Test
    fun `cobrancas alternam com o mandante batendo primeiro`() {
        repeat(100) { seed ->
            val kicks = penaltyShootout(home, away, Random(seed)).kicks
            kicks.forEachIndexed { i, kick -> assertEquals(i % 2 == 0, kick.home, "Seed $seed, cobrança $i") }
        }
    }

    @Test
    fun `serie regular tem no maximo 5 cobrancas por lado e encerra quando nao ha alcance`() {
        repeat(300) { seed ->
            val shootout = penaltyShootout(home, away, Random(seed))
            val kicks = shootout.kicks
            if (kicks.size < 2 * PENALTY_SERIES) {
                // Terminou antes da série completa: quem perdeu não alcançaria
                // nem convertendo todas as que faltavam.
                val homeLeft = PENALTY_SERIES - kicks.count { it.home }
                val awayLeft = PENALTY_SERIES - kicks.count { !it.home }
                val decided = shootout.score.home > shootout.score.away + awayLeft ||
                    shootout.score.away > shootout.score.home + homeLeft
                assertTrue(decided, "Seed $seed encerrou cedo sem estar decidida")
            } else {
                // Série completa ou morte súbita: os dois lados batem o mesmo número.
                assertEquals(kicks.count { it.home }, kicks.count { !it.home })
            }
        }
    }

    @Test
    fun `atacante bate antes e goleiro por ultimo`() {
        val takers = home.penaltyTakers()
        assertEquals(Position.FW, takers.first().position)
        assertEquals(Position.GK, takers.last().position)
    }

    @Test
    fun `conversao fica na faixa e cresce com a vantagem do batedor`() {
        val keeper = makePlayer(1, Position.GK, overall = 80)
        val weak = penaltyConversion(makePlayer(2, Position.FW, overall = 50), keeper)
        val strong = penaltyConversion(makePlayer(3, Position.FW, overall = 95), keeper)
        assertTrue(weak < strong)
        assertTrue(weak in PENALTY_CONVERSION_RANGE && strong in PENALTY_CONVERSION_RANGE)
    }

    @Test
    fun `lado sem jogadores perde todas as cobrancas`() {
        repeat(50) { seed ->
            val shootout = penaltyShootout(home, emptyList(), Random(seed))
            assertTrue(shootout.score.home > shootout.score.away)
            assertTrue(shootout.kicks.filterNot { it.home }.all { !it.scored && it.playerId == null })
        }
    }

    @Test
    fun `sem ninguem dos dois lados a vaga sai num sorteio, sem travar`() {
        val shootout = penaltyShootout(emptyList(), emptyList(), Random(1))
        assertNotEquals(shootout.score.home, shootout.score.away)
        assertTrue(shootout.kicks.isEmpty())
    }
}
