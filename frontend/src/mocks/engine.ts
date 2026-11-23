// Engine de simulação de partida — versão JS dos mocks.
// Função pura (gerador + RNG determinística) que produz MatchEvents minuto a minuto.
//
// IMPORTANTE: esta engine vive APENAS em src/mocks/ e existe só para alimentar
// o handler WebSocket de desenvolvimento. Quando o backend Kotlin tiver o
// MatchSimulator com Flow<MatchEvent>, esta engine é descartada. A app real
// (src/api/, src/pages/) nunca sabe que esta engine existe.

import type {
  Availability,
  Contract,
  ContractOffer,
  Formation,
  Lineup,
  MatchEvent,
  MatchStats,
  Player,
  Position,
  PlayerRefused,
  Posture,
  RefusalReason,
  Retirement,
  TeamStats,
  TrainedAttribute,
  TrainingEvent,
  TrainingFocus,
} from '../domain/types'
// A faixa de preço do ingresso (issue #59) NÃO é redefinida aqui: é a mesma
// que o formulário da Dashboard usa para validar antes de mandar. Duas cópias
// com o mesmo nome deixariam o mock aceitando um preço que o app bloqueia (ou
// vice-versa) sem nenhum teste reclamar. A direção do import é a permitida —
// o mock conhece a app, a app nunca importa de `mocks/`.
import { MIN_TICKET_PRICE_CENTS, MAX_TICKET_PRICE_CENTS } from '../api/queries/useSetTicketPrice'

export { MIN_TICKET_PRICE_CENTS, MAX_TICKET_PRICE_CENTS }

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
  // Mata-mata (issues #61 e #131): o placar dos 90' vai aos pênaltis? Jogo
  // único empatado sim, ida nunca, volta só com o agregado empatado — espelha
  // `MatchSetup.decidesOnPenalties`. Ausente = partida de liga, que aceita
  // empate e consome o RNG como antes.
  decidesOnPenalties?: (homeGoals: number, awayGoals: number) => boolean
  // Ids dos clubes, só repassados ao KickOff (o drill-down resolve os nomes
  // dos jogadores por eles). Ausentes = não vão no evento, como no contrato.
  homeClubId?: number
  awayClubId?: number
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
export const MEDICAL_DEPARTMENT_COST_CENTS = 6_000_00
export const MEDICAL_STAMINA_RECOVERY = 30

// ─── Ampliação do estádio (issue #79) — espelham domain/rules/StadiumRules.kt ───
export const STADIUM_SEAT_COST_CENTS = 20_00
export const MIN_STADIUM_EXPANSION_SEATS = 1_000

// ─── Valor de mercado — espelha domain/rules/ValuationRules.kt (issue #135) ───
// valor = base(overall) × idade × contrato × liga, na escala da economia do
// jogo (70 no auge ≈ US$ 25 mil). Simplificação do mock: o deságio por
// contrato restante (#136) fica de fora — só overall, idade e liga entram.
export const MARKET_REFERENCE_STRENGTH = 70
const BASE_VALUE_AT_REFERENCE_CENTS = 25_000_00
const OVERALL_DOUBLING_POINTS = 4
const PEAK_VALUE_AGE_MIN = 24
const PEAK_VALUE_AGE_MAX = 27
const YOUTH_FACTOR_PER_YEAR = 0.93
const VETERAN_FACTOR_PER_YEAR = 0.82
const LEAGUE_DOUBLING_POINTS = 16
const LEAGUE_FACTOR_MIN = 0.5
const LEAGUE_FACTOR_MAX = 1.8
const MIN_MARKET_VALUE_CENTS = 100_00
const VALUE_ROUNDING_CENTS = 10_00

const ageValueFactor = (age: number): number =>
  age < PEAK_VALUE_AGE_MIN
    ? YOUTH_FACTOR_PER_YEAR ** (PEAK_VALUE_AGE_MIN - age)
    : age > PEAK_VALUE_AGE_MAX
      ? VETERAN_FACTOR_PER_YEAR ** (age - PEAK_VALUE_AGE_MAX)
      : 1

const leagueValueFactor = (strength: number): number =>
  Math.min(LEAGUE_FACTOR_MAX, Math.max(LEAGUE_FACTOR_MIN, 2 ** ((strength - MARKET_REFERENCE_STRENGTH) / LEAGUE_DOUBLING_POINTS)))

/** Valor de mercado em centavos; sem liga, o de referência do mercado. */
export function marketValueOf(
  player: Pick<Player, 'overall' | 'age'>,
  leagueStrength: number = MARKET_REFERENCE_STRENGTH,
): number {
  const raw =
    BASE_VALUE_AT_REFERENCE_CENTS *
    2 ** ((player.overall - 70) / OVERALL_DOUBLING_POINTS) *
    ageValueFactor(player.age) *
    leagueValueFactor(leagueStrength)
  return Math.max(MIN_MARKET_VALUE_CENTS, Math.round(raw / VALUE_ROUNDING_CENTS) * VALUE_ROUNDING_CENTS)
}

