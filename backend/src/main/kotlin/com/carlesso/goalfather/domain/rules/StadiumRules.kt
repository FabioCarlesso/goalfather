package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.Money
import com.carlesso.goalfather.domain.model.cents

/**
 * Regras de ampliação do estádio (issue #79). Funções PURAS: a conta do custo
 * saiu do controller e mora aqui, onde dá para testar sem Spring.
 */

/** Custo de cada assento novo: US$ 20 (`val`: [Money] não cabe em `const`). */
val STADIUM_SEAT_COST_CENTS: Money = 20_00.cents

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
 * Custo total da obra. O `toLong()` vem ANTES da multiplicação: o `times(Long)`
 * de [Money] multiplica em `Long`, e deixar explícito documenta que
 * `Int.MAX_VALUE` assentos × US$ 20 não cabe em `Int`.
 */
fun stadiumExpansionCost(additionalSeats: Int): Money =
    STADIUM_SEAT_COST_CENTS * additionalSeats.toLong()

/**
 * Clube depois da obra: caixa debitado e capacidade somada. Extension function
 * em vez de método em `Club` — a regra fica junto das constantes que a
 * definem, e o modelo não precisa conhecer o preço do assento.
 */
fun Club.withExpandedStadium(additionalSeats: Int): Club = copy(
    cash = cash - stadiumExpansionCost(additionalSeats),
    stadiumCapacity = stadiumCapacity + additionalSeats,
)
