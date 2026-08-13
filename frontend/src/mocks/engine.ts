// Engine de simulação de partida — versão JS dos mocks.
// Função pura (gerador + RNG determinística) que produz MatchEvents minuto a minuto.
//
// IMPORTANTE: esta engine vive APENAS em src/mocks/ e existe só para alimentar
// o handler WebSocket de desenvolvimento. Quando o backend Kotlin tiver o
// MatchSimulator com Flow<MatchEvent>, esta engine é descartada. A app real
// (src/api/, src/pages/) nunca sabe que esta engine existe.

import type {
  Availability,
  Formation,
  MatchEvent,
  MatchStats,
  Player,
  Position,
  Posture,
  Retirement,
  TeamStats,
  TrainedAttribute,
  TrainingEvent,
  TrainingFocus,
} from '../domain/types'

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

export interface TeamTactics {
  posture?: Posture
  formation?: Formation
}

/**
 * Jogador em campo, do ponto de vista da engine: id (autoria dos eventos) e
 * posição (peso no sorteio de quem finaliza — issue #57). Antes bastava o id,
 * mas com o artilheiro ponderado a posição virou entrada obrigatória.
 */
export interface SquadMember {
  id: number
  pos: Position
}

export interface MatchSetup {
  matchId: number
  homeName: string
  awayName: string
  homeStrength: number   // média de overall (0–99)
  awayStrength: number
  homeSquad: SquadMember[]
  awaySquad: SquadMember[]
  // Tática de cada lado (issue #56). Opcionais: ausentes valem EQUILIBRADA em
  // 4-4-2, que é o eixo neutro e reproduz a engine anterior à tática.
  homeTactics?: TeamTactics
  awayTactics?: TeamTactics
}

const EVENT_RATE       = 0.12  // chance de algo acontecer por minuto
const P_GOAL_AT_EVENT  = 0.32
// Defesa e chute para fora dividem a fatia que era só da defesa (0.30) antes
// da issue #57 — cartão e lesão mantêm a frequência de sempre.
const P_SAVE_AT_EVENT  = 0.18
const P_MISS_AT_EVENT  = 0.12
const P_CARD_AT_EVENT  = 0.22
const P_INJURY_AT_EVENT =
  1.0 - P_GOAL_AT_EVENT - P_SAVE_AT_EVENT - P_MISS_AT_EVENT - P_CARD_AT_EVENT
const P_RED_CARD       = 0.12

// ─── Finalizador ponderado (issue #57) — espelha domain/model/Position.kt ───
// Peso relativo da posição no sorteio de quem chuta. Goleiro em 0 é literal:
// não é sorteado. A diferença entre atacante e zagueiro é de FREQUÊNCIA, não
// de permissão — daí pesos, e não uma lista de "quem pode chutar".
export const SCORING_WEIGHT: Record<Position, number> = {
  GK: 0,
  CB: 1,
  MF: 3,
  FW: 6,
}

/**
 * Roleta ponderada — espelha `drawShooter` de domain/rules/ScoringRules.kt.
 * Consome exatamente UM `next()`. `null` quando ninguém pode finalizar
 * (elenco vazio ou só goleiros): ausência no tipo, sem id inventado.
 */
export function drawShooter(squad: SquadMember[], rng: MulberryRng): SquadMember | null {
  const total = squad.reduce((sum, p) => sum + SCORING_WEIGHT[p.pos], 0)
  if (total <= 0) return null

  let ticket = rng.next() * total
  for (const p of squad) {
    ticket -= SCORING_WEIGHT[p.pos]
    if (ticket < 0) return p
  }
  return squad.filter((p) => SCORING_WEIGHT[p.pos] > 0).at(-1) ?? null
}

/** Goleiro escalado — o primeiro na posição, `null` em escalação sem GK. */
export const goalkeeperOf = (squad: SquadMember[]): SquadMember | null =>
  squad.find((p) => p.pos === 'GK') ?? null

