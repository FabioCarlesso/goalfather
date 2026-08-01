// Leitura do estado físico do jogador (issue #54).
//
// Não há regra de domínio aqui — a matemática de fadiga/lesão vive só no
// backend (e, enquanto o mock existir, em `mocks/engine.ts`). Isto é apenas o
// estreitamento da união discriminada `Availability` do contrato, o análogo
// TS do `when` exaustivo sobre o `sealed interface` Kotlin.

import type { Availability, Player } from './types'

export const isInjured = (p: Player): boolean => p.availability.type === 'Injured'

/** Rodadas restantes de afastamento, ou `null` se o jogador está apto. */
export function injuryRoundsOut(a: Availability): number | null {
  switch (a.type) {
    case 'Injured':   return a.roundsOut
    case 'Available': return null
  }
}

/**
 * Faixa de forma física para a UI pintar. O limiar de 70 espelha o
 * `STAMINA_FULL_PERFORMANCE` do backend: abaixo dele o jogador rende menos.
 */
export function staminaLevel(stamina: number): 'fresh' | 'tired' | 'exhausted' {
  if (stamina >= 70) return 'fresh'
  if (stamina >= 50) return 'tired'
  return 'exhausted'
}
