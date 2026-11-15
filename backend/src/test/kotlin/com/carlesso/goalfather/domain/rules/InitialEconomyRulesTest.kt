package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Money
import com.carlesso.goalfather.domain.model.world.LeagueStrength
import com.carlesso.goalfather.domain.model.world.Reputation
import com.carlesso.goalfather.test.makePlayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Economia inicial do mundo do pack (issue #131): regras puras, sem `Random`. */
class InitialEconomyRulesTest {

    @Test
    fun `salario dobra a cada 10 pontos de overall a partir de US$ 1500 no 60`() {
        assertEquals(Money.dollars(1_500), initialSalary(60))
        assertEquals(Money.dollars(3_000), initialSalary(70))
        assertEquals(Money.dollars(6_000), initialSalary(80))
        assertEquals(Money.dollars(12_000), initialSalary(90))
        // Arredondado a US$ 10.
        assertEquals(0L, initialSalary(77).cents % 10_00)
    }

    @Test
    fun `salario tem piso de US$ 500`() {
        assertEquals(Money.dollars(500), initialSalary(35))
    }

    @Test
    fun `caixa e um numero de folhas que cresce com a reputacao`() {
        val squad = List(10) { makePlayer(it + 1L, salary = 1_000_00) } // folha: US$ 10 mil
        // rep 50 → 4 + 5 = 9 folhas; rep 90 → 13 folhas.
        assertEquals(Money.dollars(90_000), initialCash(Reputation(50), squad))
        assertEquals(Money.dollars(130_000), initialCash(Reputation(90), squad))
        assertEquals(Money.ZERO, initialCash(Reputation(50), emptyList()))
    }

    @Test
    fun `ingresso parte do preco justo e ganha premio pela reputacao`() {
        val league = LeagueStrength(70)
        val average = initialTicketPrice(league, Reputation.AVERAGE)
        // Reputação mediana: o preço justo da força esperada (liga 70 → US$ 9), sem prêmio.
        assertEquals(fairTicketPriceCents(70.0), average)
        assertTrue(initialTicketPrice(league, Reputation(95)) > average)
        assertTrue(initialTicketPrice(league, Reputation(10)) < average)
        // Liga mais forte, mesma reputação: torcida paga mais.
        assertTrue(initialTicketPrice(LeagueStrength(80), Reputation.AVERAGE) > average)
    }

    @Test
    fun `ingresso fica sempre na faixa permitida e em multiplos de 50 centavos`() {
        for (strength in listOf(1, 50, 99)) {
            for (rep in listOf(1, 50, 100)) {
                val price = initialTicketPrice(LeagueStrength(strength), Reputation(rep))
                assertTrue(price in TICKET_PRICE_RANGE, "liga $strength, rep $rep: $price")
                assertEquals(0L, price.cents % 50)
            }
        }
    }
}