// ─── Tática (issue #56) — espelha domain/model/Tactics.kt e Formation.kt ───
// `attack` escala a chance de gol do PRÓPRIO time; `defense`, a do adversário
// (< 1 = defende melhor). Ofensiva sobe as duas: jogo aberto faz e toma gol.
export const POSTURE_MODS: Record<Posture, { attack: number; defense: number }> = {
  DEFENSIVE: { attack: 0.82, defense: 0.88 },
  BALANCED:  { attack: 1.00, defense: 1.00 },
  OFFENSIVE: { attack: 1.20, defense: 1.12 },
}

// A formação inclina; a postura decide — por isso os desvios são menores.
export const FORMATION_MODS: Record<Formation, { attack: number; defense: number }> = {
  '4-4-2': { attack: 1.00, defense: 1.00 },
  '4-3-3': { attack: 1.08, defense: 1.04 },
  '3-5-2': { attack: 1.04, defense: 1.02 },
  '5-3-2': { attack: 0.92, defense: 0.94 },
}

const modsOf = (t: TeamTactics | undefined) => {
  const posture = POSTURE_MODS[t?.posture ?? 'BALANCED']
  const formation = FORMATION_MODS[t?.formation ?? '4-4-2']
  return {
    attack: posture.attack * formation.attack,
    defense: posture.defense * formation.defense,
  }
}

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
  // Súmula: tudo que já saiu. O FullTime resume o jogo a partir DAQUI
  // (issue #57), então estatísticas e feed nunca contam histórias diferentes.
  const sheet: MatchEvent[] = []
  function* emit(event: MatchEvent): Generator<MatchEvent> {
    sheet.push(event)
    yield event
  }

  yield* emit({
    type: 'KickOff',
    minute: 0,
    homeClubName: setup.homeName,
    awayClubName: setup.awayName,
    homeStrength: setup.homeStrength,
    awayStrength: setup.awayStrength,
    homePosture: setup.homeTactics?.posture ?? 'BALANCED',
    awayPosture: setup.awayTactics?.posture ?? 'BALANCED',
  })

  let homeGoals = 0
  let awayGoals = 0

  // Peso de gol de cada lado = quanto ELE ataca × quanto o adversário deixa
  // atacar. O que sai da chance de gol volta para a defesa, então cartão e
  // lesão mantêm a frequência de sempre e 1.0/1.0 reproduz a engine antiga.
  const homeMods = modsOf(setup.homeTactics)
  const awayMods = modsOf(setup.awayTactics)
  const homeGoalWeight = homeMods.attack * awayMods.defense
  const awayGoalWeight = awayMods.attack * homeMods.defense

  const goalP = Math.min(
    P_GOAL_AT_EVENT + P_SAVE_AT_EVENT,
    Math.max(0, (P_GOAL_AT_EVENT * (homeGoalWeight + awayGoalWeight)) / 2),
  )
  const saveP = P_GOAL_AT_EVENT + P_SAVE_AT_EVENT - goalP

  const homeShare = setup.homeStrength * homeGoalWeight
  const awayShare = setup.awayStrength * awayGoalWeight
  const homeRatio = homeShare + awayShare > 0 ? homeShare / (homeShare + awayShare) : 0.5

  // Suprime o aviso de unused parameter quando os squads estão vazios (defensivo)
  void P_INJURY_AT_EVENT

  for (let minute = 1; minute <= 90; minute++) {
    if (rng.next() >= EVENT_RATE) continue

    const roll = rng.next()
    if (roll < goalP) {
      const home = rng.next() < homeRatio
      const squad = home ? setup.homeSquad : setup.awaySquad
      // Autor ponderado por posição (issue #57): sem ninguém apto o lance
      // simplesmente não acontece.
      const scorer = drawShooter(squad, rng)
      if (!scorer) continue
      if (home) homeGoals++; else awayGoals++
      yield* emit({ type: 'Goal', minute, scorerId: scorer.id, home })
    } else if (roll < goalP + saveP) {
      // Defesa é o outro desfecho de uma finalização: quem chuta segue o mesmo
      // viés do gol e quem defende é o goleiro do lado oposto.
      const shooterIsHome = rng.next() < homeRatio
      const keeper = goalkeeperOf(shooterIsHome ? setup.awaySquad : setup.homeSquad)
      yield* emit({ type: 'Save', minute, goalkeeperId: keeper?.id ?? null, home: !shooterIsHome })
    } else if (roll < goalP + saveP + P_MISS_AT_EVENT) {
      const home = rng.next() < homeRatio
      const shooter = drawShooter(home ? setup.homeSquad : setup.awaySquad, rng)
      if (!shooter) continue
      yield* emit({ type: 'Miss', minute, playerId: shooter.id, home })
    } else if (roll < goalP + saveP + P_MISS_AT_EVENT + P_CARD_AT_EVENT) {
      const home = rng.next() < 0.5
      const squad = home ? setup.homeSquad : setup.awaySquad
      // Cartão e lesão seguem UNIFORMES: qualquer um leva uma entrada dura.
      const playerId = squad.length > 0 ? rng.pick(squad).id : 0
      yield* emit({ type: 'Card', minute, playerId, red: rng.next() < P_RED_CARD, home })
    } else {
      const home = rng.next() < 0.5
      const squad = home ? setup.homeSquad : setup.awaySquad
      const playerId = squad.length > 0 ? rng.pick(squad).id : 0
      // Duração sorteada com o RNG da própria partida (issue #54) — espelha
      // drawInjuryDuration do MatchSimulator.kt.
      const roundsOut = INJURY_DURATION_MIN
        + Math.floor(rng.next() * (INJURY_DURATION_MAX - INJURY_DURATION_MIN + 1))
      yield* emit({ type: 'Injury', minute, playerId, roundsOut })
    }
  }

  yield { type: 'FullTime', minute: 90, homeGoals, awayGoals, stats: matchStats(sheet) }
}

