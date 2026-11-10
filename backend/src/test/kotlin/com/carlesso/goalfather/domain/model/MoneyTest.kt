package com.carlesso.goalfather.domain.model

import com.carlesso.goalfather.domain.serialization.MoneySerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** `Money` (issue #122): aritmética, comparação e forma JSON. Domínio puro, sem Spring. */
class MoneyTest {

    @Test
    fun `soma subtrai e multiplica em centavos`() {
        assertEquals(Money(1_500), 1_000.cents + 500.cents)
        assertEquals(Money(500), 1_000.cents - 500.cents)
        assertEquals(Money(3_000), 1_000.cents * 3)
        assertEquals(Money(3_000_000_000_000), 1_000_000.cents * 3_000_000L)
        assertEquals(Money(-200), -(200.cents))
    }

    @Test
    fun `compara como dinheiro e respeita faixas`() {
        assertTrue(2_00.cents > 1_99.cents)
        assertTrue(1_00.cents in 50.cents..2_00.cents)
        assertFalse(3_00.cents in 50.cents..2_00.cents)
        assertEquals(Money.ZERO, (-5).cents.coerceAtLeast(Money.ZERO))
        assertEquals(Money.dollars(50), 50_00.cents)
    }

    @Test
    fun `sinal do valor fica disponivel sem abrir o Long`() {
        assertTrue((-1).cents.isNegative)
        assertTrue(1.cents.isPositive)
        assertFalse(Money.ZERO.isNegative)
        assertFalse(Money.ZERO.isPositive)
    }

    @Test
    fun `soma colecoes`() {
        assertEquals(Money(600), listOf(100.cents, 200.cents, 300.cents).sum())
        assertEquals(Money(6), listOf(1, 2, 3).sumOfMoney { it.cents })
        assertEquals(Money.ZERO, emptyList<Money>().sum())
    }

    @Test
    fun `serializa como inteiro cru de centavos`() {
        assertEquals("123456", Json.encodeToString(MoneySerializer, Money(123_456)))
        assertEquals(Money(99), Json.decodeFromString(MoneySerializer, "99"))
    }

    @Test
    fun `invariante de caixa nao negativo continua sendo do clube`() {
        // `Money` aceita negativo (saldo, déficit); o `Club` é quem recusa caixa < 0.
        assertFailsWith<IllegalArgumentException> {
            Club(id = ClubId(1), name = "X", cash = (-1).cents, stadiumCapacity = 0, squad = emptyList())
        }
    }
}
