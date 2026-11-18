package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Money

/**
 * Preços do mercado de transferências. Funções PURAS, sem Spring.
 *
 * Antes da issue #135 as duas pontas saíam do salário (venda `salary × 8`,
 * anúncio `salary × 12`, como no protótipo). Agora saem do VALOR DE MERCADO
 * (`ValuationRules`), que já pesa overall, idade, contrato e liga:
 *
 * - a venda rápida paga [SALE_PAYOUT_PERCENT] do valor do jogador NO CLUBE que
 *   vende (com o prêmio da liga dele);
 * - o anúncio cobra [MARKET_LISTING_PERCENT] do valor de REFERÊNCIA do mercado
 *   (`marketReferenceValue`: contrato pleno, liga de referência).
 *
 * O "spread" anti-arbitragem continua estrutural: o maior valor possível no
 * clube é o de referência × o teto do multiplicador de liga (1,8), e
 * 60% × 1,8 = 108% < 125%. Comprar e revender o mesmo jogador, de qualquer
 * liga, SEMPRE dá prejuízo — a diferença é a comissão do mercado.
 */

/** Quanto o clube recebe ao vender ao mercado: 60% do valor — é venda rápida, sem negociação. */
const val SALE_PAYOUT_PERCENT: Long = 60

/** Por quanto o jogador é anunciado: o valor de referência + 25% de comissão do mercado. */
const val MARKET_LISTING_PERCENT: Long = 125

/** Valor pago ao clube que vende um jogador de valor [squadValue] (o do elenco dele). */
fun salePayout(squadValue: Money): Money = Money(squadValue.cents * SALE_PAYOUT_PERCENT / 100)

/** Preço de anúncio de um jogador de valor de referência [referenceValue] no mercado. */
fun marketListingPrice(referenceValue: Money): Money = Money(referenceValue.cents * MARKET_LISTING_PERCENT / 100)
