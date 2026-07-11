package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.Standings

/**
 * Regras de promoção/rebaixamento entre divisões (issue #47).
 *
 * Funções PURAS em `domain/rules` — a regra de ouro do CLAUDE.md: nada de
 * Spring/JPA aqui, e a virada de temporada é testável com tabelas finais
 * conhecidas, sem subir contexto.
 */

/** Nº padrão de vagas trocadas entre divisões adjacentes na virada. */
const val PROMOTION_RELEGATION_SPOTS = 2

/**
 * Quantas posições do TOPO desta divisão sobem na virada. A elite não tem
 * para onde subir → 0.
 */
fun promotionSpotsFor(division: Division, spots: Int = PROMOTION_RELEGATION_SPOTS): Int =
    if (division == Division.FIRST) 0 else spots

/**
 * Quantas posições do FUNDO desta divisão descem na virada. A última
 * divisão não tem para onde descer → 0.
 */
fun relegationSpotsFor(division: Division, divisionCount: Int, spots: Int = PROMOTION_RELEGATION_SPOTS): Int =
    if (division.value >= divisionCount) 0 else spots

/**
 * Aplica a virada de temporada às tabelas finais: em cada par de divisões
 * adjacentes, os últimos N da divisão de cima trocam de lugar com os
 * primeiros N da de baixo. Devolve a divisão da PRÓXIMA temporada de cada
 * clube (inclusive os que não se movem — o mapa é total sobre as tabelas).
 *
 * Determinística por construção: as tabelas já chegam ordenadas por
 * `position` (tiebreak resolvido em [applyRoundToStandings]), então a mesma
 * tabela final produz sempre a mesma recomposição — critério de aceite da
 * issue #47.
 *
 * `zipWithNext` percorre os pares adjacentes (1ª↔2ª, 2ª↔3ª, ...) — idioma
 * Kotlin para "janela deslizante de 2" sem indexação manual. Os tiers devem
 * ser CONTÍGUOS: um buraco na pirâmide (ex.: divisões 1 e 3) é um erro de
 * seed, não algo para a regra "pontear" silenciosamente. O N efetivo de
 * cada par é limitado pelo tamanho das divisões, o que mantém as divisões
 * com o MESMO número de clubes após a troca (sobem tantos quantos descem).
 */
fun applyPromotionRelegation(
    tables: List<Standings>,
    spots: Int = PROMOTION_RELEGATION_SPOTS,
): Map<ClubId, Division> {
    require(spots >= 0) { "spots não pode ser negativo: $spots" }
    val ordered = tables.sortedBy { it.division }
    require(ordered.map { it.division }.toSet().size == ordered.size) {
        "Tabelas com divisão duplicada: ${ordered.map { it.division.value }}"
    }

    val next = ordered
        .flatMap { table -> table.rows.map { it.clubId to table.division } }
        .toMap(mutableMapOf())

    for ((upper, lower) in ordered.zipWithNext()) {
        require(lower.division.value == upper.division.value + 1) {
            "Divisões não contíguas na pirâmide: ${upper.division.value} → ${lower.division.value}"
        }
        val exchanged = minOf(spots, upper.rows.size, lower.rows.size)
        val byPosition = { table: Standings -> table.rows.sortedBy { it.position } }
        byPosition(upper).takeLast(exchanged).forEach { next[it.clubId] = lower.division }
        byPosition(lower).take(exchanged).forEach { next[it.clubId] = upper.division }
    }
    return next
}
