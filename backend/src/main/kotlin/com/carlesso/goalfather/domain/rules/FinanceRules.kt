package com.carlesso.goalfather.domain.rules

/**
 * Regras financeiras de rodada (issues #4 e #59). Funções PURAS — sem estado,
 * determinísticas. A receita de bilheteria depende da capacidade do estádio,
 * da força do mandante e do PREÇO que o técnico cobra; a folha salarial é a
 * soma dos salários do elenco, cobrada a cada `SALARY_EVERY_N_ROUNDS` rodadas.
 */

/** Preço de quem nunca mexeu no ingresso — e de todo clube da IA (R$ 50). */
const val DEFAULT_TICKET_PRICE_CENTS: Long = 50_00

/** Piso da faixa que o técnico pode praticar (R$ 10). */
const val MIN_TICKET_PRICE_CENTS: Long = 10_00

/** Teto da faixa que o técnico pode praticar (R$ 200). */
const val MAX_TICKET_PRICE_CENTS: Long = 200_00

/**
 * Faixa de preço aceita pelo comando de ingresso. `LongRange` em vez de dois
 * `Long` soltos: o `in` do Kotlin já expressa a checagem, e a faixa viaja
 * inteira para a mensagem de erro.
 */
val TICKET_PRICE_RANGE: LongRange = MIN_TICKET_PRICE_CENTS..MAX_TICKET_PRICE_CENTS

/** Cadência da folha salarial: a cada 2 rodadas (~"um mês" de temporada curta). */
const val SALARY_EVERY_N_ROUNDS: Int = 2

// ─── Curva de demanda (issue #59) ─────────────────────────────────────────

/** Força de mandante em que a ocupação (e o preço justo) começam: time fraco. */
private const val STRENGTH_FLOOR = 60.0

/** Força de mandante em que a ocupação (e o preço justo) saturam: time forte. */
private const val STRENGTH_CEILING = 100.0

/** Preço justo do time mais fraco (R$ 40) e do mais forte (R$ 60). */
private const val FAIR_PRICE_AT_FLOOR: Long = 40_00
private const val FAIR_PRICE_AT_CEILING: Long = 60_00

/**
 * Sensibilidade da torcida ao preço acima do justo. É o `k` de
 * `1 / (1 + k·(r−1)²)` — quanto maior, mais rápido o estádio esvazia. Vale
 * 0.5, o que coloca o pico de receita em `r = √(1 + 1/k) ≈ 1.73` (ver
 * [ticketRevenue]).
 */
private const val PRICE_SENSITIVITY = 0.5

/**
 * Quanto a torcida responde a ingresso ABAIXO do justo: de graça (`r = 0`) a
 * demanda é 1.5× a do preço justo. Como a ocupação nunca passa de 100%, quem
 * já enche o estádio no preço justo não ganha NADA baixando — é isso que faz
 * a receita voltar a subir depois de um certo desconto.
 */
private const val CHEAP_TICKET_BOOST = 0.5

/** Posição da força na faixa `[STRENGTH_FLOOR, STRENGTH_CEILING]`, em 0..1. */
private fun strengthRatio(homeStrength: Double): Double =
    ((homeStrength - STRENGTH_FLOOR) / (STRENGTH_CEILING - STRENGTH_FLOOR)).coerceIn(0.0, 1.0)

/**
 * Ocupação que o estádio teria no preço justo: entre 50% (time fraco) e 100%
 * (time forte). É a curva original da issue #4 — o preço entra depois, como
 * um multiplicador ([ticketPriceDemandFactor]).
 */
fun baseAttendanceRate(homeStrength: Double): Double = 0.5 + 0.5 * strengthRatio(homeStrength)

/**
 * Preço que a torcida deste time considera JUSTO, em centavos: R$ 40 para o
 * mais fraco, R$ 60 para o mais forte.
 *
 * O preço justo sobe com a força de propósito: sem isso o ingresso ótimo
 * seria o mesmo para todo mundo e a decisão viraria um número decorado. Com
 * ele, ganhar jogos e evoluir o elenco AUMENTA o que a torcida aceita pagar —
 * que é o loop econômico que a issue #59 quer abrir.
 */
