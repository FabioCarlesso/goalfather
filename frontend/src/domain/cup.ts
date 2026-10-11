// Rótulos da copa mata-mata e do calendário (issue #61).
//
// SÓ apresentação: quem passou, quanto vale cada fase e em que data cai cada
// fase são regras do backend (`CupRules.kt`, `SeasonCalendar.kt`) e chegam
// prontos pelo contrato — mesma separação de `training.ts`.

import type { KnockoutPhase, RoundStage } from './types'

export const KNOCKOUT_PHASE_LABEL: Record<KnockoutPhase, string> = {
  ROUND_OF_32: '32-avos de final',
  ROUND_OF_16: 'Oitavas de final',
  QUARTER_FINALS: 'Quartas de final',
  SEMI_FINALS: 'Semifinal',
  FINAL: 'Final',
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
      return `Copa — ${KNOCKOUT_PHASE_LABEL[stage.phase]}`
  }
}
