package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundMatch
import com.carlesso.goalfather.domain.model.RoundStatus

/**
 * Gera os pareamentos de uma rodada via algoritmo de Berger (round-robin
 * de turno único), UMA vez por divisão (issue #47). Função PURA — mesmo
 * input produz exatamente o mesmo schedule (critério de determinismo da
 * issue #1).
 *
 * Ideia do Berger: o primeiro clube da divisão (o de menor `id`) fica FIXO
 * e os demais rotacionam a cada rodada. Para `N` clubes (par) o turno tem
 * `N-1` rodadas distintas, cada uma com `N/2` partidas, e nenhum par se
 * repete dentro do turno.
 *
 * Com divisões de tamanhos diferentes, uma divisão menor "folga" nas
 * rodadas além do seu turno — a rodada global segue existindo enquanto a
 * MAIOR divisão tiver jogos (ver [seasonRounds]).
 *
 * Espelha `generateRound` em `frontend/src/mocks/seed.ts`.
 *
 * Idioma Kotlin: função top-level pura em `domain/rules` (sem classe
 * utilitária estática à la Java) e `data class` imutáveis montadas via
 * `map`/`flatMap` — nenhuma mutação de estado.
 */
fun generateRound(roundNumber: Int, season: Int, clubs: List<Club>): Round {
    require(roundNumber >= 1) { "roundNumber deve ser >= 1: $roundNumber" }

    // `toSortedMap` garante ordem estável (divisão 1, 2, ...) para o
    // determinismo do schedule e da numeração das partidas.
    val byDivision = clubs.groupBy { it.division }.toSortedMap()
    require(byDivision.isNotEmpty()) { "Liga precisa de ao menos 1 divisão" }

    val matches = byDivision.flatMap { (division, divisionClubs) ->
        require(divisionClubs.size >= 2) {
            "Divisão ${division.value} precisa de ao menos 2 clubes: ${divisionClubs.size}"
        }
        require(divisionClubs.size % 2 == 0) {
            "Número de clubes da divisão ${division.value} deve ser par: ${divisionClubs.size}"
        }
        require(division.value <= 9) {
            "Fórmula de matchId suporta no máximo 9 divisões: ${division.value}"
        }
        // Turno encerrado para esta divisão → ela folga nesta rodada.
        if (roundNumber > divisionClubs.size - 1) emptyList()
        else divisionMatches(roundNumber, division, divisionClubs)
    }
    require(matches.isNotEmpty()) {
        "Nenhuma divisão tem partidas na rodada $roundNumber — temporada já encerrada"
    }

    return Round(
        number = roundNumber,
        season = season,
        status = RoundStatus.Scheduled,
        matches = matches,
    )
}

private fun divisionMatches(roundNumber: Int, division: Division, clubs: List<Club>): List<RoundMatch> {
    // Ordena por id para determinismo independente da ordem de entrada e
    // para garantir o "clube fixo" do Berger (o de menor id).
    val ordered = clubs.sortedBy { it.id.value }
    val n = ordered.size
    val fixed = ordered.first()
    val rotating = ordered.drop(1)

    // Rotação dependente da rodada — ciclo de N-1 posições (turno único).
    val offset = (roundNumber - 1) % (n - 1)
    val rotated = rotating.drop(offset) + rotating.take(offset)
    val order = listOf(fixed) + rotated

    return (0 until n / 2).map { i ->
        val home = order[i]
        val away = order[n - 1 - i]
        RoundMatch(
            // rodada × 1000 + divisão × 100 + índice: único entre divisões da
            // mesma rodada (e é a seed do RNG da simulação). Mesma fórmula do
            // RoundBuilder da DSL e do mock em frontend/src/mocks/seed.ts.
            matchId = roundNumber * 1000L + division.value * 100L + i + 1,
            homeClubId = home.id,
            awayClubId = away.id,
            homeClubName = home.name,
            awayClubName = away.name,
            division = division,
        )
    }
}

/**
 * Nº de rodadas da temporada: o turno da maior divisão (N-1 rodadas para N
 * clubes). Divisões menores encerram antes e folgam no fim do calendário.
 */
fun seasonRounds(clubs: List<Club>): Int {
    require(clubs.isNotEmpty()) { "Liga sem clubes não tem temporada" }
    return clubs.groupBy { it.division }.values.maxOf { it.size } - 1
}

/**
 * A liga consegue gerar calendário? Toda divisão precisa de nº par (Berger
 * de turno único) e ao menos 2 clubes.
 */
fun canScheduleSeason(clubs: List<Club>): Boolean =
    clubs.isNotEmpty() &&
        clubs.groupBy { it.division }.values.all { it.size >= 2 && it.size % 2 == 0 }
