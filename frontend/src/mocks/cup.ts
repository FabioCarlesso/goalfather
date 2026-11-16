// Copa mata-mata do mock (issues #61 e #131) — espelha domain/rules/CupRules.kt
// e o calendário do backend. Como todo `mocks/`, é código descartável: a regra
// mora no Kotlin e a app real só lê a chave pronta de `/api/league/cup`.
//
// O formato é o do pack do E2E (Copa do Brasil reduzida, 12 clubes):
// 1ª fase em jogo único com os 8 mais fracos → quartas em ida e volta com os
// 4 mais fortes entrando → semifinal em ida e volta → final em jogo único.

import type {
  Cup,
  CupClub,
  CupLeg,
  CupLegs,
  CupPhaseInfo,
  CupStage,
  CupTie,
  Matchday,
  Round,
  RoundMatch,
  RoundStage,
} from '../domain/types'
import { MulberryRng } from './engine'
import { matchdaySerial } from './calendar'

type CupSingleTie = Extract<CupTie, { type: 'Single' }>
type CupTwoLeggedTie = Extract<CupTie, { type: 'TwoLegged' }>
type CupMatchStage = Extract<RoundStage, { type: 'Cup' }>

/** Uma fase do formato `Knockout` do pack (o `KnockoutStage` do backend). */
export interface KnockoutStageFormat {
  name: string
  teams: number
  legs: CupLegs
  newEntrants: number
}

/** Formato da Copa do Brasil do pack do E2E (12 clubes) — o que o mock joga. */
export const E2E_CUP_FORMAT: readonly KnockoutStageFormat[] = [
  { name: 'Primeira fase', teams: 8, legs: 'SINGLE', newEntrants: 8 },
  { name: 'Quartas de final', teams: 8, legs: 'TWO_LEGGED', newEntrants: 4 },
  { name: 'Semifinal', teams: 4, legs: 'TWO_LEGGED', newEntrants: 0 },
  { name: 'Final', teams: 2, legs: 'SINGLE', newEntrants: 0 },
]

/** Id e nome da copa do mundo do MVP — o `GameCompetition` da Copa do Brasil no pack. */
export const CUP_COMPETITION_ID = 'bra-copa-do-brasil'
export const CUP_COMPETITION_NAME = 'Copa do Brasil'
export const leagueCompetitionId = (division: number): string => `liga-d${division}`
export const leagueCompetitionName = (division: number): string => `Liga — Divisão ${division}`

/** Clubes que o formato comporta: soma dos entrantes de todas as fases. */
export const cupEntrants = (format: readonly KnockoutStageFormat[]): number =>
  format.reduce((sum, s) => sum + s.newEntrants, 0)

// ─── Prêmios — espelha `cupPhasePrize` ───────────────────────────────────

const CUP_FINAL_PRIZE_CENTS = 1_000_000_00
const CUP_SEMI_FINAL_PRIZE_CENTS = 400_000_00
const CUP_MIN_PRIZE_CENTS = 10_000_00

/** Prêmio por confronto vencido numa fase a `phasesToFinal` fases da final. */
export function cupPhasePrize(phasesToFinal: number): number {
  if (phasesToFinal === 0) return CUP_FINAL_PRIZE_CENTS
  return Math.max(CUP_MIN_PRIZE_CENTS, Math.floor(CUP_SEMI_FINAL_PRIZE_CENTS / 2 ** (phasesToFinal - 1)))
}

// ─── Sorteio ─────────────────────────────────────────────────────────────

/** Fisher–Yates com o RNG seedável — mesmo seed, mesma ordem. */
function shuffled<T>(items: T[], rng: MulberryRng): T[] {
  const out = [...items]
  for (let i = out.length - 1; i > 0; i--) {
    const j = Math.floor(rng.next() * (i + 1))
    ;[out[i], out[j]] = [out[j]!, out[i]!]
  }
  return out
}

/** Sorteio da fase `index`: seed da temporada + índice (o backend usa a `drawSeed` da copa). */
const stageRng = (season: number, index: number): MulberryRng => new MulberryRng(season * 7 + 61 + index)

