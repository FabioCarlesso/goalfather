// Copa mata-mata do mock (issue #61) — espelha domain/rules/CupRules.kt e
// SeasonCalendar.kt do backend. Como todo `mocks/`, é código descartável: a
// regra mora no Kotlin e a app real só lê a chave pronta de `/api/league/cup`.

import type {
  Cup,
  CupClub,
  CupStage,
  CupTie,
  KnockoutPhase,
  PenaltyScore,
  Round,
  RoundMatch,
  RoundStage,
} from '../domain/types'
import type { MulberryRng } from './engine'

/** Fases na ordem da chave, da mais larga para a final. */
export const KNOCKOUT_PHASES: readonly KnockoutPhase[] =
  ['ROUND_OF_32', 'ROUND_OF_16', 'QUARTER_FINALS', 'SEMI_FINALS', 'FINAL']

export const PHASE_TEAMS: Record<KnockoutPhase, number> = {
  ROUND_OF_32: 32,
  ROUND_OF_16: 16,
  QUARTER_FINALS: 8,
  SEMI_FINALS: 4,
  FINAL: 2,
}

/** Prêmio por confronto vencido em cada fase — mesmos valores de `KnockoutPhase.prizeCents`. */
export const CUP_PRIZE_CENTS: Record<KnockoutPhase, number> = {
  ROUND_OF_32: 75_000_00,
  ROUND_OF_16: 100_000_00,
  QUARTER_FINALS: 150_000_00,
  SEMI_FINALS: 250_000_00,
  FINAL: 500_000_00,
}

export const MIN_CUP_CLUBS = 4
export const MAX_CUP_CLUBS = 32

export const canHoldCup = (clubCount: number): boolean =>
  clubCount >= MIN_CUP_CLUBS && clubCount <= MAX_CUP_CLUBS

/** Vencedor de um confronto: a folga passa direto; o jogo, por placar ou pênaltis. */
export function tieWinner(tie: CupTie): CupClub | null {
  switch (tie.type) {
    case 'Bye':
      return tie.club
    case 'Fixture': {
      if (!tie.result) return null
      const { homeGoals, awayGoals, penalties } = tie.result
      const homeWon = penalties ? penalties.home > penalties.away : homeGoals > awayGoals
      return homeWon ? tie.home : tie.away
    }
  }
}

const isComplete = (stage: CupStage): boolean => stage.ties.every((t) => tieWinner(t) != null)

const phasesFrom = (opening: KnockoutPhase): KnockoutPhase[] =>
  KNOCKOUT_PHASES.filter((p) => PHASE_TEAMS[p] <= PHASE_TEAMS[opening])

/**
 * Recalcula os campos que o backend deriva no `CupDto` (`phases`,
 * `currentPhase`, `champion`) a partir das fases sorteadas.
 */
function withDerived(season: number, stages: CupStage[]): Cup {
  const last = stages[stages.length - 1]!
  const lastFinal = last.phase === 'FINAL' && isComplete(last) ? tieWinner(last.ties[0]!) : null
  return {
    season,
    phases: phasesFrom(stages[0]!.phase).map((phase) => ({ phase, prizeCents: CUP_PRIZE_CENTS[phase] })),
    stages,
    ...(isComplete(last) ? {} : { currentPhase: last.phase }),
    ...(lastFinal ? { champion: lastFinal } : {}),
  }
}

export const currentStage = (cup: Cup): CupStage | null => {
  const last = cup.stages[cup.stages.length - 1]!
  return isComplete(last) ? null : last
}

/** Fisher–Yates com o RNG seedável — mesmo seed, mesma ordem. */
function shuffled<T>(items: T[], rng: MulberryRng): T[] {
  const out = [...items]
  for (let i = out.length - 1; i > 0; i--) {
    const j = Math.floor(rng.next() * (i + 1))
    ;[out[i], out[j]] = [out[j]!, out[i]!]
  }
  return out
}

/**
 * Sorteia a chave: menor potência de 2 que comporta os clubes, folgas nos
 * confrontos de índice par primeiro (quem folga estreia contra quem venceu um
 * jogo). Clubes ordenados por id antes do embaralhamento.
 */
export function drawCup(season: number, clubs: CupClub[], rng: MulberryRng): Cup {
  if (!canHoldCup(clubs.length)) throw new Error(`Copa exige ${MIN_CUP_CLUBS}..${MAX_CUP_CLUBS} clubes`)
  let bracket = 2
  while (bracket < clubs.length) bracket *= 2
  const tieCount = bracket / 2
  const byes = bracket - clubs.length
  const evens = Array.from({ length: tieCount }, (_, i) => i).filter((i) => i % 2 === 0)
  const odds = Array.from({ length: tieCount }, (_, i) => i).filter((i) => i % 2 === 1)
  const byeSlots = new Set([...evens, ...odds].slice(0, byes))

  const drawn = shuffled([...clubs].sort((a, b) => a.id - b.id), rng)
  let next = 0
  const ties: CupTie[] = Array.from({ length: tieCount }, (_, index) =>
    byeSlots.has(index)
      ? { type: 'Bye', index, club: drawn[next++]! }
      : { type: 'Fixture', index, home: drawn[next++]!, away: drawn[next++]! },
  )
  const opening = KNOCKOUT_PHASES.find((p) => PHASE_TEAMS[p] === bracket)!
  return withDerived(season, [{ phase: opening, ties }])
}

