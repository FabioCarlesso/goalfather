package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundStage
import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.domain.model.StandingRow
import com.carlesso.goalfather.domain.model.Standings

/**
 * Aplica o resultado de uma rodada finalizada à tabela de UMA divisão.
 *
 * Função PURA — recebe e devolve `Standings` sem efeitos colaterais.
 * Espelha `applyRoundToStandings` em `frontend/src/mocks/seed.ts`.
 *
 * Regras:
 * - Vitória vale 3 pts, empate 1, derrota 0.
 * - Tiebreak: [LEAGUE_COMPETITION_TIEBREAKERS] (pontos → saldo → gols pró),
 *   aplicados por [rankTable] (issue #124); esgotados os critérios, nome alfabético.
 * - Partidas com `status != Finished` são ignoradas (rodada parcial).
 * - Partidas de clubes que não estão em `current.rows` são ignoradas —
 *   com múltiplas divisões (issue #47), a MESMA rodada pode ser aplicada
 *   a cada tabela e só os jogos daquela divisão contam.
 * - Rodada de COPA não mexe na tabela (issue #61): mata-mata não vale ponto.
 *   O `when` sobre o `sealed` [RoundStage] força a decisão aqui, em vez de
 *   confiar que o chamador lembre de filtrar. A tabela também conta a rodada
 *   pela `matchday` da liga — com a copa no meio, a data 5 pode ser a 3ª
 *   rodada do campeonato, e é esta que o técnico lê.
 */
fun applyRoundToStandings(round: Round, current: Standings): Standings {
    val matchday = when (val stage = round.stage) {
        is RoundStage.League -> stage.matchday
        is RoundStage.Cup -> return current
    }
    val byId = current.rows.associateBy { it.clubId }.toMutableMap()

    for (match in round.matches) {
        if (match.status != RoundStatus.Finished) continue
        val home = byId[match.homeClubId] ?: continue
        val away = byId[match.awayClubId] ?: continue

        byId[match.homeClubId] = home.updatedForMatch(
            ownGoals = match.homeGoals,
            againstGoals = match.awayGoals,
        )
        byId[match.awayClubId] = away.updatedForMatch(
            ownGoals = match.awayGoals,
            againstGoals = match.homeGoals,
        )
    }

    // `copy` preserva divisão e vagas de promoção/rebaixamento da tabela.
    // A ordenação é a genérica dos formatos de competição (issue #124), com os
    // critérios que a liga do jogo sempre usou — mesmo resultado de antes.
    return current.copy(
        round = matchday,
        rows = rankTable(byId.values.toList(), LEAGUE_COMPETITION_TIEBREAKERS),
    )
}

/**
 * Atualiza uma linha após uma partida. Extension function private —
 * encapsula a regra de pontuação perto do tipo que ela manipula.
 */
private fun StandingRow.updatedForMatch(ownGoals: Int, againstGoals: Int): StandingRow {
    val pointsAdded = when {
        ownGoals > againstGoals -> 3
        ownGoals == againstGoals -> 1
        else -> 0
    }
    return copy(
        played = played + 1,
        goalsFor = goalsFor + ownGoals,
        goalsAgainst = goalsAgainst + againstGoals,
        goalDifference = goalDifference + (ownGoals - againstGoals),
        wins = wins + (if (ownGoals > againstGoals) 1 else 0),
        draws = draws + (if (ownGoals == againstGoals) 1 else 0),
        losses = losses + (if (ownGoals < againstGoals) 1 else 0),
        points = points + pointsAdded,
    )
}