/** Sorteia os confrontos de uma fase; o primeiro de cada par tem o mando (da ida). */
function drawStage(phase: CupPhaseInfo, clubs: CupClub[], rng: MulberryRng): CupStage {
  if (clubs.length !== phase.teams) throw new Error(`${phase.name} espera ${phase.teams} clubes`)
  const drawn = shuffled([...clubs].sort((a, b) => a.id - b.id), rng)
  const ties = Array.from({ length: phase.teams / 2 }, (_, index): CupTie => {
    const home = drawn[2 * index]!
    const away = drawn[2 * index + 1]!
    return phase.legs === 'SINGLE'
      ? { type: 'Single', index, home, away }
      : { type: 'TwoLegged', index, home, away }
  })
  return { name: phase.name, legs: phase.legs, ties }
}

/**
 * Sorteia a copa: `seeded` vem do mais forte ao mais fraco; da última fase
 * para a primeira, cada uma tira do topo os seus entrantes. Só a abertura é
 * sorteada agora — as seguintes, quando a anterior termina.
 */
export function drawCup(season: number, format: readonly KnockoutStageFormat[], seeded: CupClub[]): Cup {
  if (seeded.length !== cupEntrants(format)) throw new Error(`Copa espera ${cupEntrants(format)} clubes`)
  const phases: CupPhaseInfo[] = format.map((stage, i) => {
    const start = format.slice(i + 1).reduce((sum, s) => sum + s.newEntrants, 0)
    return {
      name: stage.name,
      teams: stage.teams,
      legs: stage.legs,
      entrants: seeded.slice(start, start + stage.newEntrants),
      prizeCents: cupPhasePrize(format.length - 1 - i),
    }
  })
  const opening = phases[0]!
  return withDerived({
    season,
    competitionId: CUP_COMPETITION_ID,
    competitionName: CUP_COMPETITION_NAME,
    phases,
    stages: [drawStage(opening, opening.entrants, stageRng(season, 0))],
  })
}

const isComplete = (stage: CupStage): boolean => stage.ties.every((t) => t.winnerId != null)

const winnerOf = (tie: CupTie): CupClub | null =>
  tie.winnerId == null ? null : tie.winnerId === tie.home.id ? tie.home : tie.away

type CupCore = Omit<Cup, 'currentStage' | 'champion'>

/** Recalcula o que o backend deriva no `CupDto`: `currentStage` e `champion`. */
function withDerived({ season, competitionId, competitionName, phases, stages }: CupCore): Cup {
  const last = stages[stages.length - 1]!
  const finalTie = stages.length === phases.length && isComplete(last) ? last.ties[0] : undefined
  const champion = finalTie ? winnerOf(finalTie) : null
  return {
    season,
    competitionId,
    competitionName,
    phases,
    stages,
    ...(isComplete(last) ? {} : { currentStage: last.name }),
    ...(champion ? { champion } : {}),
  }
}

export const currentStage = (cup: Cup): CupStage | null => {
  const last = cup.stages[cup.stages.length - 1]!
  return isComplete(last) ? null : last
}

// ─── Pernas e pênaltis ───────────────────────────────────────────────────

/** A perna em aberto de um confronto; `null` se ele já foi decidido. */
function openLeg(tie: CupTie): CupLeg | null {
  if (tie.winnerId != null) return null
  switch (tie.type) {
    case 'Single': return 'SINGLE'
    case 'TwoLegged': return tie.firstLeg ? 'SECOND' : 'FIRST'
  }
}

/**
 * Esta partida, terminando `homeGoals × awayGoals`, vai aos pênaltis? Espelha
 * `RoundMatch.decidesOnPenalties`: liga nunca; jogo único se empatou; ida
 * nunca; volta só com o agregado empatado (sem gol fora).
 */
