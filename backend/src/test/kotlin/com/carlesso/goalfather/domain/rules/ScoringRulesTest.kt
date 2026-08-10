package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.Position
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Sorteio ponderado do finalizador (issue #57) — sem Spring, com RNG
 * injetado, como manda o CLAUDE.md.
 */
class ScoringRulesTest {

    private fun player(id: Long, position: Position) = Player(
        id = PlayerId(id),
        name = "Player$id",
        position = position,
        overall = 75,
        pace = 75,
        shooting = 75,
        passing = 75,
        defending = 75,
        salary = 10_000,
        age = 25,
    )

    private val squad = listOf(
        player(1, Position.GK),
        player(2, Position.CB),
        player(3, Position.CB),
        player(4, Position.MF),
        player(5, Position.FW),
    )

    @Test
    fun `goleiro nunca eh sorteado`() {
        val rng = Random(7)
        val keeper = squad.first().id
        repeat(5_000) {
            assertTrue(squad.drawShooter(rng)?.id != keeper)
        }
    }

    @Test
    fun `frequencia acompanha o peso da posicao`() {
        val rng = Random(7)
        val draws = (1..20_000).mapNotNull { squad.drawShooter(rng)?.position }
        val counts = draws.groupingBy { it }.eachCount()

        // Pesos: FW 6, MF 3, CB 1 (×2 em campo) → FW > MF > CB somados.
        val forwards = counts[Position.FW] ?: 0
        val midfielders = counts[Position.MF] ?: 0
        val defenders = counts[Position.CB] ?: 0

        assertTrue(forwards > midfielders, "FW: $forwards, MF: $midfielders")
        assertTrue(midfielders > defenders, "MF: $midfielders, CB: $defenders")

        // 6 / (0+1+1+3+6) = 54,5% para o atacante. Margem folgada para o RNG.
        val share = forwards.toDouble() / draws.size
        assertTrue(share in 0.50..0.59, "fatia do atacante fora do esperado: $share")
    }

    @Test
    fun `sem ninguem apto devolve null sem consumir sorteio`() {
        assertNull(emptyList<Player>().drawShooter(Random(1)))
        assertNull(listOf(player(1, Position.GK)).drawShooter(Random(1)))

        // Nada foi sorteado: dois RNGs com a mesma seed seguem alinhados.
        val a = Random(1)
        val b = Random(1)
        listOf(player(1, Position.GK)).drawShooter(a)
        assertEquals(b.nextDouble(), a.nextDouble())
    }

    @Test
    fun `goleiro em campo eh o primeiro escalado na posicao`() {
        assertEquals(PlayerId(1), squad.goalkeeper()?.id)
        assertNull(squad.filter { it.position != Position.GK }.goalkeeper())
    }
}
