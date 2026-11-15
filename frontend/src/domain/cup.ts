// Rótulos da copa mata-mata e do calendário (issues #61 e #131).
//
// SÓ apresentação: quem passou, o agregado, quanto vale cada fase e em que
// data cai cada perna são regras do backend (`CupRules.kt`, `SeasonCalendar.kt`)
// e chegam prontos pelo contrato — mesma separação de `training.ts`. O nome da
// fase também vem pronto (`stageName`): as fases são dado do formato da
// competição, não um enum que a UI traduz.

import type { CupLeg, CupLegs, RoundStage } from './types'

/** Sufixo da perna: jogo único não tem; ida e volta dizem qual jogo é. */
export const CUP_LEG_LABEL: Record<CupLeg, string> = {
  SINGLE: '',
  FIRST: 'ida',
  SECOND: 'volta',
}

export const CUP_LEGS_LABEL: Record<CupLegs, string> = {
  SINGLE: 'jogo único',
  TWO_LEGGED: 'ida e volta',
}

/** "Quartas de final — ida", "Final". */
export function cupStageLabel(stage: Extract<RoundStage, { type: 'Cup' }>): string {
  const leg = CUP_LEG_LABEL[stage.leg]
  return leg ? `${stage.stageName} — ${leg}` : stage.stageName
}

/**
 * Título de uma data do calendário. `switch` exaustivo sobre o `oneOf` do
 * contrato — espelha o `when` sobre o `sealed interface RoundStage` do Kotlin:
 * um terceiro tipo de data quebra o TS aqui.
 */
export function stageTitle(stage: RoundStage): string {
  switch (stage.type) {
    case 'League':
      return `Rodada ${stage.matchday}`
    case 'Cup':
      return `Copa — ${cupStageLabel(stage)}`
  }
}
