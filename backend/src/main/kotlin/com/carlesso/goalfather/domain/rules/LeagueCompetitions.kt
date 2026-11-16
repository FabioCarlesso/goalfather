package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Competition
import com.carlesso.goalfather.domain.model.CompetitionFormat
import com.carlesso.goalfather.domain.model.Tiebreaker

/**
 * A liga que o jogo já tem, descrita no modelo genérico de formatos (issue
 * #124): `generateRound` e `applyRoundToStandings` rodam sobre os geradores e
 * o desempate genéricos. Desde o calendário semanal (issue #125) o plano da
 * temporada (`seasonPlan`) monta uma `Competition` com este formato para cada
 * liga — é dela que o agendador tira o encaixe preferido. A copa traz o
 * próprio formato (`Cup.format`, issue #131).
 */

/** Critérios da liga do jogo desde a Fase 1: pontos → saldo → gols pró. */
val LEAGUE_COMPETITION_TIEBREAKERS: List<Tiebreaker> = Competition.DEFAULT_TIEBREAKERS

/** A liga do jogo: turno único por divisão. */
val LEAGUE_FORMAT: CompetitionFormat.RoundRobin = CompetitionFormat.RoundRobin(legs = 1)
