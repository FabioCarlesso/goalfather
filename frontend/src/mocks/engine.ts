// Engine de simulação de partida — versão JS dos mocks.
// Função pura (gerador + RNG determinística) que produz MatchEvents minuto a minuto.
//
// IMPORTANTE: esta engine vive APENAS em src/mocks/ e existe só para alimentar
// o handler WebSocket de desenvolvimento. Quando o backend Kotlin tiver o
// MatchSimulator com Flow<MatchEvent>, esta engine é descartada. A app real
// (src/api/, src/pages/) nunca sabe que esta engine existe.

import type { MatchEvent } from '../domain/types'

/** RNG seedável (mulberry32) — mesmo seed → mesma sequência de eventos. */
export class MulberryRng {
  private state: number
  constructor(seed: number) {
    this.state = seed >>> 0
  }
  next(): number {
    let t = (this.state += 0x6d2b79f5)
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
  pick<T>(arr: readonly T[]): T {
    if (arr.length === 0) throw new Error('pick: array vazio')
    return arr[Math.floor(this.next() * arr.length)] as T
  }
}

export interface MatchSetup {
  matchId: number
  homeName: string
  awayName: string
  homeStrength: number   // média de overall (0–99)
  awayStrength: number
  homeSquad: number[]    // IDs dos jogadores para escolher autor de gol/cartão/lesão
  awaySquad: number[]
}

const EVENT_RATE       = 0.12  // chance de algo acontecer por minuto
const P_GOAL_AT_EVENT  = 0.32
const P_SAVE_AT_EVENT  = 0.30
const P_CARD_AT_EVENT  = 0.22
const P_INJURY_AT_EVENT = 1.0 - P_GOAL_AT_EVENT - P_SAVE_AT_EVENT - P_CARD_AT_EVENT
const P_RED_CARD       = 0.12

/**
 * Gera o fluxo de eventos da partida.
 * KickOff → eventos minuto a minuto → FullTime.
 */
export function* simulateMatch(
  setup: MatchSetup,
  rng: MulberryRng = new MulberryRng(setup.matchId),
): Generator<MatchEvent> {
  yield {
    type: 'KickOff',
    minute: 0,
    homeClubName: setup.homeName,
    awayClubName: setup.awayName,
    homeStrength: setup.homeStrength,
    awayStrength: setup.awayStrength,
  }

  let homeGoals = 0
  let awayGoals = 0
  const totalStrength = setup.homeStrength + setup.awayStrength
  const homeRatio = totalStrength > 0 ? setup.homeStrength / totalStrength : 0.5

  // Suprime o aviso de unused parameter quando os squads estão vazios (defensivo)
  void P_INJURY_AT_EVENT

  for (let minute = 1; minute <= 90; minute++) {
    if (rng.next() >= EVENT_RATE) continue

    const roll = rng.next()
    if (roll < P_GOAL_AT_EVENT) {
      const home = rng.next() < homeRatio
      const squad = home ? setup.homeSquad : setup.awaySquad
      const scorerId = squad.length > 0 ? rng.pick(squad) : 0
      if (home) homeGoals++; else awayGoals++
      yield { type: 'Goal', minute, scorerId, home }
    } else if (roll < P_GOAL_AT_EVENT + P_SAVE_AT_EVENT) {
      yield { type: 'Save', minute }
    } else if (roll < P_GOAL_AT_EVENT + P_SAVE_AT_EVENT + P_CARD_AT_EVENT) {
      const home = rng.next() < 0.5
      const squad = home ? setup.homeSquad : setup.awaySquad
      const playerId = squad.length > 0 ? rng.pick(squad) : 0
      yield { type: 'Card', minute, playerId, red: rng.next() < P_RED_CARD }
    } else {
      const home = rng.next() < 0.5
      const squad = home ? setup.homeSquad : setup.awaySquad
      const playerId = squad.length > 0 ? rng.pick(squad) : 0
      yield { type: 'Injury', minute, playerId }
    }
  }

  yield { type: 'FullTime', minute: 90, homeGoals, awayGoals }
}

/** Média de overall do elenco — usado como "força" agregada do time. */
export function averageOverall(squad: { overall: number }[]): number {
  if (squad.length === 0) return 60
  return squad.reduce((sum, p) => sum + p.overall, 0) / squad.length
}
