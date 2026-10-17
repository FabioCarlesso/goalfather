package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Club

/**
 * Regras de ampliação do estádio (issue #79). Funções PURAS: a conta do custo
 * saiu do controller e mora aqui, onde dá para testar sem Spring.
 */

/** Custo de cada assento novo: R$ 100. */
const val STADIUM_SEAT_COST_CENTS: Long = 100_00

/**
 * Menor obra aceita: 1.000 lugares. Também é o que barra zero e negativos —
 * sem esse piso, "ampliar" −10.000 lugares virava venda de assentos que o
 * clube nunca comprou (caixa grátis) ou capacidade negativa (500).
 */
const val MIN_STADIUM_EXPANSION_SEATS: Int = 1_000

/** A quantidade pedida é uma obra válida? */
fun isStadiumExpansionAllowed(additionalSeats: Int): Boolean =
    additionalSeats >= MIN_STADIUM_EXPANSION_SEATS

/**
 * Custo total da obra, em centavos. O `toLong()` vem ANTES da multiplicação:
 * `Int * Long` já promoveria para `Long`, mas deixar explícito documenta que
 * `Int.MAX_VALUE` assentos × R$ 100 não cabe em `Int`.
 */
fun stadiumExpansionCost(additionalSeats: Int): Long =
    additionalSeats.toLong() * STADIUM_SEAT_COST_CENTS

/**
 * Clube depois da obra: caixa debitado e capacidade somada. Extension function
 * em vez de método em `Club` — a regra fica junto das constantes que a
 * definem, e o modelo não precisa conhecer o preço do assento.
 */
fun Club.withExpandedStadium(additionalSeats: Int): Club = copy(
    cash = cash - stadiumExpansionCost(additionalSeats),
    stadiumCapacity = stadiumCapacity + additionalSeats,
)
