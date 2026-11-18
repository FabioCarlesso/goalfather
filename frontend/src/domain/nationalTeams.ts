// Rótulos das seleções nas datas FIFA (issue #134).
//
// SÓ apresentação: quem é convocado, os placares, a classificação das
// Eliminatórias e o ranking são regras do backend (`NationalTeamRules.kt`) e
// chegam prontos pelo contrato.

import { formatGameDate } from './calendar'
import type { FifaWindowStatus, FifaWindowSummary, NationalMatchKind } from './types'

export const WINDOW_STATUS_LABEL: Record<FifaWindowStatus, string> = {
  UPCOMING: 'Próxima',
  CALLED: 'Em andamento',
  PLAYED: 'Disputada',
}

export const MATCH_KIND_LABEL: Record<NationalMatchKind, string> = {
  QUALIFIER: 'Eliminatórias',
  FRIENDLY: 'Amistoso',
}

/** "23/03/2026 a 31/03/2026". */
export function windowRange(window: Pick<FifaWindowSummary, 'start' | 'end'>): string {
  return `${formatGameDate(window.start)} a ${formatGameDate(window.end)}`
}

/** "1 jogador convocado", "3 jogadores convocados". */
export function calledUpCount(n: number): string {
  return n === 1 ? '1 jogador convocado' : `${n} jogadores convocados`
}
