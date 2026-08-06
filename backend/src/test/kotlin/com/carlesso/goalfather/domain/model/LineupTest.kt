package com.carlesso.goalfather.domain.model

import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LineupTest {

    private fun player(id: Long, overall: Int) = Player(
        id = PlayerId(id),
        name = "P$id",
        position = Position.MF,
        overall = overall,
        pace = overall,
        shooting = overall,
        passing = overall,
        defending = overall,
        salary = 10_000,
        age = 25,
    )

    @Test
    fun `teamStrength e a media de overall`() {
        val lineup = Lineup(
            formation = Formation.F_4_4_2,
            players = listOf(player(1, 80), player(2, 60)),
        )
        assertEquals(70.0, lineup.teamStrength())
    }

    @Test
    fun `teamStrength default para 60 em lineup vazio`() {
        val lineup = Lineup(formation = Formation.F_4_4_2, players = emptyList())
        assertEquals(60.0, lineup.teamStrength())
    }

    @Test
    fun `isComplete e true quando 11 jogadores`() {
        val lineup = Lineup(
            formation = Formation.F_4_4_2,
            players = (1..11).map { player(it.toLong(), 70) },
        )
        assertTrue(lineup.isComplete)
    }

    @Test
    fun `isComplete e false quando menos de 11`() {
        val lineup = Lineup(formation = Formation.F_4_4_2, players = listOf(player(1, 70)))
        assertFalse(lineup.isComplete)
    }

    @Test
    fun `Lineup rejeita mais de 11 jogadores`() {
        assertThrows<IllegalArgumentException> {
            Lineup(
                formation = Formation.F_4_4_2,
                players = (1..12).map { player(it.toLong(), 70) },
            )
        }
    }

    @Test
    fun `Formation slots tem sempre 11 posicoes`() {
        for (formation in Formation.entries) {
            assertEquals(11, formation.slots.size, "${formation.label} deve ter 11 slots")
        }
    }

    @Test
    fun `Formation fromLabel resolve nome do contrato`() {
        assertEquals(Formation.F_4_4_2, Formation.fromLabel("4-4-2"))
        assertEquals(Formation.F_4_3_3, Formation.fromLabel("4-3-3"))
        assertThrows<IllegalArgumentException> { Formation.fromLabel("9-9-9") }
    }

    // ─── Tática (issue #56) ────────────────────────────────────────────────

    @Test
    fun `lineup sem tatica explicita entra EQUILIBRADA`() {
        val lineup = Lineup(formation = Formation.F_4_4_2, players = listOf(player(1, 70)))
        assertEquals(Posture.BALANCED, lineup.tactics.posture)
    }

    @Test
    fun `EQUILIBRADA em 4-4-2 e o eixo neutro`() {
        val lineup = Lineup(formation = Formation.F_4_4_2, players = emptyList())
        assertEquals(1.0, lineup.attackFactor())
        assertEquals(1.0, lineup.defenseFactor())
    }

    @Test
    fun `OFENSIVA ataca mais e concede mais que DEFENSIVA`() {
        val base = Lineup(formation = Formation.F_4_4_2, players = emptyList())
        val offensive = base.copy(tactics = Tactics(Posture.OFFENSIVE))
        val defensive = base.copy(tactics = Tactics(Posture.DEFENSIVE))

        assertTrue(offensive.attackFactor() > base.attackFactor())
        assertTrue(offensive.defenseFactor() > base.defenseFactor())
        assertTrue(defensive.attackFactor() < base.attackFactor())
        assertTrue(defensive.defenseFactor() < base.defenseFactor())
    }

    @Test
    fun `DEFENSIVA sacrifica menos defesa do que ataque`() {
        // O "leve bônus defensivo": fechar o time custa mais em criação do que
        // rende em concessão — por isso a razão ataque÷defesa cai.
        val defensive = Lineup(
            formation = Formation.F_4_4_2,
            players = emptyList(),
            tactics = Tactics(Posture.DEFENSIVE),
        )
        assertTrue(defensive.attackFactor() < defensive.defenseFactor())
    }

    @Test
    fun `formacao inclina os fatores sem inverter a postura`() {
        val attacking = Lineup(players = emptyList(), formation = Formation.F_4_3_3)
        val defending = Lineup(players = emptyList(), formation = Formation.F_5_3_2)

        assertTrue(attacking.attackFactor() > 1.0)
        assertTrue(defending.attackFactor() < 1.0)
        // Postura pesa mais que formação: 4-3-3 DEFENSIVA ainda é mais fechada
        // que 5-3-2 EQUILIBRADA.
        val defensive433 = attacking.copy(tactics = Tactics(Posture.DEFENSIVE))
        assertTrue(defensive433.attackFactor() < defending.attackFactor())
    }
}
