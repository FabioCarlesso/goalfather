package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.test.makePlayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Preços do mercado — sem Spring. */
class TransferRulesTest {

    @Test
    fun `venda paga 8 salarios e o anuncio cobra 12, como no prototipo`() {
        val player = makePlayer(1, salary = 23_000_00)

        assertEquals(184_000_00, salePayout(player))
        assertEquals(276_000_00, marketListingPrice(player))
    }

    @Test
    fun `comprar e revender o mesmo jogador sempre da prejuizo`() {
        // A arbitragem que a regra antiga permitia: o pagamento ao vendedor não
        // pode alcançar o preço que o próximo comprador paga.
        for (salary in listOf(1, 10_000_00, 55_000_00, Int.MAX_VALUE)) {
            val player = makePlayer(1, salary = salary)
            assertTrue(salePayout(player) < marketListingPrice(player), "salary=$salary")
        }
    }

    @Test
    fun `preco nao depende do overall - o jogador craque nao vale ouro so por isso`() {
        // Regressão: a venda era `overall × R$ 100 mil`, sem relação com o preço
        // de compra. Mesmo salário → mesmo preço, qualquer que seja o overall.
        assertEquals(salePayout(makePlayer(1, overall = 60)), salePayout(makePlayer(1, overall = 95)))
    }
}