/**
 * Registra os placares de uma rodada de copa encerrada na fase corrente e,
 * se ela fechou, cruza os vencedores (2k × 2k+1) na fase seguinte. Devolve o
 * prêmio de quem venceu EM CAMPO — folga não premia.
 */
export function recordCupRound(cup: Cup, round: Round): { cup: Cup; prizes: Map<number, number> } {
  const stage = currentStage(cup)
  const prizes = new Map<number, number>()
  if (!stage) return { cup, prizes }

  const played = new Map(
    round.matches.filter((m) => m.status === 'Finished').map((m) => [`${m.homeClubId}-${m.awayClubId}`, m]),
  )
  const ties: CupTie[] = stage.ties.map((tie) => {
    if (tie.type === 'Bye' || tie.result) return tie
    const match = played.get(`${tie.home.id}-${tie.away.id}`)
    if (!match) return tie
    const penalties: PenaltyScore | undefined = match.penalties
    // Empate sem pênaltis não decide ninguém — confronto segue em aberto.
    if (match.homeGoals === match.awayGoals && (!penalties || penalties.home === penalties.away)) return tie
    const result = {
      homeGoals: match.homeGoals,
      awayGoals: match.awayGoals,
      ...(match.homeGoals === match.awayGoals && penalties ? { penalties } : {}),
    }
    const decided: CupTie = { ...tie, result }
    const winner = tieWinner(decided)!
    prizes.set(winner.id, CUP_PRIZE_CENTS[stage.phase])
    return { ...decided, winnerId: winner.id }
  })

  const recorded: CupStage = { ...stage, ties }
  const stages = [...cup.stages.slice(0, -1), recorded]
  const nextPhase = KNOCKOUT_PHASES[KNOCKOUT_PHASES.indexOf(stage.phase) + 1]
  if (isComplete(recorded) && nextPhase) {
    const winners = [...ties].sort((a, b) => a.index - b.index).map((t) => tieWinner(t)!)
    const nextTies: CupTie[] = []
    for (let i = 0; i < winners.length; i += 2) {
      nextTies.push({ type: 'Fixture', index: i / 2, home: winners[i]!, away: winners[i + 1]! })
    }
    stages.push({ phase: nextPhase, ties: nextTies })
  }
  return { cup: withDerived(cup.season, stages), prizes }
}

/**
 * Rodada da data `number` com os confrontos em aberto da fase corrente. Mesma
 * fórmula de matchId do backend: data × 1000 + índice + 1.
 */
export function cupRound(
  number: number,
  season: number,
  cup: Cup,
  divisionOf: (clubId: number) => number,
): Round {
  const stage = currentStage(cup)
  const pending = (stage?.ties ?? []).filter(
    (t): t is Extract<CupTie, { type: 'Fixture' }> => t.type === 'Fixture' && !t.result,
  )
  const matches: RoundMatch[] = pending.map((tie) => ({
    matchId: number * 1000 + tie.index + 1,
    homeClubId: tie.home.id,
    awayClubId: tie.away.id,
    homeClubName: tie.home.name,
    awayClubName: tie.away.name,
    status: 'Scheduled',
    homeGoals: 0,
    awayGoals: 0,
    minute: 0,
    division: divisionOf(tie.home.id),
  }))
  const phase = stage?.phase ?? cup.phases[cup.phases.length - 1]!.phase
  return { number, season, status: 'Scheduled', stage: { type: 'Cup', phase }, matches }
}

/**
 * Calendário da temporada — espelha `seasonCalendar` do backend: a fase `i`
 * entra logo após a rodada de liga ⌊i·L/(C+1)⌋ (no mínimo a 1ª).
 */
export function seasonCalendar(leagueMatchdays: number, cupPhases: KnockoutPhase[]): RoundStage[] {
  const gaps = cupPhases.length + 1
  const after = cupPhases.map((phase, i) => ({
    phase,
    matchday: Math.min(leagueMatchdays, Math.max(1, Math.floor(((i + 1) * leagueMatchdays) / gaps))),
  }))
  const calendar: RoundStage[] = []
  for (let matchday = 1; matchday <= leagueMatchdays; matchday++) {
    calendar.push({ type: 'League', matchday })
    for (const c of after) if (c.matchday === matchday) calendar.push({ type: 'Cup', phase: c.phase })
  }
  return calendar
}
