package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.CompetitionKind
import com.carlesso.goalfather.domain.model.GameCompetition
import com.carlesso.goalfather.domain.model.Matchday
import com.carlesso.goalfather.domain.model.Pairing
import com.carlesso.goalfather.domain.model.RoundMatch
import com.carlesso.goalfather.domain.model.RoundStage

/**
 * Os jogos de uma rodada de LIGA marcada num encaixe (issues #47, #125, #131).
 * Função PURA — mesmo input produz exatamente os mesmos jogos (critério de
 * determinismo da issue #1).
 *
 * Os pareamentos vêm prontos do formato da liga (`roundRobinSchedule`, Berger
 * com turno e returno); aqui só se transformam em [RoundMatch] com os nomes
 * dos clubes e a competição do catálogo.
 *
 * `matchId = encaixe × 1000 + slot × 100 + índice`: [slot] (1..9) é a posição
 * da liga no catálogo do save — a Série A e a Premier League são ambas
 * divisão 1, então a divisão já não distingue as ligas de um mesmo encaixe. A
 * copa usa `encaixe × 1000 + índice` (< 100), que nenhuma liga usa: todas
 * podem dividir o encaixe sem colidir. O id é também a seed da simulação.
 *
 * Idioma Kotlin: função top-level pura em `domain/rules` (sem classe
 * utilitária estática à la Java) e `data class` imutáveis montadas via `map`.
 */
fun leagueMatches(
    matchday: Matchday,
    leagueRound: Int,
    league: GameCompetition,
    slot: Int,
    pairings: List<Pairing>,
    clubsById: Map<ClubId, Club>,
): List<RoundMatch> {
    require(leagueRound >= 1) { "leagueRound deve ser >= 1: $leagueRound" }
    require(slot in 1..MAX_LEAGUE_SLOTS) { "Slot de liga fora de 1..$MAX_LEAGUE_SLOTS: $slot (${league.id.value})" }
    require(pairings.size < LEAGUE_MATCHES_PER_SLOT) { "Jogos demais numa rodada de ${league.id.value}: ${pairings.size}" }
    val division = when (val kind = league.kind) {
        is CompetitionKind.League -> kind.tier
        CompetitionKind.NationalCup, CompetitionKind.Continental ->
            error("${league.id.value} é copa, não liga")
    }
    return pairings.mapIndexed { i, pairing ->
        val home = clubsById.getValue(pairing.home)
        val away = clubsById.getValue(pairing.away)
        RoundMatch(
            matchId = matchday.serial * Matchday.MATCH_IDS_PER_MATCHDAY +
                slot * LEAGUE_MATCHES_PER_SLOT + i + 1,
            homeClubId = home.id,
            awayClubId = away.id,
            homeClubName = home.name,
            awayClubName = away.name,
            division = division,
            stage = RoundStage.League(leagueRound),
            competition = league.id,
            competitionName = league.competition.name,
        )
    }
}

/** Quantas ligas cabem na faixa de `matchId` de um encaixe (os slots 1..9). */
const val MAX_LEAGUE_SLOTS: Int = 9

/** Largura da faixa de `matchId` de cada liga dentro do encaixe. */
private const val LEAGUE_MATCHES_PER_SLOT: Long = 100
