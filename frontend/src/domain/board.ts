// Rótulos e textos da diretoria (issue #62).
//
// SÓ apresentação. Quanto a confiança anda, qual o limiar de demissão e qual o
// humor da diretoria são regra de domínio (`domain/rules/BoardRules.kt`) e
// chegam prontos no `BoardStatus` (`mood`, `firingConfidence`) — espelhados
// apenas pela engine de mock em `src/mocks/board.ts`.

import type { BoardMood, BoardObjective, BoardVerdict } from './types'

export const BOARD_OBJECTIVE_LABEL: Record<BoardObjective, string> = {
  TITLE: 'Conquistar o título',
  TOP_HALF: 'Terminar na metade de cima',
  MID_TABLE: 'Terminar no meio da tabela',
}

export const BOARD_MOOD_LABEL: Record<BoardMood, string> = {
  CONFIDENT: 'Diretoria satisfeita',
  WARY: 'Diretoria atenta',
  AT_RISK: 'Cargo em risco',
}

/** Classes da faixa de humor — cor do texto e da barra de confiança. */
export const BOARD_MOOD_TONE: Record<BoardMood, { text: string; bar: string }> = {
  CONFIDENT: { text: 'text-emerald-300', bar: 'bg-emerald-500' },
  WARY: { text: 'text-amber-300', bar: 'bg-amber-500' },
  AT_RISK: { text: 'text-red-300', bar: 'bg-red-500' },
}

/** Posição ordinal na tabela: 1 → "1º". */
export const ordinal = (position: number): string => `${position}º`

/**
 * Manchete do veredito de fim de temporada. `switch` exaustivo sobre o
 * discriminador — variante nova no contrato quebra a compilação aqui.
 */
export function boardVerdictHeadline(verdict: BoardVerdict): string {
  switch (verdict.type) {
    case 'TargetMet':
      return 'Meta cumprida — a diretoria pagou o bônus'
    case 'TargetMissed':
      return 'Meta não cumprida — a diretoria mantém o técnico'
    case 'Fired':
      return 'Você foi demitido pela diretoria'
  }
}