fun fairTicketPriceCents(homeStrength: Double): Long =
    FAIR_PRICE_AT_FLOOR +
        ((FAIR_PRICE_AT_CEILING - FAIR_PRICE_AT_FLOOR) * strengthRatio(homeStrength)).toLong()

/**
 * Multiplicador de demanda do preço `r = preço / preço justo`. Vale 1.0 no
 * preço justo, cresce até 1.5 quando o ingresso tende a zero e decai — nunca
 * até zero — quando o técnico cobra caro:
 *
 * ```
 * r ≤ 1 → 1 + CHEAP_TICKET_BOOST · (1 − r)      (linear: baratear atrai)
 * r > 1 → 1 / (1 + PRICE_SENSITIVITY · (r−1)²)  (decai e nunca zera)
 * ```
 *
 * A cauda hiperbólica não é enfeite: com um decaimento linear a demanda
 * chegaria a zero dentro da faixa de preço, e do ponto de zeragem em diante
 * uma faixa inteira renderia exatamente R$ 0 — receita plana onde deveria
 * haver escolha. Aqui sempre sobra a torcida fiel, e a receita cai suave.
 *
 * Contínua em `r = 1` (os dois ramos valem 1.0) e monotônica não-crescente em
 * toda a faixa — a primeira garantia que `FinanceRulesTest` cobre.
 */
fun ticketPriceDemandFactor(ticketPriceCents: Long, homeStrength: Double): Double {
    val ratio = ticketPriceCents.toDouble() / fairTicketPriceCents(homeStrength)
    return if (ratio <= 1.0) {
        1.0 + CHEAP_TICKET_BOOST * (1.0 - ratio)
    } else {
        val excess = ratio - 1.0
        1.0 / (1.0 + PRICE_SENSITIVITY * excess * excess)
    }
}

/**
 * Taxa de ocupação do estádio: a ocupação-base da força do mandante corrigida
 * pelo preço, com teto em 100% — o estádio não estica.
 */
fun attendanceRate(homeStrength: Double, ticketPriceCents: Long): Double =
    (baseAttendanceRate(homeStrength) * ticketPriceDemandFactor(ticketPriceCents, homeStrength))
        .coerceAtMost(1.0)

/** Público pagante = capacidade × taxa de ocupação. */
fun attendance(stadiumCapacity: Int, homeStrength: Double, ticketPriceCents: Long): Int =
    (stadiumCapacity * attendanceRate(homeStrength, ticketPriceCents)).toInt()

/**
 * Receita de bilheteria do mandante, em centavos: público × preço.
 *
 * O trade-off que a issue #59 abre vive aqui. Ingresso barato enche o estádio
 * mas rende pouco por torcedor; ingresso caro esvazia. Como a ocupação satura
 * em 100%, a receita cresce com o preço enquanto há gente sobrando e passa a
 * cair quando o esvaziamento supera o ganho por ingresso — o máximo fica em
 * `r = √(1 + 1/PRICE_SENSITIVITY) ≈ 1.73` vezes o preço justo, ou seja ~R$ 69
 * para o time mais fraco e ~R$ 104 para o mais forte.
 */
fun ticketRevenue(stadiumCapacity: Int, homeStrength: Double, ticketPriceCents: Long): Long =
    attendance(stadiumCapacity, homeStrength, ticketPriceCents) * ticketPriceCents

/** O preço está na faixa que o técnico pode praticar? */
fun isTicketPriceAllowed(ticketPriceCents: Long): Boolean = ticketPriceCents in TICKET_PRICE_RANGE

/** Há cobrança de folha salarial nesta rodada? */
fun isSalaryRound(roundNumber: Int): Boolean =
    roundNumber % SALARY_EVERY_N_ROUNDS == 0
