package com.carlesso.goalfather.domain.model

import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerTest {

    private fun makePlayer(overall: Int) = Player(
        id = PlayerId(1),
        name = "Test",
        position = Position.FW,
        overall = overall,
        pace = overall,
        shooting = overall,
        passing = overall,
        defending = overall,
        salary = 10_000,
        age = 25,
    )

    @Test
    fun `star e true quando overall maior ou igual a 82`() {
        assertTrue(makePlayer(82).star)
        assertTrue(makePlayer(95).star)
    }

    @Test
    fun `star e false quando overall abaixo de 82`() {
        assertFalse(makePlayer(81).star)
        assertFalse(makePlayer(0).star)
    }

    @Test
    fun `copy preserva padrao imutavel`() {
        val original = makePlayer(80)
        val modified = original.copy(overall = 85)
        assertEquals(80, original.overall, "Original nao deve mudar")
        assertEquals(85, modified.overall, "Copy reflete novo valor")
    }

    @Test
    fun `init rejeita overall fora de 0 a 99`() {
        assertThrows<IllegalArgumentException> { makePlayer(100) }
        assertThrows<IllegalArgumentException> { makePlayer(-1) }
    }

    @Test
    fun `PlayerId distingue jogadores diferentes`() {
        val a = PlayerId(1)
        val b = PlayerId(2)
        val a2 = PlayerId(1)
        assertEquals(a, a2)
        assertTrue(a != b)
    }
}
