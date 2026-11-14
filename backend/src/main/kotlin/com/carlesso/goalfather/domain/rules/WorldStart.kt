package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.CompetitionKind
import com.carlesso.goalfather.domain.model.Cup
import com.carlesso.goalfather.domain.model.GameCompetition
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.Standings
import com.carlesso.goalfather.domain.model.leagueOf
import kotlin.random.Random

/**
 * O que um mundo recém-criado precisa além dos clubes (issue #131): o
 * primeiro encaixe do calendário, as tabelas zeradas de cada liga e as copas
 * já sorteadas. É a costura entre o seed (que monta clubes e catálogo a partir
 * do pack) e o calendário (que sabe montar a temporada).
 */
data class WorldStart(
    val firstRound: Round?,
    val standings: List<Standings>,
    val cups: List<Cup>,
)

/**
 * Abre a temporada [year] do mundo: [clubs] já nas suas ligas e [competitions]
 * do catálogo do save. Todo país começa a edição de [year] — o Brasil a de
 * 2026 (jan–dez), a Europa a de 2026/27 (ago–mai). Função PURA — o seed e o
 * reset do E2E só gravam o que ela devolve.
 *
 * - tabelas: uma por liga com clubes, zeradas, com as faixas das regras de
 *   vaga do mundo;
 * - copas: cada copa nacional sorteada entre os clubes das ligas do país, com
 *   a seed da copa + temporada (a mesma regra da virada);
 * - primeiro encaixe: o primeiro do plano do mundo — em 2026, janeiro, no
 *   Brasil (a Europa só estreia em agosto).
 */
fun startWorld(year: Int, clubs: List<Club>, competitions: List<GameCompetition>): WorldStart {
    val cups = competitions
        .filter { it.kind == CompetitionKind.NationalCup }
        .mapNotNull { game ->
            val entrants = clubs.filter { it.country.code == game.country.code && competitions.leagueOf(it) != null }
            drawNationalCup(year, game, entrants, Random(nationalCupDrawSeed(year, game.id)))?.let { game.id to it }
        }
        .toMap()
    val seasons = competitions.associate { it.country.code to year }
    val plan = worldPlan(competitions, clubs, seasons, cups)
    return WorldStart(
        firstRound = plan.first?.let { plan.roundAt(it, clubs) },
        standings = seasonStandings(year, competitions, clubs),
        cups = cups.values.toList(),
    )
}
