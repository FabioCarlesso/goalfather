// Rótulos do calendário semanal (issue #125).
//
// SÓ apresentação: em que semana cai cada jogo, qual é o próximo encaixe e o
// próximo jogo do clube são regras do backend (`MatchdayScheduler.kt`,
// `SeasonCalendar.kt`) e chegam prontos pelo contrato — este módulo só
// transforma em texto o que a API entregou.

import { KNOCKOUT_PHASE_LABEL, stageTitle } from './cup'
import type { Matchday, Round, RoundMatch, RoundStage, Slot } from './types'

export const SLOT_LABEL: Record<Slot, string> = {
  MIDWEEK: 'Meio de semana',
  WEEKEND: 'Fim de semana',
}

/** `2026-02-01` → `01/02/2026`, sem passar por `Date` (fuso não muda a data do jogo). */
export function formatGameDate(isoDate: string): string {
  const [year, month, day] = isoDate.split('-')
  return `${day}/${month}/${year}`
}

/** "Semana 5 · Fim de semana · 01/02/2026". */
export function formatMatchday(matchday: Matchday): string {
  return `Semana ${matchday.week} · ${SLOT_LABEL[matchday.slot]} · ${formatGameDate(matchday.date)}`
}

/**
 * O que se disputa no encaixe, sem repetição e na ordem em que aparece. Desde
 * a issue #125 o `stage` é de cada partida: um encaixe pode juntar liga e copa.
 */
export function roundStages(round: Pick<Round, 'matches'>): RoundStage[] {
  const seen = new Map<string, RoundStage>()
  for (const m of round.matches) seen.set(JSON.stringify(m.stage), m.stage)
  return [...seen.values()]
}

/** Título do encaixe: "Rodada 3", "Copa — Semifinal" ou os dois juntos. */
export function roundTitle(round: Pick<Round, 'matches'>): string {
  const stages = roundStages(round)
  return stages.length === 0 ? 'Encaixe sem jogos' : stages.map(stageTitle).join(' + ')
}

/** O encaixe tem jogo de mata-mata? Então empate vai aos pênaltis. */
export function hasKnockout(round: Pick<Round, 'matches'>): boolean {
  return round.matches.some((m) => m.stage.type === 'Cup')
}

/**
 * O estágio que o resumo do encaixe destaca: a rodada de LIGA, se houver (é
 * ela que mexe na tabela); senão a fase da copa.
 */
export function primaryStage(round: Pick<Round, 'matches'>): RoundStage | null {
  const stages = roundStages(round)
  return stages.find((s) => s.type === 'League') ?? stages[0] ?? null
}

/** "Liga — Divisão 1 · Rodada 3" / "Copa · Semifinal": competição + o que a partida disputa. */
export function matchCompetitionLabel(match: Pick<RoundMatch, 'competitionName' | 'stage'>): string {
  switch (match.stage.type) {
    case 'League':
      return `${match.competitionName} · Rodada ${match.stage.matchday}`
    case 'Cup':
      return `${match.competitionName} · ${KNOCKOUT_PHASE_LABEL[match.stage.phase]}`
  }
}