export function decidesOnPenalties(stage: RoundStage, homeGoals: number, awayGoals: number): boolean {
  switch (stage.type) {
    case 'League':
      return false
    case 'Cup':
      switch (stage.leg) {
        case 'SINGLE': return homeGoals === awayGoals
        case 'FIRST': return false
        // Na volta, o mandante é o visitante da ida.
        case 'SECOND': {
          const first = stage.firstLeg
          return first != null && first.homeGoals + awayGoals === first.awayGoals + homeGoals
        }
      }
  }
}

// ─── Avanço da chave ─────────────────────────────────────────────────────

/** Grava a perna jogada; o confronto segue em aberto se o resultado não decide ninguém. */
function recordTie(tie: CupTie, played: Map<string, RoundMatch>): CupTie {
  switch (tie.type) {
    case 'Single': return recordSingle(tie, played)
    case 'TwoLegged': return recordTwoLegged(tie, played)
  }
}

function recordSingle(tie: CupSingleTie, played: Map<string, RoundMatch>): CupTie {
  const match = tie.result ? undefined : played.get(`${tie.home.id}-${tie.away.id}`)
  if (!match) return tie
  const { homeGoals, awayGoals, penalties } = match
  if (homeGoals !== awayGoals) {
    return { ...tie, result: { homeGoals, awayGoals }, winnerId: homeGoals > awayGoals ? tie.home.id : tie.away.id }
  }
  // Empate sem pênaltis não decide ninguém — confronto segue em aberto.
  if (!penalties || penalties.home === penalties.away) return tie
  return {
    ...tie,
    result: { homeGoals, awayGoals, penalties },
    winnerId: penalties.home > penalties.away ? tie.home.id : tie.away.id,
  }
}

function recordTwoLegged(tie: CupTwoLeggedTie, played: Map<string, RoundMatch>): CupTie {
  if (tie.winnerId != null) return tie
  if (!tie.firstLeg) {
    const match = played.get(`${tie.home.id}-${tie.away.id}`)
    if (!match) return tie
    const firstLeg = { homeGoals: match.homeGoals, awayGoals: match.awayGoals }
    return { ...tie, firstLeg, aggregate: { home: firstLeg.homeGoals, away: firstLeg.awayGoals } }
  }
  // Volta: mando invertido.
  const match = played.get(`${tie.away.id}-${tie.home.id}`)
  if (!match) return tie
  const secondLeg = { homeGoals: match.homeGoals, awayGoals: match.awayGoals }
  const aggregate = {
    home: tie.firstLeg.homeGoals + secondLeg.awayGoals,
    away: tie.firstLeg.awayGoals + secondLeg.homeGoals,
  }
  if (aggregate.home !== aggregate.away) {
    return { ...tie, secondLeg, aggregate, winnerId: aggregate.home > aggregate.away ? tie.home.id : tie.away.id }
  }
  const shootout = match.penalties
  if (!shootout || shootout.home === shootout.away) return tie
  // Pênaltis da súmula (mandante da volta = `away`) → orientação do confronto.
  const penalties = { home: shootout.away, away: shootout.home }
  return {
    ...tie,
    secondLeg,
    aggregate,
    penalties,
    winnerId: penalties.home > penalties.away ? tie.home.id : tie.away.id,
  }
}

/**
 * Registra a perna em aberto de cada confronto da fase corrente e, se a fase
 * fechou, sorteia a seguinte com os vencedores + os entrantes dela. Devolve o
 * prêmio de quem passou de fase NESTA perna.
 */
export function recordCupRound(cup: Cup, round: Round): { cup: Cup; prizes: Map<number, number> } {
  const stage = currentStage(cup)
  const prizes = new Map<number, number>()
  if (!stage) return { cup, prizes }
  const stageIndex = cup.stages.length - 1
  const prize = cup.phases[stageIndex]!.prizeCents

  const played = new Map(
    // Só jogos DESTA copa (issue #125): liga e copa podem dividir o encaixe.
    round.matches
      .filter((m) => m.status === 'Finished' && m.stage.type === 'Cup' && m.competitionId === cup.competitionId)
      .map((m) => [`${m.homeClubId}-${m.awayClubId}`, m]),
  )
  const ties = stage.ties.map((tie) => {
    const recorded = recordTie(tie, played)
    if (tie.winnerId == null && recorded.winnerId != null) prizes.set(recorded.winnerId, prize)
    return recorded
  })

  const recorded: CupStage = { ...stage, ties }
  const stages = [...cup.stages.slice(0, -1), recorded]
  const nextPhase = cup.phases[stageIndex + 1]
  if (isComplete(recorded) && nextPhase) {
    const winners = ties.flatMap((t) => winnerOf(t) ?? [])
    stages.push(drawStage(nextPhase, [...winners, ...nextPhase.entrants], stageRng(cup.season, stageIndex + 1)))
  }
  return { cup: withDerived({ ...cup, stages }), prizes }
}