// ─── Mercado — espelham domain/rules/TransferRules.kt ───
// A venda paga 60% do valor no clube; o anúncio cobra 125% do valor de
// referência. Como o multiplicador de liga tem teto 1,8 (60% × 1,8 < 125%),
// comprar e revender não gera dinheiro, de liga nenhuma.
export const SALE_PAYOUT_PERCENT = 60
export const MARKET_LISTING_PERCENT = 125
export const salePayoutOf = (squadValue: number): number => Math.trunc((squadValue * SALE_PAYOUT_PERCENT) / 100)
export const marketListingPriceOf = (referenceValue: number): number =>
  Math.trunc((referenceValue * MARKET_LISTING_PERCENT) / 100)

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
    ...(setup.homeClubId !== undefined && { homeClubId: setup.homeClubId }),
    ...(setup.awayClubId !== undefined && { awayClubId: setup.awayClubId }),
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

  // Quem se lesiona ou leva vermelho deixa o campo — espelha `offPitch` do
  // MatchSimulator.kt: não participa de mais nenhum lance nem dos pênaltis.
  // A força do time continua a do apito inicial.
  const offPitch = new Set<number>()
  const onPitch = (home: boolean) =>
    (home ? setup.homeSquad : setup.awaySquad).filter((p) => !offPitch.has(p.id))

  for (let minute = 1; minute <= 90; minute++) {
    if (rng.next() >= EVENT_RATE) continue

    const roll = rng.next()
    if (roll < goalP) {
      const home = rng.next() < homeRatio
      const squad = onPitch(home)
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
      const keeper = goalkeeperOf(onPitch(!shooterIsHome))
      yield* emit({ type: 'Save', minute, goalkeeperId: keeper?.id ?? null, home: !shooterIsHome })
    } else if (roll < goalP + saveP + P_MISS_AT_EVENT) {
      const home = rng.next() < homeRatio
      const shooter = drawShooter(onPitch(home), rng)
      if (!shooter) continue
      yield* emit({ type: 'Miss', minute, playerId: shooter.id, home })
    } else if (roll < goalP + saveP + P_MISS_AT_EVENT + P_CARD_AT_EVENT) {
      const home = rng.next() < 0.5
      const squad = onPitch(home)
      // Cartão e lesão seguem UNIFORMES: qualquer um leva uma entrada dura.
      if (squad.length === 0) continue
      const playerId = rng.pick(squad).id
      const red = rng.next() < P_RED_CARD
      if (red) offPitch.add(playerId)
      yield* emit({ type: 'Card', minute, playerId, red, home })
    } else {
      const home = rng.next() < 0.5
      const squad = onPitch(home)
      if (squad.length === 0) continue
      const playerId = rng.pick(squad).id
      offPitch.add(playerId)
      // Duração sorteada com o RNG da própria partida (issue #54) — espelha
      // drawInjuryDuration do MatchSimulator.kt.
      const roundsOut = INJURY_DURATION_MIN
        + Math.floor(rng.next() * (INJURY_DURATION_MAX - INJURY_DURATION_MIN + 1))
      yield* emit({ type: 'Injury', minute, playerId, roundsOut })
    }
  }

  // Mata-mata decidido no empate (jogo único, ou agregado da volta): a disputa
  // usa o MESMO RNG, depois dos 90' — o tempo normal fica idêntico ao de liga.
  if (setup.decidesOnPenalties?.(homeGoals, awayGoals)) {
    const shootout = penaltyShootout(
      onPitch(true), onPitch(false), setup.homeStrength, setup.awayStrength, rng,
    )
    yield* emit({ type: 'PenaltyShootout', minute: 90, ...shootout })
  }

  yield { type: 'FullTime', minute: 90, homeGoals, awayGoals, stats: matchStats(sheet) }
}

// ─── Pênaltis (issue #61) — espelha domain/rules/PenaltyRules.kt ──────────

export const PENALTY_SERIES = 5
export const PENALTY_CONVERSION_MIN = 0.55
export const PENALTY_CONVERSION_MAX = 0.92
const PENALTY_BASE_CONVERSION = 0.75
const PENALTY_SKILL_WEIGHT = 0.005
const MAX_SUDDEN_DEATH_PAIRS = 1_000

type PenaltyKick = Extract<MatchEvent, { type: 'PenaltyShootout' }>['kicks'][number]

export interface Shootout {
  homeScore: number
  awayScore: number
  kicks: PenaltyKick[]
}

/** Quem mais finaliza bate primeiro; goleiro (peso 0) por último; id desempata. */
export const penaltyTakers = (squad: SquadMember[]): SquadMember[] =>
  [...squad].sort((a, b) => SCORING_WEIGHT[b.pos] - SCORING_WEIGHT[a.pos] || a.id - b.id)

/**
 * Chance de conversão. O mock não tem o overall de cada jogador da IA, então
 * usa a força média de quem bate contra a de quem defende — mesma fórmula do
 * backend, com o time no lugar do indivíduo.
 */
export const penaltyConversion = (takerStrength: number, keeperStrength: number): number =>
  Math.min(
    PENALTY_CONVERSION_MAX,
    Math.max(PENALTY_CONVERSION_MIN, PENALTY_BASE_CONVERSION + (takerStrength - keeperStrength) * PENALTY_SKILL_WEIGHT),
  )

/**
 * Série de 5 alternada (mandante primeiro), encerrada quando um lado não
 * alcança mais o outro, depois morte súbita. Sempre devolve vencedor; lado sem
 * ninguém perde as cobranças, e sem ninguém dos dois lados a vaga é sorteada.
 */
