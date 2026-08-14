package com.carlesso.goalfather.domain.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FinanceRulesTest {

    /** Forças de mandante que cobrem a faixa inteira, incluindo as bordas. */
    private val strengths = listOf(50.0, 60.0, 70.0, 80.0, 90.0, 100.0, 110.0)

    /** Varredura da faixa de preço de R$ 1 em R$ 1. */
    private val prices = (MIN_TICKET_PRICE_CENTS..MAX_TICKET_PRICE_CENTS step 100).toList()

    @Test
    fun `taxa de ocupacao fica entre 50% e 100% no preco justo`() {
        // No preço justo o multiplicador vale 1, então sobra a curva da força
        // (a mesma da issue #4).
        fun rateAtFairPrice(strength: Double) =
            attendanceRate(strength, fairTicketPriceCents(strength))

        assertEquals(0.5, rateAtFairPrice(0.0))   // piso
        assertEquals(0.5, rateAtFairPrice(60.0))  // base
        assertEquals(1.0, rateAtFairPrice(100.0)) // teto
        assertEquals(1.0, rateAtFairPrice(120.0)) // clamp no teto
        val mid = rateAtFairPrice(80.0)
        assertTrue(mid in 0.5..1.0, "Taxa intermediária fora da faixa: $mid")
    }

    @Test
    fun `bilheteria cresce com a forca do mandante no mesmo preco`() {
        val fraco = ticketRevenue(10_000, homeStrength = 60.0, ticketPriceCents = DEFAULT_TICKET_PRICE_CENTS)
        val forte = ticketRevenue(10_000, homeStrength = 90.0, ticketPriceCents = DEFAULT_TICKET_PRICE_CENTS)
        assertTrue(forte > fraco, "Time mais forte deveria render mais bilheteria ($forte vs $fraco)")
    }

    @Test
    fun `bilheteria tem a MAGNITUDE esperada, nao so a forma`() {
        // Âncora absoluta. Todo o resto desta classe testa FORMA (monotonia,
        // máximo interior, continuidade, proporcionalidade) — propriedades que
        // sobrevivem intactas a um erro de escala: dividir a economia por dez
        // passaria por todos eles. Aqui a conta é conferida no detalhe:
        // força 60 ⇒ ocupação-base 0.5 e preço justo R$ 40; a R$ 50 o
        // multiplicador é 1/(1 + 0.5·0.25²) ≈ 0.9697, logo 48.48% de 10.000 =
        // 4.848 pagantes × R$ 50 = R$ 242.400.
        assertEquals(0.4848, attendanceRate(60.0, DEFAULT_TICKET_PRICE_CENTS), absoluteTolerance = 1e-4)
        assertEquals(4_848, attendance(10_000, 60.0, DEFAULT_TICKET_PRICE_CENTS))
        assertEquals(242_400_00L, ticketRevenue(10_000, 60.0, DEFAULT_TICKET_PRICE_CENTS))
        // E no preço justo, onde o multiplicador vale exatamente 1: metade do
        // estádio a R$ 40.
        assertEquals(200_000_00L, ticketRevenue(10_000, 60.0, fairTicketPriceCents(60.0)))
    }

    @Test
    fun `gate devolve publico e receita da mesma conta`() {
        // `gate` é o que a rodada usa; `ticketRevenue` é o atalho. Se os dois
        // divergirem, o extrato da rodada deixa de fechar (público × preço ≠
        // receita) sem nenhum outro teste reclamar.
        for (strength in strengths) {
            for (price in prices) {
                val g = gate(20_000, strength, price)
                assertEquals(attendance(20_000, strength, price), g.attendance)
                assertEquals(ticketRevenue(20_000, strength, price), g.revenue)
                assertEquals(g.attendance * price, g.revenue)
            }
        }
    }

    @Test
    fun `folha salarial cobrada a cada 2 rodadas`() {
        assertFalse(isSalaryRound(1))
        assertTrue(isSalaryRound(2))
        assertFalse(isSalaryRound(3))
        assertTrue(isSalaryRound(4))
    }

    // ─── Curva de demanda (issue #59) ─────────────────────────────────────

    @Test
    fun `preco maior nunca aumenta a ocupacao`() {
        // A garantia central da curva: monotonicidade. Vale para toda força,
        // inclusive nas bordas onde a ocupação satura em 100%.
        for (strength in strengths) {
            var previous = attendanceRate(strength, prices.first())
            for (price in prices.drop(1)) {
                val rate = attendanceRate(strength, price)
                assertTrue(
                    rate <= previous,
                    "Ocupação subiu ao encarecer (força $strength, preço $price): $previous → $rate",
                )
                previous = rate
            }
        }
    }

    @Test
    fun `ocupacao fica sempre entre zero e o estadio cheio`() {
        for (strength in strengths) {
            for (price in prices) {
                val rate = attendanceRate(strength, price)
                assertTrue(rate > 0.0, "Ocupação zerada (força $strength, preço $price)")
                assertTrue(rate <= 1.0, "Estádio esticou (força $strength, preço $price): $rate")
            }
        }
    }

    @Test
    fun `receita tem maximo em preco intermediario`() {
        // O trade-off da issue: barato demais rende pouco por torcedor, caro
        // demais esvazia o estádio. Se o ótimo fosse uma das pontas da faixa,
        // não haveria decisão a tomar.
        for (strength in strengths) {
            val best = prices.maxBy { ticketRevenue(20_000, strength, it) }
            assertTrue(
                best > MIN_TICKET_PRICE_CENTS && best < MAX_TICKET_PRICE_CENTS,
                "Receita máxima na borda da faixa (força $strength): $best",
            )
            assertTrue(
                ticketRevenue(20_000, strength, best) > ticketRevenue(20_000, strength, MAX_TICKET_PRICE_CENTS),
                "Cobrar o teto deveria render menos que o preço ótimo (força $strength)",
            )
        }
    }

    @Test
    fun `time mais forte suporta ingresso mais caro`() {
        // O preço justo sobe com a força, então o ótimo sobe junto — é o que
        // liga evoluir o elenco a poder cobrar mais.
        val otimoFraco = prices.maxBy { ticketRevenue(20_000, 60.0, it) }
        val otimoForte = prices.maxBy { ticketRevenue(20_000, 100.0, it) }
        assertTrue(
            otimoForte > otimoFraco,
            "Time forte deveria suportar ingresso mais caro ($otimoForte vs $otimoFraco)",
        )
    }

    @Test
    fun `multiplicador de preco vale 1 no preco justo e e continuo`() {
        for (strength in strengths) {
            val fair = fairTicketPriceCents(strength)
            assertEquals(1.0, ticketPriceDemandFactor(fair, strength), absoluteTolerance = 1e-9)
            // Um centavo para cada lado do preço justo: os dois ramos da curva
            // se encontram sem degrau.
            assertTrue(ticketPriceDemandFactor(fair - 1, strength) > 1.0)
            assertTrue(ticketPriceDemandFactor(fair + 1, strength) < 1.0)
        }
    }

    @Test
    fun `preco justo fica dentro da faixa praticavel e cresce com a forca`() {
        val fraco = fairTicketPriceCents(50.0)
        val forte = fairTicketPriceCents(110.0)
        assertTrue(fraco < forte, "Preço justo deveria crescer com a força")
        for (strength in strengths) {
            assertTrue(
                fairTicketPriceCents(strength) in TICKET_PRICE_RANGE,
                "Preço justo fora da faixa praticável (força $strength)",
            )
        }
    }

    @Test
    fun `faixa de preco aceita as bordas e recusa o que passa delas`() {
        assertTrue(isTicketPriceAllowed(MIN_TICKET_PRICE_CENTS))
        assertTrue(isTicketPriceAllowed(MAX_TICKET_PRICE_CENTS))
        assertTrue(isTicketPriceAllowed(DEFAULT_TICKET_PRICE_CENTS))
        assertFalse(isTicketPriceAllowed(MIN_TICKET_PRICE_CENTS - 1))
        assertFalse(isTicketPriceAllowed(MAX_TICKET_PRICE_CENTS + 1))
        assertFalse(isTicketPriceAllowed(0))
        assertFalse(isTicketPriceAllowed(-1))
    }

    @Test
    fun `estadio maior rende mais no mesmo preco`() {
        val pequeno = ticketRevenue(10_000, 75.0, DEFAULT_TICKET_PRICE_CENTS)
        val grande = ticketRevenue(30_000, 75.0, DEFAULT_TICKET_PRICE_CENTS)
        assertTrue(grande > pequeno, "Ampliar o estádio deveria render mais ($grande vs $pequeno)")
        // Público é proporcional à capacidade — a taxa de ocupação não depende
        // do tamanho do estádio (a folga de 3 absorve o truncamento do público).
        val esperado = 3 * attendance(10_000, 75.0, DEFAULT_TICKET_PRICE_CENTS)
        val obtido = attendance(30_000, 75.0, DEFAULT_TICKET_PRICE_CENTS)
        assertTrue(obtido - esperado in 0..3, "Público não escalou com a capacidade: $obtido vs ~$esperado")
    }
}
