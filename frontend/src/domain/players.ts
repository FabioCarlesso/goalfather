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
/**
 * Suspensão automática em vigor (issue #130) — leitura do contrato. Quem
 * decide quem cumpre gancho e em que jogo é o backend; aqui só se exibe.
 */
export const isSuspended = (p: Player): boolean => p.suspensions.length > 0

/** "Liga — Divisão 1 (1 jogo)", "Copa (2 jogos)" — para o rótulo do selo. */
export const suspensionSummary = (p: Player): string =>
  p.suspensions
    .map((s) => `${s.competitionName} (${s.matchesLeft} ${s.matchesLeft === 1 ? 'jogo' : 'jogos'})`)
    .join(', ')

/** Ocupa vaga de estrangeiro no clube? `foreign` vem pronto do backend (issue #130). */
export const isForeign = (p: Player): boolean => p.foreign === true

export function staminaLevel(stamina: number): 'fresh' | 'tired' | 'exhausted' {
  if (stamina >= 70) return 'fresh'
  if (stamina >= 50) return 'tired'
  return 'exhausted'
}
