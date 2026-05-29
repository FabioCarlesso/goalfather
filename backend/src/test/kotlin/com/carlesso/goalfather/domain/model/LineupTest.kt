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
}
