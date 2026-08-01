// Engine de simulação de partida — versão JS dos mocks.
// Função pura (gerador + RNG determinística) que produz MatchEvents minuto a minuto.
//
// IMPORTANTE: esta engine vive APENAS em src/mocks/ e existe só para alimentar
// o handler WebSocket de desenvolvimento. Quando o backend Kotlin tiver o
// MatchSimulator com Flow<MatchEvent>, esta engine é descartada. A app real
// (src/api/, src/pages/) nunca sabe que esta engine existe.

import type { Availability, MatchEvent } from '../domain/types'

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

// ─── Fadiga e lesões (issue #54) — espelham domain/rules/FitnessRules.kt ───
export const INJURY_DURATION_MIN = 1
export const INJURY_DURATION_MAX = 4
export const STARTER_STAMINA_LOSS_MIN = 10
export const STARTER_STAMINA_LOSS_MAX = 25
export const STAMINA_MATCH_FLOOR = 40
export const BENCH_STAMINA_RECOVERY = 12
export const STAMINA_FULL_PERFORMANCE = 70
export const MEDICAL_DEPARTMENT_COST_CENTS = 30_000_00
export const MEDICAL_STAMINA_RECOVERY = 30

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
      // Duração sorteada com o RNG da própria partida (issue #54) — espelha
      // drawInjuryDuration do MatchSimulator.kt.
      const roundsOut = INJURY_DURATION_MIN
        + Math.floor(rng.next() * (INJURY_DURATION_MAX - INJURY_DURATION_MIN + 1))
      yield { type: 'Injury', minute, playerId, roundsOut }
    }
  }

  yield { type: 'FullTime', minute: 90, homeGoals, awayGoals }
}

/**
 * Overall efetivo — já descontada a fadiga (issue #54). Espelha
 * `Player.effectiveOverall()` do backend: sem penalidade acima do piso de
 * forma, queda proporcional abaixo dele.
 */
export const effectiveOverall = (p: { overall: number; stamina?: number }): number => {
  const stamina = p.stamina ?? 100
  return stamina >= STAMINA_FULL_PERFORMANCE
    ? p.overall
    : p.overall * (stamina / STAMINA_FULL_PERFORMANCE)
}

/** Força agregada do time = média do overall EFETIVO dos escalados. */
export function averageOverall(squad: { overall: number; stamina?: number }[]): number {
  if (squad.length === 0) return 60
  return squad.reduce((sum, p) => sum + effectiveOverall(p), 0) / squad.length
}

// ─── Desgaste de rodada (espelha FitnessRules.kt) ─────────────────────────

type FitPlayer = {
  id: number
  stamina: number
  availability: Availability
}

const advanceInjury = (a: Availability, rounds = 1): Availability =>
  a.type === 'Injured' && a.roundsOut - rounds >= 1
    ? { type: 'Injured', roundsOut: a.roundsOut - rounds }
    : { type: 'Available' }

/**
 * Aplica UMA rodada de desgaste: titulares cansam, reservas recuperam,
 * lesões em curso andam uma rodada e as novas entram em vigor. Mesma ordem
 * do backend — decrementa antes de aplicar as lesões da própria rodada.
 */
export function applyRoundFitness<T extends FitPlayer>(
  squad: T[],
  starterIds: Set<number>,
  newInjuries: Map<number, number>,
  rng: MulberryRng,
): T[] {
  return squad.map((p) => {
    const stamina = starterIds.has(p.id)
      ? Math.max(
          STAMINA_MATCH_FLOOR,
          p.stamina - (STARTER_STAMINA_LOSS_MIN
            + Math.floor(rng.next() * (STARTER_STAMINA_LOSS_MAX - STARTER_STAMINA_LOSS_MIN + 1))),
        )
      : Math.min(100, p.stamina + BENCH_STAMINA_RECOVERY)

    const advanced = advanceInjury(p.availability)
    const fresh = newInjuries.get(p.id)
    const availability: Availability =
      fresh == null
        ? advanced
        : {
            type: 'Injured',
            roundsOut: advanced.type === 'Injured' ? Math.max(advanced.roundsOut, fresh) : fresh,
          }

    return { ...p, stamina, availability }
  })
}

/** Sessão do departamento médico — espelha `applyMedicalTreatment` do backend. */
export function applyMedicalTreatment<T extends FitPlayer>(squad: T[]): T[] {
  return squad.map((p) => ({
    ...p,
    stamina: Math.min(100, p.stamina + MEDICAL_STAMINA_RECOVERY),
    availability: advanceInjury(p.availability),
  }))
}
