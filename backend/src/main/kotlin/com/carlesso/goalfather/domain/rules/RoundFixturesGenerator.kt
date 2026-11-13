package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.Matchday
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundMatch
import com.carlesso.goalfather.domain.model.RoundStage
import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.domain.model.SeasonCompetitions

/**
 * Gera os jogos de liga de um encaixe via algoritmo de Berger (round-robin
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
 * rodadas além do seu turno (ver [seasonRounds]).
 *
 * Espelha `generateRound` em `frontend/src/mocks/seed.ts`.
 *
 * [matchday] é o ENCAIXE do calendário (issue #125) e [leagueRound] a rodada
 * da LIGA: o Berger gira pela rodada (é ela que define quem enfrenta quem) e o
 * `matchId` usa o encaixe, que é único no jogo inteiro.
 *
 * Idioma Kotlin: função top-level pura em `domain/rules` (sem classe
 * utilitária estática à la Java) e `data class` imutáveis montadas via
 * `map`/`flatMap` — nenhuma mutação de estado.
 */
fun generateRound(matchday: Matchday, season: Int, clubs: List<Club>, leagueRound: Int = 1): Round {
    // `toSortedMap` garante ordem estável (divisão 1, 2, ...) para o
    // determinismo do schedule e da numeração das partidas.
    val byDivision = clubs.groupBy { it.division }.toSortedMap()
    require(byDivision.isNotEmpty()) { "Liga precisa de ao menos 1 divisão" }

    val matches = byDivision.flatMap { (division, divisionClubs) ->
        leagueMatches(matchday, leagueRound, division, divisionClubs)
    }
    require(matches.isNotEmpty()) {
        "Nenhuma divisão tem partidas na rodada $leagueRound — temporada já encerrada"
    }

    return Round(matchday = matchday, season = season, status = RoundStatus.Scheduled, matches = matches)
}

/**
 * Os jogos da rodada [leagueRound] da liga de UMA divisão, marcados no
 * encaixe [matchday]. Lista vazia quando o turno da divisão já acabou (folga).
 */
fun leagueMatches(matchday: Matchday, leagueRound: Int, division: Division, clubs: List<Club>): List<RoundMatch> {
    require(leagueRound >= 1) { "leagueRound deve ser >= 1: $leagueRound" }
    require(clubs.size >= 2) { "Divisão ${division.value} precisa de ao menos 2 clubes: ${clubs.size}" }
    require(clubs.size % 2 == 0) { "Número de clubes da divisão ${division.value} deve ser par: ${clubs.size}" }
    require(division.value <= 9) { "Fórmula de matchId suporta no máximo 9 divisões: ${division.value}" }
    // Turno encerrado para esta divisão → ela folga nesta rodada.
    if (leagueRound > turnRounds(clubs.size)) return emptyList()

    // O pareamento é o Berger genérico dos formatos de competição (issue #124),
    // que ordena por id e mantém o "clube fixo" de menor id — mesmos jogos de antes.
    val byId = clubs.associateBy { it.id }
    val pairings = roundRobinMatchday(clubs.map { it.id }, leagueRound)

    return pairings.mapIndexed { i, pairing ->
        val home = byId.getValue(pairing.home)
        val away = byId.getValue(pairing.away)
        RoundMatch(
            // encaixe × 1000 + divisão × 100 + índice: único no jogo inteiro (o
            // serial do encaixe não se repete entre temporadas) e é a seed do RNG
            // da simulação. Mesma fórmula do mock em frontend/src/mocks/seed.ts.
            matchId = matchday.serial * Matchday.MATCH_IDS_PER_MATCHDAY + division.value * 100L + i + 1,
            homeClubId = home.id,
            awayClubId = away.id,
            homeClubName = home.name,
            awayClubName = away.name,
            division = division,
            stage = RoundStage.League(leagueRound),
            competition = SeasonCompetitions.leagueId(division),
            competitionName = SeasonCompetitions.leagueName(division),
        )
    }
}

/**
 * Nº de rodadas da temporada: o turno da maior divisão (N-1 rodadas para N
 * clubes). Divisões menores encerram antes e folgam no fim do calendário.
 */
fun seasonRounds(clubs: List<Club>): Int {
    require(clubs.isNotEmpty()) { "Liga sem clubes não tem temporada" }
    return clubs.groupBy { it.division }.values.maxOf { turnRounds(it.size) }
}

/**
 * A liga consegue gerar calendário? Toda divisão precisa de nº par (Berger
 * de turno único) e ao menos 2 clubes.
 */
fun canScheduleSeason(clubs: List<Club>): Boolean =
    clubs.isNotEmpty() &&
        clubs.groupBy { it.division }.values.all { it.size >= 2 && it.size % 2 == 0 }
