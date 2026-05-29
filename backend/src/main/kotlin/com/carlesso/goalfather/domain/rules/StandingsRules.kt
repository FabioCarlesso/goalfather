package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.domain.model.StandingRow
import com.carlesso.goalfather.domain.model.Standings

/**
 * Aplica o resultado de uma rodada finalizada à tabela.
 *
 * Função PURA — recebe e devolve `Standings` sem efeitos colaterais.
 * Espelha `applyRoundToStandings` em `frontend/src/mocks/seed.ts`.
 *
 * Regras:
 * - Vitória vale 3 pts, empate 1, derrota 0.
 * - Tiebreak: pontos → saldo de gols → gols pró → nome alfabético.
 * - Partidas com `status != Finished` são ignoradas (rodada parcial).
 */
fun applyRoundToStandings(round: Round, current: Standings): Standings {
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

    val sorted = byId.values.sortedWith(
        compareByDescending<StandingRow> { it.points }
            .thenByDescending { it.goalDifference }
            .thenByDescending { it.goalsFor }
            .thenBy { it.clubName },
    )

    return Standings(
        season = current.season,
        round = round.number,
        rows = sorted.mapIndexed { i, row -> row.copy(position = i + 1) },
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