// ─── Jogos do encaixe e calendário ───────────────────────────────────────

/**
 * Jogos de copa do encaixe `matchday`: a perna em aberto de cada confronto da
 * fase corrente — a ida (ou jogo único) e depois a volta, de mando invertido,
 * carregando o placar da ida. Mesma fórmula de matchId do backend: serial do
 * encaixe × 1000 + índice + 1.
 */
export function cupRound(
  matchday: Matchday,
  season: number,
  cup: Cup,
  divisionOf: (clubId: number) => number,
): Round {
  const stage = currentStage(cup)
  const matches: RoundMatch[] = (stage?.ties ?? []).flatMap((tie): RoundMatch[] => {
    const leg = openLeg(tie)
    if (!leg || !stage) return []
    const firstLeg = leg === 'SECOND' && tie.type === 'TwoLegged' ? tie.firstLeg : undefined
    const [home, away] = leg === 'SECOND' ? [tie.away, tie.home] : [tie.home, tie.away]
    const matchStage: CupMatchStage = { type: 'Cup', stageName: stage.name, leg, ...(firstLeg ? { firstLeg } : {}) }
    return [{
      matchId: matchdaySerial(matchday) * 1000 + tie.index + 1,
      homeClubId: home.id,
      awayClubId: away.id,
      homeClubName: home.name,
      awayClubName: away.name,
      status: 'Scheduled',
      homeGoals: 0,
      awayGoals: 0,
      minute: 0,
      division: divisionOf(home.id),
      stage: matchStage,
      competitionId: cup.competitionId,
      competitionName: cup.competitionName,
    }]
  })
  return { matchday, season, status: 'Scheduled', matches }
}

/** As pernas da copa em ordem (espelha `Cup.calendarRounds`): jogo único = uma; ida e volta = duas. */
export function cupLegSlots(cup: Pick<Cup, 'phases'>): CupMatchStage[] {
  return cup.phases.flatMap((p): CupMatchStage[] =>
    p.legs === 'SINGLE'
      ? [{ type: 'Cup', stageName: p.name, leg: 'SINGLE' }]
      : [{ type: 'Cup', stageName: p.name, leg: 'FIRST' }, { type: 'Cup', stageName: p.name, leg: 'SECOND' }])
}

/**
 * Calendário da temporada: o encaixe de copa `i` entra logo após a rodada de
 * liga ⌊(i+1)·L/(C+1)⌋ (no mínimo a 1ª) — uma perna de copa por encaixe,
 * espalhadas entre as rodadas, com a final antes da última rodada. Os
 * estágios de copa aqui são só MARCADORES do encaixe (sem o placar da ida):
 * os jogos de verdade saem de `cupRound`, com a copa como ela estiver.
 */
export function seasonCalendar(leagueMatchdays: number, cupLegs: readonly CupMatchStage[]): RoundStage[] {
  const gaps = cupLegs.length + 1
  const after = cupLegs.map((stage, i) => ({
    stage,
    matchday: Math.min(leagueMatchdays, Math.max(1, Math.floor(((i + 1) * leagueMatchdays) / gaps))),
  }))
  const calendar: RoundStage[] = []
  for (let matchday = 1; matchday <= leagueMatchdays; matchday++) {
    calendar.push({ type: 'League', matchday })
    for (const c of after) if (c.matchday === matchday) calendar.push(c.stage)
  }
  return calendar
}
