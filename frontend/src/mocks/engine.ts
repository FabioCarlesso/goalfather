// Engine de simulação de partida — versão JS dos mocks.
// Função pura (gerador + RNG determinística) que produz MatchEvents minuto a minuto.
//
// IMPORTANTE: esta engine vive APENAS em src/mocks/ e existe só para alimentar
// o handler WebSocket de desenvolvimento. Quando o backend Kotlin tiver o
// MatchSimulator com Flow<MatchEvent>, esta engine é descartada. A app real
// (src/api/, src/pages/) nunca sabe que esta engine existe.

import type { Availability, MatchEvent, Player, Retirement } from '../domain/types'

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
  rng: MulberryRng,
  newInjuries: Map<number, number> = new Map(),
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

/**
 * Quem de fato entra em campo — espelha `Club.startingLineup()` do backend:
 * a escalação salva é revalidada contra o estado atual do elenco, lesionado
 * fica fora e reserva apto assume a vaga (issue #54).
 */
export function startingEleven<T extends FitPlayer>(
  squad: T[],
  savedPlayerIds: number[] | undefined,
): Set<number> {
  const available = squad.filter((p) => p.availability.type !== 'Injured')
  if (!savedPlayerIds) return new Set(available.slice(0, 11).map((p) => p.id))

  const byId = new Map(available.map((p) => [p.id, p]))
  const starters = savedPlayerIds.filter((id) => byId.has(id))
  const substitutes = available.filter((p) => !starters.includes(p.id)).map((p) => p.id)
  return new Set([...starters, ...substitutes].slice(0, 11))
}

// ─── Envelhecimento de temporada (espelha AgingRules.kt, issue #55) ───────

export const YOUNG_MAX_AGE = 23
export const PEAK_MAX_AGE = 29
export const RETIREMENT_MIN_AGE = 36
export const RETIREMENT_MAX_OVERALL = 70
export const FORCED_RETIREMENT_AGE = 41

/**
 * Variação de atributos sorteada por faixa etária. Assimétrica de propósito:
 * jovem pode estagnar mas não desabar, veterano pode ter um último bom ano mas
 * não vira craque aos 35. Mesmos números de `AgeBand` no Kotlin.
 */
export const AGE_BAND_DELTA = {
  YOUNG: { min: -1, max: 3 },
  PEAK: { min: -1, max: 1 },
  VETERAN: { min: -3, max: 1 },
} as const

export type AgeBand = keyof typeof AGE_BAND_DELTA

export const ageBandOf = (age: number): AgeBand =>
  age <= YOUNG_MAX_AGE ? 'YOUNG' : age <= PEAK_MAX_AGE ? 'PEAK' : 'VETERAN'

export const YOUTH_AGE_MIN = 17
export const YOUTH_AGE_MAX = 19
export const YOUTH_OVERALL_GAP_MIN = 8
export const YOUTH_OVERALL_GAP_MAX = 18
export const YOUTH_MIN_OVERALL = 40
export const YOUTH_SALARY_CENTS = 3_000_00

const shift = (value: number, delta: number): number => Math.min(99, Math.max(0, value + delta))

const retires = (p: { age: number; overall: number }): boolean =>
  p.age >= FORCED_RETIREMENT_AGE ||
  (p.age >= RETIREMENT_MIN_AGE && p.overall < RETIREMENT_MAX_OVERALL)

const drawInt = (rng: MulberryRng, min: number, max: number): number =>
  min + Math.floor(rng.next() * (max - min + 1))

/**
 * Envelhece o elenco UMA temporada e promove a base no lugar de quem se
 * aposentou. A reposição é 1:1 (mesma regra do backend): elenco que só encolhe
 * acabaria em time incompleto.
 *
 * O backend modela o desfecho de cada jogador num `sealed interface
 * AgingOutcome` (evoluiu/estagnou/regrediu/aposentou); aqui o mock só precisa
 * do elenco resultante e da lista de aposentadorias.
 *
 * `youthId` gera o id do garoto promovido — no backend a fórmula é
 * `youthPlayerId(clube, temporada, vaga)`; o mock tem um clube só.
 */
export function ageSquadOneSeason(
  squad: Player[],
  rng: MulberryRng,
  clubId = 1,
  youthId: (slot: number) => number = (slot) => 900_000 + slot,
): { squad: Player[]; retirements: Retirement[] } {
  const next: Player[] = []
  const retirements: Retirement[] = []

  const promote = (retired: Player): Player => {
    const slot = retirements.length + 1
    const overall = Math.max(
      YOUTH_MIN_OVERALL,
      Math.min(99, retired.overall - drawInt(rng, YOUTH_OVERALL_GAP_MIN, YOUTH_OVERALL_GAP_MAX)),
    )
    return {
      ...retired,
      id: youthId(slot),
      name: `Cria da base ${slot}`,
      overall,
      pace: overall,
      shooting: overall,
      passing: overall,
      defending: overall,
      salary: YOUTH_SALARY_CENTS,
      age: drawInt(rng, YOUTH_AGE_MIN, YOUTH_AGE_MAX),
      goals: 0,
      yellowCards: 0,
      redCards: 0,
      star: overall >= 82,
    }
  }

  for (const p of squad) {
    // Quem já bateu o teto de carreira sai sem sortear delta nenhum — mas ganha
    // o ano, como no backend, para o extrato mostrar a idade em que parou.
    if (p.age >= FORCED_RETIREMENT_AGE) {
      const retired = { ...p, age: Math.min(50, p.age + 1) }
      const promoted = promote(retired)
      next.push(promoted)
      retirements.push({ clubId, retired, promoted })
      continue
    }

    const age = p.age + 1
    const band = AGE_BAND_DELTA[ageBandOf(age)]
    const delta = drawInt(rng, band.min, band.max)
    const aged: Player = {
      ...p,
      age,
      overall: shift(p.overall, delta),
      pace: shift(p.pace, delta),
      shooting: shift(p.shooting, delta),
      passing: shift(p.passing, delta),
      defending: shift(p.defending, delta),
    }

    if (retires(aged)) {
      const promoted = promote(aged)
      next.push(promoted)
      retirements.push({ clubId, retired: aged, promoted })
    } else {
      next.push(aged)
    }
  }

  return { squad: next, retirements }
}

/** Sessão do departamento médico — espelha `applyMedicalTreatment` do backend. */
export function applyMedicalTreatment<T extends FitPlayer>(squad: T[]): T[] {
  return squad.map((p) => ({
    ...p,
    stamina: Math.min(100, p.stamina + MEDICAL_STAMINA_RECOVERY),
    availability: advanceInjury(p.availability),
  }))
}