export function penaltyShootout(
  home: SquadMember[],
  away: SquadMember[],
  homeStrength: number,
  awayStrength: number,
  rng: MulberryRng,
): Shootout {
  const homeTakers = penaltyTakers(home)
  const awayTakers = penaltyTakers(away)
  if (homeTakers.length === 0 && awayTakers.length === 0) {
    const homeWins = rng.next() < 0.5
    return { homeScore: homeWins ? 1 : 0, awayScore: homeWins ? 0 : 1, kicks: [] }
  }

  const kicks: PenaltyKick[] = []
  const score = { home: 0, away: 0 }
  const taken = { home: 0, away: 0 }

  const kick = (isHome: boolean) => {
    const side = isHome ? 'home' : 'away'
    const takers = isHome ? homeTakers : awayTakers
    const taker = takers.length > 0 ? takers[taken[side] % takers.length]! : null
    const chance = isHome
      ? penaltyConversion(homeStrength, awayStrength)
      : penaltyConversion(awayStrength, homeStrength)
    const scored = taker != null && rng.next() < chance
    kicks.push({ playerId: taker?.id ?? null, home: isHome, scored })
    taken[side]++
    if (scored) score[side]++
  }

  const decidedInSeries = () =>
    score.home > score.away + (PENALTY_SERIES - taken.away) ||
    score.away > score.home + (PENALTY_SERIES - taken.home)

  for (let i = 0; i < PENALTY_SERIES; i++) {
    kick(true)
    if (decidedInSeries()) break
    kick(false)
    if (decidedInSeries()) break
  }

  let pairs = 0
  while (score.home === score.away) {
    if (++pairs > MAX_SUDDEN_DEATH_PAIRS) throw new Error('Morte súbita sem fim')
    kick(true)
    kick(false)
  }

  return { homeScore: score.home, awayScore: score.away, kicks }
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
      // Pênaltis não entram nas finalizações (issue #61): o sumário é dos 90'.
      case 'KickOff':
      case 'Injury':
      case 'PenaltyShootout':
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
 *
 * Lesão conta JOGOS DO CLUBE (issue #125): no encaixe em que o clube não
 * joga, o elenco descansa mas a lesão fica parada (`clubPlayed = false`).
 */
export function applyRoundFitness<T extends FitPlayer>(
  squad: T[],
  starterIds: Set<number>,
  rng: MulberryRng,
  newInjuries: Map<number, number> = new Map(),
  clubPlayed = true,
): T[] {
  return squad.map((p) => {
    const stamina = starterIds.has(p.id)
      ? Math.max(
          STAMINA_MATCH_FLOOR,
          p.stamina - (STARTER_STAMINA_LOSS_MIN
            + Math.floor(rng.next() * (STARTER_STAMINA_LOSS_MAX - STARTER_STAMINA_LOSS_MIN + 1))),
        )
      : Math.min(100, p.stamina + BENCH_STAMINA_RECOVERY)

    const advanced = clubPlayed ? advanceInjury(p.availability) : p.availability
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
  rules?: LineupRules<T>,
): Set<number> {
  const available = squad.filter((p) => p.availability.type !== 'Injured' && !(rules?.suspended(p) ?? false))
  const byId = new Map(available.map((p) => [p.id, p]))
  const starters = (savedPlayerIds ?? []).filter((id) => byId.has(id))
  const substitutes = available.filter((p) => !starters.includes(p.id)).map((p) => p.id)
  const candidates = [...starters, ...substitutes]
  // Limite de estrangeiros (issue #130): pula o estrangeiro que estouraria o
  // limite, na ordem — titulares salvos primeiro, como `Club.startingLineup`.
  const limit = rules?.foreignLimit
  let foreigners = 0
  const kept = limit === undefined || !rules
    ? candidates
    : candidates.filter((id) => {
        const p = byId.get(id)
        if (!p || !rules.foreign(p)) return true
        if (foreigners >= limit) return false
        foreigners += 1
        return true
      })
  return new Set(kept.slice(0, 11))
}

// ─── Suspensões e estrangeiros (espelha SuspensionRules.kt, issue #130) ───

/** Regras da competição que o apito inicial aplica além da lesão. */
export interface LineupRules<T> {
  suspended: (p: T) => boolean
  foreign: (p: T) => boolean
  /** Ausente = sem limite. */
  foreignLimit?: number
}

/** Limite da CBF para o Brasileirão 2026 — o `NATIONAL_FOREIGN_PLAYER_LIMIT` do backend. */
export const NATIONAL_FOREIGN_PLAYER_LIMIT = 9
export const YELLOWS_FOR_SUSPENSION = 3
export const MATCHES_PER_RED_CARD = 1

/** Estrangeiro no país [countryCode]: nenhuma das nacionalidades é dele. */
export const isForeignAt = (p: Pick<Player, 'nationality' | 'secondNationality'>, countryCode: string): boolean =>
  p.nationality.code !== countryCode && p.secondNationality?.code !== countryCode

export const isSuspendedIn = (p: Pick<Player, 'suspensions'>, competitionId: string): boolean =>
  p.suspensions.some((s) => s.competitionId === competitionId)

/** Amarelos pendentes por jogador → por competição (o contrato não os expõe). */
export type PendingYellows = Record<number, Record<string, number>>

/**
 * Efeito disciplinar de UMA partida de [competitionId] sobre o elenco do clube
 * que a jogou: suspenso cumpre um jogo; expulso (vermelho ou 2 amarelos) pega
 * gancho; o 3º amarelo acumulado vira 1 jogo e zera. Mesma ordem de
 * `Player.afterMatchIn` no Kotlin.
 */
export function applyMatchDiscipline(
  squad: Player[],
  competitionId: string,
  competitionName: string,
  cards: Map<number, { yellow: number; red: number }>,
  pending: PendingYellows,
): { squad: Player[]; pending: PendingYellows } {
  const nextPending: PendingYellows = { ...pending }
  const suspend = (p: Player, matches: number): Player => ({
    ...p,
    suspensions: [
      ...p.suspensions.filter((s) => s.competitionId !== competitionId),
      { competitionId, competitionName, matchesLeft: matches },
    ],
  })
  const next = squad.map((p): Player => {
    const serving = p.suspensions.find((s) => s.competitionId === competitionId)
    if (serving) {
      const others = p.suspensions.filter((s) => s !== serving)
      const rest = serving.matchesLeft - 1
      return { ...p, suspensions: rest >= 1 ? [...others, { ...serving, matchesLeft: rest }] : others }
    }
    const c = cards.get(p.id) ?? { yellow: 0, red: 0 }
    if (c.red >= 1 || c.yellow >= 2) return suspend(p, MATCHES_PER_RED_CARD)
    if (c.yellow === 0) return p
    const mine = { ...(nextPending[p.id] ?? {}) }
    const total = (mine[competitionId] ?? 0) + c.yellow
    if (total >= YELLOWS_FOR_SUSPENSION) {
      delete mine[competitionId]
      nextPending[p.id] = mine
      return suspend(p, 1)
    }
    mine[competitionId] = total
    nextPending[p.id] = mine
    return p
  })
  return { squad: next, pending: nextPending }
}

/** Campos derivados da escalação que o schema `Club` exige (issue #87). */
export interface EffectiveLineupFields {
  effectiveLineup: Lineup
  lineupIsDefault: boolean
  lineupAdjusted: boolean
}

/**
 * Escalação EFETIVA do clube — espelha o mapeamento `Club.toDto()` do backend
 * (issue #87): `startingEleven` decide QUEM entra (a mesma regra de
 * `Club.startingLineup()`), e a formação/postura vêm da salva ou do 4-4-2
 * EQUILIBRADO automático. Único lugar do front que deriva titulares — a
 * `LineupPage` só lê `effectiveLineup`.
 */
export function effectiveLineupOf<T extends FitPlayer>(
  squad: T[],
  saved: Lineup | undefined,
  rules?: LineupRules<T>,
): EffectiveLineupFields {
  const playerIds = [...startingEleven(squad, saved?.playerIds, rules)]
  const effectiveLineup: Lineup = {
    formation: saved?.formation ?? '4-4-2',
    playerIds,
    posture: saved?.posture ?? 'BALANCED',
  }
  const sameIds = saved != null
    && saved.playerIds.length === playerIds.length
    && saved.playerIds.every((id, i) => id === playerIds[i])
  return {
    effectiveLineup,
    lineupIsDefault: saved == null,
    lineupAdjusted: saved != null && !sameIds,
  }
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

// Base (issue #139): de 2 a 4 garotos de 16 a 18 anos por clube e temporada.
export const YOUTH_INTAKE_MIN = 2
export const YOUTH_INTAKE_MAX = 4
export const YOUTH_AGE_MIN = 16
export const YOUTH_AGE_MAX = 18
/** Idade do auge em que o potencial do garoto é medido (`POTENTIAL_AGE` no backend). */
export const POTENTIAL_AGE = 24
export const YOUTH_SALARY_CENTS = 600_00

const shift = (value: number, delta: number): number => Math.min(99, Math.max(0, value + delta))

const retires = (p: { age: number; overall: number }): boolean =>
  p.age >= FORCED_RETIREMENT_AGE ||
  (p.age >= RETIREMENT_MIN_AGE && p.overall < RETIREMENT_MAX_OVERALL)

const drawInt = (rng: MulberryRng, min: number, max: number): number =>
  min + Math.floor(rng.next() * (max - min + 1))

/**
 * Envelhece o elenco UMA temporada e tira quem se aposentou. Desde a issue
 * #139 a base não repõe 1:1: os garotos sobem à parte, em `youthIntake`, e a
 * IA segura o tamanho do elenco dispensando quem vence (`contractTurnover`).
 *
 * O backend modela o desfecho de cada jogador num `sealed interface
 * AgingOutcome` (evoluiu/estagnou/regrediu/aposentou); aqui o mock só precisa
 * do elenco resultante e da lista de aposentadorias.
 */
export function ageSquadOneSeason(
  squad: Player[],
  rng: MulberryRng,
  clubId = 1,
): { squad: Player[]; retirements: Retirement[] } {
  const next: Player[] = []
  const retirements: Retirement[] = []

  for (const p of squad) {
    // Quem já bateu o teto de carreira sai sem sortear delta nenhum — mas ganha
    // o ano, como no backend, para o extrato mostrar a idade em que parou.
    if (p.age >= FORCED_RETIREMENT_AGE) {
      retirements.push({ clubId, retired: { ...p, age: Math.min(50, p.age + 1) } })
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

    if (retires(aged)) retirements.push({ clubId, retired: aged })
    else next.push(aged)
  }

  return { squad: next, retirements }
}

/** Proporção ideal de cada posição no elenco (`IDEAL_SQUAD_SHARE` no backend). */
const IDEAL_SQUAD_SHARE: Record<Position, number> = { GK: 3 / 25, CB: 8 / 25, MF: 8 / 25, FW: 6 / 25 }

/** A posição de maior déficit, contando quem vai entrar (empate: goleiro primeiro). */
export function neediestPosition(squad: readonly Pick<Player, 'position'>[]): Position {
  const size = squad.length + 1
  const deficit = (position: Position) =>
    IDEAL_SQUAD_SHARE[position] * size - squad.filter((p) => p.position === position).length
  return (['GK', 'CB', 'MF', 'FW'] as const).reduce((best, p) => (deficit(p) > deficit(best) ? p : best))
}

/**
 * Garotos da base de UM clube na virada (issue #139), espelho simplificado de
 * `youthIntake` do backend: de 2 a 4, na posição mais carente, com o overall do
 * potencial (média dos 11 melhores) menos o que o envelhecimento ainda vai
 * devolver (+1 por ano até os 24). O nome vem de [nameFor] (as listas por país
 * de `mocks/world.ts`, que importa deste módulo e por isso entra por parâmetro).
 */
export function youthIntake(
  squad: Player[],
  rng: MulberryRng,
  idFor: (slot: number) => number,
  contract: Contract | undefined,
  nationality: Player['nationality'],
  nameFor: (rng: MulberryRng) => string,
  maxSquadSize?: number,
): Player[] {
  const drawn = drawInt(rng, YOUTH_INTAKE_MIN, YOUTH_INTAKE_MAX)
  const count = maxSquadSize === undefined ? drawn : Math.min(drawn, Math.max(0, maxSquadSize - squad.length))
  const best = squad.map((p) => p.overall).sort((a, b) => b - a).slice(0, 11)
  const potential = best.length === 0 ? 50 : Math.round(best.reduce((a, b) => a + b, 0) / best.length)
  const youths: Player[] = []
  for (let slot = 1; slot <= count; slot++) {
    const age = drawInt(rng, YOUTH_AGE_MIN, YOUTH_AGE_MAX)
    const overall = Math.max(30, Math.min(99, potential - (POTENTIAL_AGE - age) + drawInt(rng, -4, 4)))
    const name = nameFor(rng)
    youths.push({
      id: idFor(slot),
      name,
      position: neediestPosition([...squad, ...youths]),
      overall,
      pace: overall,
      shooting: overall,
      passing: overall,
      defending: overall,
      stamina: 100,
      salary: YOUTH_SALARY_CENTS,
      age,
      goals: 0,
      yellowCards: 0,
      redCards: 0,
      availability: { type: 'Available' },
      suspensions: [],
      star: overall >= 82,
      marketValue: 0,
      nationality,
      ...(contract ? { contract } : {}),
    })
  }
  return youths
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
 * backend. A recuperação fica entre 1 e `STARTER_STAMINA_LOSS_MIN - 1`: nunca
 * apaga o desgaste da partida (senão a fadiga da issue #54 sumia), mas
 * também nunca é zero — com zero o titular ficava preso no piso de partida
 * enquanto a IA se estabilizava acima dele.
 */
export const TRAINING_FOCUS_EFFECTS: Record<
  TrainingFocus,
  { trains: TrainedAttribute | null; staminaRecovery: number; injuryRisk: number }
> = {
  ATAQUE:   { trains: 'SHOOTING',  staminaRecovery: 3, injuryRisk: 0.04 },
  DEFESA:   { trains: 'DEFENDING', staminaRecovery: 3, injuryRisk: 0.04 },
  FISICO:   { trains: 'PACE',      staminaRecovery: 6, injuryRisk: 0.06 },
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
  const weeks = squad.map((p) => trainOneWeek(p, focus, rng))
  return {
    squad: weeks.map((w) => w.player),
    events: weeks.flatMap((w) => w.events),
  }
}

/**
 * A semana de UM jogador. Os eventos são montados no fim, a partir do estado
 * FINAL — quem evolui e se machuca na mesma semana gera dois eventos que
 * concordam entre si (mesma correção do backend).
 */
function trainOneWeek(
  p: Player,
  focus: TrainingFocus,
  rng: MulberryRng,
): { player: Player; events: TrainingEvent[] } {
  const effect = TRAINING_FOCUS_EFFECTS[focus]
  const rested: Player = { ...p, stamina: Math.min(100, p.stamina + effect.staminaRecovery) }
  if (effect.trains === null || p.availability.type === 'Injured') {
    return { player: rested, events: [] }
  }

  const improves = rng.next() < TRAINING_UPGRADE_CHANCE[ageBandOf(p.age)]
  const hurts = rng.next() < effect.injuryRisk

  const better = improves ? improve(rested, effect.trains) : rested
  // Jogador no teto do invariante não "evolui": nada mudou.
  const key = ATTRIBUTE_KEY[effect.trains]
  const evolved = better.overall !== rested.overall || better[key] !== rested[key]
  const player: Player = hurts
    ? { ...better, availability: { type: 'Injured', roundsOut: TRAINING_INJURY_ROUNDS } }
    : better

  const events: TrainingEvent[] = []
  if (evolved) events.push({ type: 'Improved', player, attribute: effect.trains })
  if (hurts) events.push({ type: 'Injured', player, roundsOut: TRAINING_INJURY_ROUNDS })
  return { player, events }
}

/**
 * Atributo do contrato → campo do `Player`. `Record` e não ternário aninhado:
 * um `TrainedAttribute` novo vira erro de tipo aqui, do mesmo jeito que o
 * `when` exaustivo do Kotlin cobra o caso novo em `TrainingRules.improve`.
 */
const ATTRIBUTE_KEY: Record<TrainedAttribute, 'shooting' | 'defending' | 'pace'> = {
  SHOOTING: 'shooting',
  DEFENDING: 'defending',
  PACE: 'pace',
}

/**
 * O ganho move o atributo do foco E o `overall` — é o `overall` que decide a
 * força do time, então um ganho que não o tocasse seria decorativo (mesma
 * decisão do backend). `star` é derivado lá; aqui o mock recalcula.
 */
function improve(p: Player, attribute: TrainedAttribute): Player {
  const key = ATTRIBUTE_KEY[attribute]
  const overall = grow(p.overall)
  return { ...p, [key]: grow(p[key]), overall, star: overall >= 82 }
}

const grow = (value: number): number => Math.min(99, value + TRAINING_ATTRIBUTE_GAIN)

// ─── Bilheteria (espelha FinanceRules.kt, issues #4 e #59) ────────────────
//
// Estas regras viviam soltas em handlers.ts. Com a curva de demanda da issue
// #59 elas passaram a ser regra de simulação de verdade — e regra de mock vive
// em engine.ts (CLAUDE.md), onde dá para testá-la sem subir um handler.

export const DEFAULT_TICKET_PRICE_CENTS = 10_00
export const SALARY_EVERY_N_ROUNDS = 2

const STRENGTH_FLOOR = 60
const STRENGTH_CEILING = 100
const FAIR_PRICE_AT_FLOOR = 8_00
const FAIR_PRICE_AT_CEILING = 12_00
const PRICE_SENSITIVITY = 0.5
const CHEAP_TICKET_BOOST = 0.5

const strengthRatio = (strength: number): number =>
  Math.min(1, Math.max(0, (strength - STRENGTH_FLOOR) / (STRENGTH_CEILING - STRENGTH_FLOOR)))

/** Ocupação no preço justo: 50% (time fraco) a 100% (time forte). */
export const baseAttendanceRate = (strength: number): number => 0.5 + 0.5 * strengthRatio(strength)

/** Preço que a torcida deste time considera justo — sobe com a força. */
export const fairTicketPriceCents = (strength: number): number =>
  FAIR_PRICE_AT_FLOOR +
  Math.trunc((FAIR_PRICE_AT_CEILING - FAIR_PRICE_AT_FLOOR) * strengthRatio(strength))

/**
 * Multiplicador de demanda do preço relativo ao justo: 1.0 no preço justo,
 * até 1.5 quando o ingresso tende a zero, e decaindo (sem nunca zerar) quando
 * o técnico cobra caro.
 */
export function ticketPriceDemandFactor(priceCents: number, strength: number): number {
  const ratio = priceCents / fairTicketPriceCents(strength)
  if (ratio <= 1) return 1 + CHEAP_TICKET_BOOST * (1 - ratio)
  const excess = ratio - 1
  return 1 / (1 + PRICE_SENSITIVITY * excess * excess)
}

/** Ocupação do estádio: base da força corrigida pelo preço, com teto em 100%. */
export const attendanceRate = (strength: number, priceCents: number): number =>
  Math.min(1, baseAttendanceRate(strength) * ticketPriceDemandFactor(priceCents, strength))

/** Público pagante = capacidade × ocupação. */
export const attendanceOf = (capacity: number, strength: number, priceCents: number): number =>
  Math.trunc(capacity * attendanceRate(strength, priceCents))

/**
 * Bilheteria do mandante — público e receita da MESMA conta, espelhando
 * `gate()` do backend. Quem precisa dos dois números chama isto: pedir público
 * e receita em duas chamadas recalcularia a ocupação e abriria espaço para as
 * duas respostas divergirem.
 */
export const gateOf = (
  capacity: number,
  strength: number,
  priceCents: number,
): { attendance: number; revenue: number } => {
  const crowd = attendanceOf(capacity, strength, priceCents)
  return { attendance: crowd, revenue: crowd * priceCents }
}

/** Só a receita de [gateOf] — atalho para quem não precisa do público. */
export const ticketRevenueOf = (capacity: number, strength: number, priceCents: number): number =>
  gateOf(capacity, strength, priceCents).revenue

/** O preço está na faixa que o técnico pode praticar? */
export const isTicketPriceAllowed = (priceCents: number): boolean =>
  Number.isInteger(priceCents) &&
  priceCents >= MIN_TICKET_PRICE_CENTS &&
  priceCents <= MAX_TICKET_PRICE_CENTS

// ─── Contratos — espelham domain/rules/ContractRules.kt (issue #136) ───
// O mock só tem a régua do ano civil (31/12) para o Brasil e a de agosto–maio
// (31/05 do ano seguinte) para os clubes europeus; o backend tira a janela da
// confederação do país do clube. Datas são strings ISO (`YYYY-MM-DD`), que
// comparam certo como texto.

export const CONTRACT_SEASONS_MAX = 5
export const MAX_MINOR_CONTRACT_SEASONS = 3
export const ADULT_AGE = 18

/** Contrato de quem sobe da base: o máximo permitido ao menor. */
export const YOUTH_CONTRACT_SEASONS = MAX_MINOR_CONTRACT_SEASONS

const UEFA_CODES = new Set(['ENG', 'ESP', 'ITA', 'GER', 'FRA', 'POR'])

/** Último dia da temporada [season] da liga de [countryCode]. */
export const seasonEndOf = (season: number, countryCode: string): string =>
  UEFA_CODES.has(countryCode) ? `${season + 1}-05-31` : `${season}-12-31`

/** Primeiro dia da temporada [season] da liga de [countryCode]. */
export const seasonStartOf = (season: number, countryCode: string): string =>
  UEFA_CODES.has(countryCode) ? `${season}-08-01` : `${season}-01-01`

/** Temporadas permitidas para [age] anos: 1–5, ou 1–3 para menor de 18. */
export const maxContractSeasons = (age: number): number =>
  age < ADULT_AGE ? MAX_MINOR_CONTRACT_SEASONS : CONTRACT_SEASONS_MAX

/** Duração típica pela idade (jovem assina longo, veterano curto), dentro da regra. */
export function typicalContractSeasons(age: number, rng: MulberryRng): number {
  const [min, max] =
    age < ADULT_AGE ? [2, 3] : age <= 23 ? [2, 5] : age <= 29 ? [1, 4] : age <= 32 ? [1, 3] : [1, 2]
  return Math.min(drawInt(rng, min, max), maxContractSeasons(age))
}

/** Contrato que termina no fim da temporada `season + seasons - 1` da liga do clube. */
export const contractFor = (
  start: string,
  season: number,
  seasons: number,
  countryCode: string,
): Contract => ({ start, end: seasonEndOf(season + seasons - 1, countryCode), finalSeason: false })

/** Última temporada do contrato em [today]: passou do término menos um ano. */
export const isInFinalSeason = (contract: Pick<Contract, 'end'>, today: string): boolean => {
  const year = Number(contract.end.slice(0, 4))
  return today > `${year - 1}${contract.end.slice(4)}`
}

/** O jogador sem clube: sem contrato e sem salário. */
export const releasedPlayer = (player: Player): Player => {
  // O "livre" do contrato é a AUSÊNCIA de `contract`, não um
  // `contract: undefined`: copia e apaga o campo (opcional no tipo).
  const free: Player = { ...player, salary: 0 }
  delete free.contract
  return free
}

/**
 * Salário mensal de quem vale [value] — espelha `salaryForValue` de
 * domain/rules/ContractRules.kt (#136): valor ÷ 20, arredondado a US$ 10, com
 * piso de US$ 500.
 */
export const VALUE_PER_MONTHLY_SALARY = 20
export const salaryForValueOf = (value: number): number =>
  Math.max(500_00, Math.round(value / VALUE_PER_MONTHLY_SALARY / 10_00) * 10_00)

/**
 * Vencimentos na virada da temporada [endedSeason] de um clube: o humano perde
 * quem venceu (sai livre); a IA renova pelo mesmo salário (provisório, #143).
 * Com `targetSize` (issue #139), a IA só renova até o elenco caber nele e
 * libera o excedente — os de pior overall projetado primeiro (o jovem soma o
 * que falta até os 24; o trintão perde 1 por ano passado dos 30), sem deixar
 * posição abaixo do mínimo.
 */
export function contractTurnover(
  squad: Player[],
  endedSeason: number,
  countryCode: string,
  human: boolean,
  rng: MulberryRng,
  targetSize?: number,
): { squad: Player[]; released: Player[] } {
  const seasonEnd = seasonEndOf(endedSeason, countryCode)
  const expires = (p: Player): boolean => p.contract !== undefined && p.contract.end <= seasonEnd
  if (human) {
    return { squad: squad.filter((p) => !expires(p)), released: squad.filter(expires).map(releasedPlayer) }
  }
  const leaving = surplus(squad, squad.filter(expires), targetSize === undefined ? 0 : squad.length - targetSize)
  const start = seasonStartOf(endedSeason + 1, countryCode)
  return {
    squad: squad
      .filter((p) => !leaving.has(p.id))
      .map((p) =>
        expires(p)
          ? { ...p, contract: contractFor(start, endedSeason + 1, typicalContractSeasons(p.age, rng), countryCode) }
          : p,
      ),
    released: squad.filter((p) => leaving.has(p.id)).map(releasedPlayer),
  }
}

const MIN_PER_POSITION: Record<Position, number> = { GK: 2, CB: 5, MF: 5, FW: 3 }

const projectedOverall = (p: Pick<Player, 'overall' | 'age'>): number =>
  p.overall + Math.max(0, POTENTIAL_AGE - p.age) - Math.max(0, p.age - 30)

function surplus(squad: Player[], expiring: Player[], excess: number): Set<number> {
  const leaving = new Set<number>()
  if (excess <= 0) return leaving
  const counts = new Map<Position, number>()
  for (const p of squad) counts.set(p.position, (counts.get(p.position) ?? 0) + 1)
  const candidates = expiring.toSorted(
    (a, b) => projectedOverall(a) - projectedOverall(b) || b.age - a.age || a.id - b.id,
  )
  for (const p of candidates) {
    if (leaving.size === excess) break
    const left = counts.get(p.position) ?? 0
    if (left <= MIN_PER_POSITION[p.position]) continue
    counts.set(p.position, left - 1)
    leaving.add(p.id)
  }
  return leaving
}

/**
 * Contrato de quem já tem clube quando o mundo do mock nasce: assinado em
 * 01/01/2026, duração típica da idade (seed pelo id do jogador).
 */
export const initialContractOf = (player: Pick<Player, 'id' | 'age'>, countryCode: string): Contract =>
  contractFor('2026-01-01', 2026, typicalContractSeasons(player.age, new MulberryRng(player.id * 31 + 136)), countryCode)

// ─── Termos pessoais — espelham domain/rules/ContractNegotiationRules.kt (issue #138) ───
// Mesma forma da regra do backend: exigência = salário do valor (contrato
// pleno, liga do clube que oferece) × fator de idade × prestígio; o degrau
// para baixo (clube ou liga muito mais fracos) cobra prêmio. Determinística.

export const WEAKER_CLUB_GAP = 6
export const WEAKER_CLUB_PREMIUM = 1.5
export const LOWER_LEAGUE_GAP = 8
export const LOWER_LEAGUE_PREMIUM = 1.3
export const RENEWAL_COOLDOWN_DAYS = 28
export const OFFENSIVE_OFFER_PERCENT = 75

/** O que o jogador enxerga de um clube: quem é, reputação (média dos 11 melhores) e força da liga. */
export interface ClubStature {
  clubId: number
  reputation: number
  leagueStrength: number
}

export const squadReputationOf = (squad: Pick<Player, 'overall'>[]): number => {
  const best = squad.map((p) => p.overall).sort((a, b) => b - a).slice(0, 11)
  return best.length === 0 ? 0 : Math.round(best.reduce((a, b) => a + b, 0) / best.length)
}

const roundedSalary = (raw: number): number => Math.max(500_00, Math.round(raw / 10_00) * 10_00)

const salaryAgeFactor = (age: number): number =>
  age <= 21 ? 0.85 : age < 30 ? 1 : Math.min(1.6, 1 + 0.12 * (age - 29))

const prestigeFactor = (reputation: number): number =>
  Math.min(1.25, Math.max(0.8, 1 + 0.015 * (70 - reputation)))

/** Durações que o jogador aceita pela idade (dentro do permitido pela FIFA). */
export function acceptedContractYears(age: number): [number, number] {
  if (age < ADULT_AGE) return [1, MAX_MINOR_CONTRACT_SEASONS]
  if (age <= 23) return [2, 5]
  if (age <= 29) return [1, 4]
  if (age <= 32) return [2, 4]
  return [1, 2]
}

/** O salário pedido, antes do degrau; na renovação, nunca abaixo do atual. */
export function salaryDemandOf(
  player: Pick<Player, 'overall' | 'age' | 'salary'>,
  buyer: ClubStature,
  current: ClubStature | null,
): number {
  const fair = salaryForValueOf(marketValueOf(player, buyer.leagueStrength))
  const demand = roundedSalary(fair * salaryAgeFactor(player.age) * prestigeFactor(buyer.reputation))
  return current && current.clubId === buyer.clubId ? Math.max(demand, player.salary) : demand
}

/** A recusa sem o `type`/`playerId` do contrato — o handler completa. */
export type Refusal = Pick<PlayerRefused, 'reason' | 'demandedSalary' | 'minYears' | 'maxYears'>

/** `null` = aceita; senão, a recusa com motivo, pedido e faixa de durações. */
export function playerResponseOf(
  player: Pick<Player, 'overall' | 'age' | 'salary'>,
  offer: ContractOffer,
  buyer: ClubStature,
  current: ClubStature | null,
): Refusal | null {
  const [minYears, maxYears] = acceptedContractYears(player.age)
  const base = salaryDemandOf(player, buyer, current)
  const moving = current !== null && current.clubId !== buyer.clubId
  const weaker = moving && current.reputation - buyer.reputation >= WEAKER_CLUB_GAP
  const lower = moving && current.leagueStrength - buyer.leagueStrength >= LOWER_LEAGUE_GAP
  const premium = (weaker ? WEAKER_CLUB_PREMIUM : 1) * (lower ? LOWER_LEAGUE_PREMIUM : 1)
  const demandedSalary = weaker || lower ? roundedSalary(base * premium) : base
  const refuse = (reason: RefusalReason): Refusal => ({ reason, demandedSalary, minYears, maxYears })

  if (offer.years < minYears) return refuse('TOO_SHORT')
  if (offer.years > maxYears) return refuse('TOO_LONG')
  if (offer.salary < base) return refuse('LOW_SALARY')
  if (offer.salary < demandedSalary) return refuse(weaker ? 'WEAKER_CLUB' : 'LOWER_LEAGUE')
  return null
}

/**
 * Esfriamento da renovação: oferta abaixo de 75% do pedido E abaixo do salário
 * atual → 4 semanas sem conversa. Manter o que ele ganha nunca ofende.
 */
export function renewalCooldownOf(
  offer: ContractOffer,
  refusal: Refusal,
  currentSalary: number,
  today: string,
): string | undefined {
  if (offer.salary >= currentSalary) return undefined
  if (offer.salary * 100 >= refusal.demandedSalary * OFFENSIVE_OFFER_PERCENT) return undefined
  const until = new Date(`${today}T00:00:00Z`)
  until.setUTCDate(until.getUTCDate() + RENEWAL_COOLDOWN_DAYS)
  return until.toISOString().slice(0, 10)
}
