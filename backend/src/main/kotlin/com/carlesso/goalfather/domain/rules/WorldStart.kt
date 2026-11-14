package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.Cup
import com.carlesso.goalfather.domain.model.GameCompetition
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.Standings
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
 * do catálogo do save. Função PURA — o seed e o reset do E2E só gravam o que
 * ela devolve.
 *
 * Implementação de transição: ainda delega ao calendário por divisão. A frente
 * de competições da #131 a reescreve sobre o catálogo.
 */
@Suppress("UNUSED_PARAMETER")
fun startWorld(year: Int, clubs: List<Club>, competitions: List<GameCompetition>): WorldStart {
    val cup = if (canHoldCup(clubs)) drawCup(year, clubs, Random(cupDrawSeed(year))) else null
    val plan = if (canScheduleSeason(clubs)) seasonPlan(year, clubs, cup) else null
    return WorldStart(
        firstRound = plan?.first?.let { plan.roundAt(it, clubs, cup) },
        standings = seasonStandings(year, clubs),
        cups = listOfNotNull(cup),
    )
}
