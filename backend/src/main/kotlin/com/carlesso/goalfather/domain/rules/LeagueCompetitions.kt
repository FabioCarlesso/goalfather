package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Competition
import com.carlesso.goalfather.domain.model.CompetitionFormat
import com.carlesso.goalfather.domain.model.KnockoutPhase
import com.carlesso.goalfather.domain.model.KnockoutStage
import com.carlesso.goalfather.domain.model.Legs
import com.carlesso.goalfather.domain.model.Tiebreaker

/**
 * A liga e a copa que o jogo já tem, descritas no modelo genérico de formatos
 * (issue #124). Hoje elas servem de ponte: `generateRound` e
 * `applyRoundToStandings` já rodam sobre os geradores e o desempate genéricos,
 * e o [cupFormat] descreve no formato `Knockout` a chave que `drawCup` sorteia.
 * Desde o calendário semanal (issue #125) o plano da temporada (`seasonPlan`)
 * monta uma `Competition` com estes formatos para cada liga e para a copa —
 * é dela que o agendador tira o encaixe preferido.
 */

/** Critérios da liga do jogo desde a Fase 1: pontos → saldo → gols pró. */
val LEAGUE_COMPETITION_TIEBREAKERS: List<Tiebreaker> = Competition.DEFAULT_TIEBREAKERS

/** A liga do jogo: turno único por divisão. */
val LEAGUE_FORMAT: CompetitionFormat.RoundRobin = CompetitionFormat.RoundRobin(legs = 1)

/**
 * A copa do jogo para [clubCount] clubes, como `Knockout`: jogo único em todas
 * as fases e as folgas de `drawCup` expressas como ENTRANTES da 2ª fase — quem
 * folga entra direto nela, igual à Série A na Copa do Brasil. 12 clubes numa
 * chave de 16 viram: 8 clubes na 1ª fase (4 jogos) + 4 entrantes nas quartas.
 */
fun cupFormat(clubCount: Int): CompetitionFormat.Knockout {
    require(clubCount in MIN_CUP_CLUBS..MAX_CUP_CLUBS) { "Copa exige de $MIN_CUP_CLUBS a $MAX_CUP_CLUBS clubes: $clubCount" }
    val bracketSize = (clubCount - 1).takeHighestOneBit() shl 1
    val byes = bracketSize - clubCount
    val phases = KnockoutPhase.entries.filter { it.teams <= bracketSize }

    val opening = phases.first()
    val firstStagePlayers = opening.teams - 2 * byes
    val stages = if (byes == 0) {
        phases.map { KnockoutStage(it.name, it.teams, Legs.SINGLE, newEntrants = if (it == opening) it.teams else 0) }
    } else {
        // Com folgas, a fase de abertura só tem os confrontos jogados; os
        // `byes` clubes entram na fase seguinte.
        listOf(KnockoutStage(opening.name, firstStagePlayers, Legs.SINGLE, newEntrants = firstStagePlayers)) +
            phases.drop(1).mapIndexed { i, phase ->
                KnockoutStage(phase.name, phase.teams, Legs.SINGLE, newEntrants = if (i == 0) byes else 0)
            }
    }
    return CompetitionFormat.Knockout(stages)
}
