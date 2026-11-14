package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundStage
import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.domain.model.StandingRow
import com.carlesso.goalfather.domain.model.Standings
import com.carlesso.goalfather.domain.model.TableZone

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
 * - Jogo de COPA não mexe na tabela (issue #61): mata-mata não vale ponto.
 *   Desde o calendário semanal (issue #125) o encaixe pode juntar liga e copa,
 *   então o filtro é POR PARTIDA, com o `when` sobre o `sealed` [RoundStage]
 *   forçando a decisão aqui em vez de confiar que o chamador filtre.
 * - A tabela conta a rodada pela `matchday` da LIGA — com a copa no meio, o
 *   encaixe da 3ª rodada do campeonato é outro, e é a rodada que o técnico lê.
 *   Encaixe sem jogo desta tabela devolve a tabela intacta.
 */
fun applyRoundToStandings(round: Round, current: Standings): Standings {
    val byId = current.rows.associateBy { it.clubId }.toMutableMap()
    val leagueMatches = round.matches.mapNotNull { match ->
        when (val stage = match.stage) {
            is RoundStage.League -> (match to stage.matchday).takeIf { match.homeClubId in byId && match.awayClubId in byId }
            is RoundStage.Cup -> null
        }
    }
    // `maxOfOrNull` + elvis: nenhum jogo de liga desta tabela → nada muda.
    val matchday = leagueMatches.maxOfOrNull { it.second } ?: return current

    for ((match, _) in leagueMatches) {
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
    // A faixa é da POSIÇÃO, não do clube (issue #128): quem sobe na tabela
    // herda a faixa do lugar que ocupou.
    val zoneAt = current.rows.associate { it.position to it.zone }
    return current.copy(
        round = matchday,
        rows = rankTable(byId.values.toList(), LEAGUE_COMPETITION_TIEBREAKERS)
            .map { it.copy(zone = zoneAt[it.position] ?: TableZone.NONE) },
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

/**
 * Tabelas zeradas da temporada [season], uma por divisão, clubes em ordem de
 * id — o ponto de partida de toda temporada nova. Saiu do `PlayRoundService`
 * (issue #125) porque o boot também reinicia temporada (ver `DataInitializer`).
 */
fun seasonStandings(season: Int, clubs: List<Club>): List<Standings> {
    val byDivision = clubs.groupBy { it.division }.toSortedMap()
    return byDivision.map { (division, divisionClubs) ->
        Standings(
            season = season,
            round = 0,
            rows = divisionClubs.sortedBy { it.id.value }.mapIndexed { i, club ->
                StandingRow(position = i + 1, clubId = club.id, clubName = club.name)
            },
            division = division,
        )
    }.withPyramidZones() // faixas e vagas das regras da pirâmide (issue #128)
}
