package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.test.makeClub
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Regras puras de ampliação do estádio (issue #79) — sem Spring. */
class StadiumRulesTest {

    @Test
    fun `o minimo de assentos e aceito e qualquer coisa abaixo nao`() {
        assertTrue(isStadiumExpansionAllowed(MIN_STADIUM_EXPANSION_SEATS))
        assertTrue(isStadiumExpansionAllowed(50_000))
        assertFalse(isStadiumExpansionAllowed(MIN_STADIUM_EXPANSION_SEATS - 1))
        assertFalse(isStadiumExpansionAllowed(0))
        assertFalse(isStadiumExpansionAllowed(-10_000))
    }

    @Test
    fun `custo e R$ 100 por assento`() {
        assertEquals(100_000_00, stadiumExpansionCost(1_000))
        assertEquals(1_000 * STADIUM_SEAT_COST_CENTS, stadiumExpansionCost(1_000))
    }

    @Test
    fun `custo nao estoura Int para pedidos enormes`() {
        assertEquals(Int.MAX_VALUE.toLong() * 100_00, stadiumExpansionCost(Int.MAX_VALUE))
    }

    @Test
    fun `obra debita o caixa e soma a capacidade`() {
        val club = makeClub(cash = 500_000_00)

        val expanded = club.withExpandedStadium(1_000)

        assertEquals(400_000_00, expanded.cash)
        assertEquals(club.stadiumCapacity + 1_000, expanded.stadiumCapacity)
    }
}