// ─── Sumário da partida (issue #57) — espelha domain/event/MatchStats.kt ───

const EMPTY_TEAM_STATS: TeamStats = {
  shots: 0,
  shotsOnTarget: 0,
  saves: 0,
  yellowCards: 0,
  redCards: 0,
}

export const EMPTY_MATCH_STATS: MatchStats = { home: EMPTY_TEAM_STATS, away: EMPTY_TEAM_STATS }

/**
 * Projeta o stream em estatísticas. `switch` exaustivo sobre o discriminated
 * union — variante nova de `MatchEvent` quebra o TS aqui, como o `when` do
 * sealed interface quebra o Kotlin.
 */
export function matchStats(events: MatchEvent[]): MatchStats {
  const onSide = (
    stats: MatchStats,
    home: boolean,
    update: (t: TeamStats) => TeamStats,
  ): MatchStats =>
    home ? { ...stats, home: update(stats.home) } : { ...stats, away: update(stats.away) }

  return events.reduce<MatchStats>((stats, event) => {
    switch (event.type) {
      case 'Goal':
        return onSide(stats, event.home, (t) => ({
          ...t,
          shots: t.shots + 1,
          shotsOnTarget: t.shotsOnTarget + 1,
        }))
      case 'Miss':
        return onSide(stats, event.home, (t) => ({ ...t, shots: t.shots + 1 }))
      // Uma defesa conta para os DOIS lados: defesa de quem pegou, finalização
      // no gol de quem chutou. `Save.home` é o lado do goleiro.
      case 'Save':
        return onSide(
          onSide(stats, event.home, (t) => ({ ...t, saves: t.saves + 1 })),
          !event.home,
          (t) => ({ ...t, shots: t.shots + 1, shotsOnTarget: t.shotsOnTarget + 1 }),
        )
      case 'Card':
        return onSide(stats, event.home, (t) =>
          event.red ? { ...t, redCards: t.redCards + 1 } : { ...t, yellowCards: t.yellowCards + 1 },
        )
      case 'KickOff':
      case 'Injury':
      case 'FullTime':
        return stats
    }
  }, EMPTY_MATCH_STATS)
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

// ─── Treino semanal (espelha TrainingRules.kt, issue #58) ─────────────────

/**
 * Efeitos de cada foco — mesmos números de `enum class TrainingFocus` no
 * backend. Nenhuma recuperação alcança o desgaste mínimo de um titular
 * (`STARTER_STAMINA_LOSS_MIN`): o treino alivia a fadiga, não a apaga.
 */
export const TRAINING_FOCUS_EFFECTS: Record<
  TrainingFocus,
  { trains: TrainedAttribute | null; staminaRecovery: number; injuryRisk: number }
> = {
  ATAQUE:   { trains: 'SHOOTING',  staminaRecovery: 0, injuryRisk: 0.04 },
  DEFESA:   { trains: 'DEFENDING', staminaRecovery: 0, injuryRisk: 0.04 },
  FISICO:   { trains: 'PACE',      staminaRecovery: 5, injuryRisk: 0.06 },
  DESCANSO: { trains: null,        staminaRecovery: 8, injuryRisk: 0 },
}

/** Chance de +1 por faixa etária — espelha `AgeBand.trainingUpgradeChance`. */
export const TRAINING_UPGRADE_CHANCE: Record<AgeBand, number> = {
  YOUNG: 0.25,
  PEAK: 0.12,
  VETERAN: 0.05,
}

export const TRAINING_ATTRIBUTE_GAIN = 1
export const TRAINING_INJURY_ROUNDS = 1

/**
 * Uma semana de treino no foco escolhido — espelha `train()` do backend.
 * Lesionado não treina (a semana dele é de recuperação) e o contador de
 * lesão não é tocado aqui: quem o faz andar é `applyRoundFitness`.
 *
 * Consome DOIS sorteios por jogador apto, sempre na mesma ordem (evolução,
 * depois lesão), para que a sequência dependa só do elenco e do foco.
 */
export function trainSquad(
  squad: Player[],
  focus: TrainingFocus,
  rng: MulberryRng,
): { squad: Player[]; events: TrainingEvent[] } {
  const effect = TRAINING_FOCUS_EFFECTS[focus]
  const events: TrainingEvent[] = []

  const next = squad.map((p) => {
    const rested: Player = {
      ...p,
      stamina: Math.min(100, p.stamina + effect.staminaRecovery),
    }
    if (effect.trains === null || p.availability.type === 'Injured') return rested

    const improves = rng.next() < TRAINING_UPGRADE_CHANCE[ageBandOf(p.age)]
    const hurts = rng.next() < effect.injuryRisk

    let result = rested
    if (improves) {
      const key = attributeKey(effect.trains)
      const better = improve(result, effect.trains)
      // Jogador no teto do invariante não "evolui": nada mudou.
      if (better.overall !== result.overall || better[key] !== result[key]) {
        result = better
        events.push({ type: 'Improved', player: better, attribute: effect.trains })
      }
    }
    if (hurts) {
      result = { ...result, availability: { type: 'Injured', roundsOut: TRAINING_INJURY_ROUNDS } }
      events.push({ type: 'Injured', player: result, roundsOut: TRAINING_INJURY_ROUNDS })
    }
    return result
  })

  return { squad: next, events }
}

const attributeKey = (a: TrainedAttribute): 'shooting' | 'defending' | 'pace' =>
  a === 'SHOOTING' ? 'shooting' : a === 'DEFENDING' ? 'defending' : 'pace'

/**
 * O ganho move o atributo do foco E o `overall` — é o `overall` que decide a
 * força do time, então um ganho que não o tocasse seria decorativo (mesma
 * decisão do backend). `star` é derivado lá; aqui o mock recalcula.
 */
function improve(p: Player, attribute: TrainedAttribute): Player {
  const key = attributeKey(attribute)
  const overall = grow(p.overall)
  return { ...p, [key]: grow(p[key]), overall, star: overall >= 82 }
}

const grow = (value: number): number => Math.min(99, value + TRAINING_ATTRIBUTE_GAIN)
