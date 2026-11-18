package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Employment
import com.carlesso.goalfather.domain.model.cents
import com.carlesso.goalfather.domain.model.world.LeagueStrength
import com.carlesso.goalfather.test.makePlayer
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Preços do mercado derivados do valor (issue #135) — sem Spring. */
class TransferRulesTest {

    private val today = LocalDate.of(2026, 3, 1)

    @Test
    fun `venda paga 60 por cento do valor e o anuncio cobra 125 por cento`() {
        assertEquals(600_000_00.cents, salePayout(1_000_000_00.cents))
        assertEquals(1_250_000_00.cents, marketListingPrice(1_000_000_00.cents))
    }

    @Test
    fun `comprar no mercado e revender da prejuizo em qualquer liga, idade, overall e contrato`() {
        // A arbitragem que as regras antigas tiveram de fechar (revisão do PR #92):
        // quem compra pelo anúncio (valor de REFERÊNCIA) e revende ao mercado
        // (valor no clube, com o prêmio da liga) nunca lucra — nem do clube da
        // liga mais forte possível, com o contrato mais longo possível.
        val strengths = listOf(1, 40, 62, 70, 78, 90, 99).map(::LeagueStrength)
        val contracts = listOf(0L, 1, 3, 5)
        for (overall in listOf(30, 50, 65, 75, 85, 94, 99)) {
            for (age in listOf(15, 19, 25, 30, 36, 50)) {
                val player = makePlayer(1, overall = overall, age = age)
                val bought = marketListingPrice(marketReferenceValue(player))
                for (strength in strengths) {
                    for (years in contracts) {
                        val contract = Employment.UnderContract(ClubId(1), player.salary, today.minusDays(1), today.plusYears(years))
                        val resold = salePayout(marketValue(player, contract, strength, today))
                        assertTrue(resold < bought, "ovr=$overall idade=$age liga=${strength.value} anos=$years: $resold >= $bought")
                    }
                }
            }
        }
    }

    @Test
    fun `preco nao depende do salario - o valor e que manda`() {
        // Regressão da regra antiga (`salary × 8`/`× 12`): mesmo jogador com
        // salários diferentes tem o mesmo preço; overall diferente, não.
        val cheap = makePlayer(1, overall = 80, salary = 1_000_00)
        val pricey = makePlayer(1, overall = 80, salary = 90_000_00)
        assertEquals(marketReferenceValue(cheap), marketReferenceValue(pricey))
        assertTrue(marketReferenceValue(makePlayer(1, overall = 85)) > marketReferenceValue(makePlayer(1, overall = 80)))
    }
}
