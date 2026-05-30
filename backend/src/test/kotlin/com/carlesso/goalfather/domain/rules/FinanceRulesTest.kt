package com.carlesso.goalfather.domain.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FinanceRulesTest {

    @Test
    fun `taxa de ocupacao fica entre 50% e 100%`() {
        assertEquals(0.5, attendanceRate(0.0))   // piso
        assertEquals(0.5, attendanceRate(60.0))  // base
        assertEquals(1.0, attendanceRate(100.0)) // teto
        assertEquals(1.0, attendanceRate(120.0)) // clamp no teto
        val mid = attendanceRate(80.0)
        assertTrue(mid in 0.5..1.0, "Taxa intermediária fora da faixa: $mid")
    }

    @Test
    fun `bilheteria cresce com a forca do mandante`() {
        val fraco = ticketRevenue(stadiumCapacity = 10_000, homeStrength = 60.0)
        val forte = ticketRevenue(stadiumCapacity = 10_000, homeStrength = 90.0)
        assertTrue(forte > fraco, "Time mais forte deveria render mais bilheteria")
        // attendance = 10_000 * 0.5 = 5_000; receita = 5_000 * 50_00 = 25_000_000
        assertEquals(25_000_000L, fraco)
    }

    @Test
    fun `folha salarial cobrada a cada 2 rodadas`() {
        assertFalse(isSalaryRound(1))
        assertTrue(isSalaryRound(2))
        assertFalse(isSalaryRound(3))
        assertTrue(isSalaryRound(4))
    }
}
