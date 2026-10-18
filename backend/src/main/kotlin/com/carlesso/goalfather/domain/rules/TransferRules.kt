package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Player

/**
 * Preços do mercado de transferências. Funções PURAS, sem Spring.
 *
 * Antes, a venda pagava `overall × R$ 100 mil` enquanto o mercado anunciava
 * por um preço sem relação nenhuma com isso (seed: ~9–14× o salário). Comprar
 * um jogador de 77 por R$ 220 mil e vendê-lo na hora rendia R$ 7,7 milhões —
 * arbitragem infinita em dois cliques.
 *
 * As duas pontas agora saem do MESMO número, o salário, como no protótipo
 * (`prototype/goalfather-web.jsx`: compra `salary × 12`, venda `salary × 8`),
 * sem o ruído aleatório de lá: preço é regra, não sorteio. Como o pagamento ao
 * vendedor é sempre menor que o anúncio, comprar e revender o mesmo jogador
 * SEMPRE dá prejuízo — o "spread" é a comissão do mercado.
 */

/** Quanto o clube recebe ao vender: 8 salários. */
const val SALE_PAYOUT_SALARY_MULTIPLIER: Long = 8

/** Por quanto o jogador vendido é anunciado no mercado: 12 salários. */
const val MARKET_LISTING_SALARY_MULTIPLIER: Long = 12

/** Valor pago ao clube que vende [player], em centavos. */
fun salePayout(player: Player): Long = player.salary.toLong() * SALE_PAYOUT_SALARY_MULTIPLIER

/** Preço de anúncio de [player] no mercado, em centavos. */
fun marketListingPrice(player: Player): Long = player.salary.toLong() * MARKET_LISTING_SALARY_MULTIPLIER
