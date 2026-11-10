package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Discipline
import com.carlesso.goalfather.domain.model.PlayedMatch
import com.carlesso.goalfather.domain.model.StandingRow
import com.carlesso.goalfather.domain.model.Tiebreaker
import kotlin.random.Random

/**
 * Ordenação de uma tabela pelos critérios de desempate da competição (issue
 * #124). Função PURA: o sorteio ([Tiebreaker.DRAWING_OF_LOTS]) usa o `Random`
 * recebido.
 *
 * Os critérios se dividem em dois tipos:
 *
 * - **Absolutos** (pontos, vitórias, saldo, gols, cartões): só dependem da
 *   linha do clube. Uma sequência deles vira UM `Comparator` composto com
 *   `thenComparing` — o idioma Kotlin/Java para "ordena por A, depois B".
 * - **Relativos ao grupo** (confronto direto, sorteio): dependem de QUEM está
 *   empatado. O confronto direto entre três clubes é um mini-campeonato só
 *   entre eles, e não faz sentido como comparação de dois a dois.
 *
 * Por isso a ordenação é um refinamento recursivo: ordena pelo bloco de
 * critérios absolutos, separa os grupos ainda empatados e aplica o próximo
 * critério só dentro de cada grupo. Ao esgotar os critérios, nome e id dão a
 * ordem final — a tabela é sempre determinística, como a liga do jogo sempre foi.
 *
 * @param results jogos já disputados na competição, para o confronto direto.
 * @param discipline cartões por clube, para os critérios FEWER_*_CARDS (ausente = zero).
 * @param lots sorteio; sem ele, o sorteio cai no desempate final por nome.
 */
fun rankTable(
    rows: List<StandingRow>,
    tiebreakers: List<Tiebreaker>,
    results: List<PlayedMatch> = emptyList(),
    discipline: Map<ClubId, Discipline> = emptyMap(),
    lots: Random? = null,
): List<StandingRow> =
    refine(rows, tiebreakers, results, discipline, lots)
        .mapIndexed { i, row -> row.copy(position = i + 1) }

private fun refine(
    rows: List<StandingRow>,
    criteria: List<Tiebreaker>,
    results: List<PlayedMatch>,
    discipline: Map<ClubId, Discipline>,
    lots: Random?,
): List<StandingRow> {
    if (rows.size <= 1) return rows
    val criterion = criteria.firstOrNull() ?: return rows.sortedWith(FINAL_ORDER)

    // Bloco de critérios absolutos consecutivos → um Comparator só.
    val absolute = criteria.takeWhile { it.absoluteComparator(discipline) != null }
    if (absolute.isNotEmpty()) {
        val comparator = absolute
            .mapNotNull { it.absoluteComparator(discipline) }
            .reduce { acc, next -> acc.thenComparing(next) }
        return splitTies(rows.sortedWith(comparator), comparator)
            .flatMap { refine(it, criteria.drop(absolute.size), results, discipline, lots) }
    }

    val rest = criteria.drop(1)
    return when (criterion) {
        Tiebreaker.HEAD_TO_HEAD, Tiebreaker.HEAD_TO_HEAD_GOAL_DIFFERENCE -> {
            val mini = miniTable(rows.map { it.clubId }.toSet(), results)
            val key: (StandingRow) -> Int = { row ->
                val stats = mini.getValue(row.clubId)
                if (criterion == Tiebreaker.HEAD_TO_HEAD) stats.points else stats.goalDifference
            }
            val comparator = compareByDescending(key)
            splitTies(rows.sortedWith(comparator), comparator).flatMap { refine(it, rest, results, discipline, lots) }
        }
        // Sorteio decide tudo que ainda está empatado; sem `Random`, segue para o desempate final.
        Tiebreaker.DRAWING_OF_LOTS ->
            if (lots == null) refine(rows, rest, results, discipline, null)
            else rows.sortedWith(FINAL_ORDER).shuffled(lots)
        // Os absolutos já foram tratados pelo bloco acima; o `when` precisa
        // listá-los para seguir exaustivo sem `else`.
        Tiebreaker.POINTS, Tiebreaker.WINS, Tiebreaker.GOAL_DIFFERENCE, Tiebreaker.GOALS_FOR,
        Tiebreaker.FEWER_RED_CARDS, Tiebreaker.FEWER_YELLOW_CARDS ->
            error("Critério absoluto $criterion fora do bloco de comparadores")
    }
}

/** Comparador do critério se ele depender só da linha do clube; `null` se depender do grupo empatado. */
private fun Tiebreaker.absoluteComparator(discipline: Map<ClubId, Discipline>): Comparator<StandingRow>? = when (this) {
    Tiebreaker.POINTS -> compareByDescending { it.points }
    Tiebreaker.WINS -> compareByDescending { it.wins }
    Tiebreaker.GOAL_DIFFERENCE -> compareByDescending { it.goalDifference }
    Tiebreaker.GOALS_FOR -> compareByDescending { it.goalsFor }
    Tiebreaker.FEWER_RED_CARDS -> compareBy { discipline[it.clubId]?.redCards ?: 0 }
    Tiebreaker.FEWER_YELLOW_CARDS -> compareBy { discipline[it.clubId]?.yellowCards ?: 0 }
    Tiebreaker.HEAD_TO_HEAD, Tiebreaker.HEAD_TO_HEAD_GOAL_DIFFERENCE, Tiebreaker.DRAWING_OF_LOTS -> null
}

/** Desempate final quando os critérios acabam: nome, depois id. */
private val FINAL_ORDER: Comparator<StandingRow> = compareBy<StandingRow> { it.clubName }.thenBy { it.clubId.value }

/** Corta uma lista JÁ ORDENADA em blocos de linhas iguais segundo [comparator]. */
private fun splitTies(sorted: List<StandingRow>, comparator: Comparator<StandingRow>): List<List<StandingRow>> =
    sorted.fold(mutableListOf<MutableList<StandingRow>>()) { groups, row ->
        val last = groups.lastOrNull()
        if (last != null && comparator.compare(last.first(), row) == 0) last += row else groups += mutableListOf(row)
        groups
    }

private data class MiniStats(val points: Int = 0, val goalDifference: Int = 0)

/** Mini-campeonato: só os jogos em que os DOIS clubes estão em [clubs]. */
private fun miniTable(clubs: Set<ClubId>, results: List<PlayedMatch>): Map<ClubId, MiniStats> {
    val stats = clubs.associateWith { MiniStats() }.toMutableMap()
    results.filter { it.home in clubs && it.away in clubs }.forEach { match ->
        val diff = match.homeGoals - match.awayGoals
        stats.computeIfPresent(match.home) { _, s -> s.copy(points = s.points + pointsFor(diff), goalDifference = s.goalDifference + diff) }
        stats.computeIfPresent(match.away) { _, s -> s.copy(points = s.points + pointsFor(-diff), goalDifference = s.goalDifference - diff) }
    }
    return stats
}

private fun pointsFor(goalDifference: Int): Int = when {
    goalDifference > 0 -> 3
    goalDifference == 0 -> 1
    else -> 0
}
