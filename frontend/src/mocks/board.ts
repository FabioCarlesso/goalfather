// Regras da diretoria no mock (issue #62) — espelha
// `backend/.../domain/rules/BoardRules.kt`. Como todo `mocks/`, é código
// descartável: quando o backend estiver ligado, os handlers saem e isto vai
// junto. A app NUNCA importa daqui — o painel lê o `BoardStatus` pronto.

import type {
  BoardMood,
  Dismissal,
  BoardObjective,
  BoardReview,
  BoardStatus,
  BoardVerdict,
  Standings,
} from '../domain/types'

export const INITIAL_BOARD_CONFIDENCE = 50
export const FIRING_CONFIDENCE = 25
export const CONFIDENT_CONFIDENCE = 65

const ON_TARGET_GAIN = 3
const GAIN_PER_POSITION_ABOVE = 2
const MAX_ROUND_GAIN = 8
const LOSS_PER_POSITION_BELOW = 4
const MAX_ROUND_LOSS = 12
const TITLE_CONTENDERS_PER_20 = 3

/** Bônus por cumprir a meta — `BoardObjective.bonusCents` no backend. */
export const BOARD_BONUS_CENTS: Record<BoardObjective, number> = {
  TITLE: 300_000_00,
  TOP_HALF: 200_000_00,
  MID_TABLE: 100_000_00,
}

/** Meta de uma temporada — `BoardExpectation` do domínio. */
export interface MockBoard {
  season: number
  objective: BoardObjective
  targetPosition: number
  confidence: number
}

/** O que a diretoria precisa saber de um clube para fixar a meta. */
export interface BoardClub {
  id: number
  division: number
  /** Força do elenco para a diretoria (`squadRating` no backend). */
  rating: number
}

/** Soma do `overall` dos 11 melhores ÷ 11 (vaga vazia conta zero) — ignora fadiga e escalação. */
export function squadRating(overalls: number[]): number {
  const best = [...overalls].sort((a, b) => b - a).slice(0, 11)
  return best.reduce((s, o) => s + o, 0) / 11
}

export function objectiveFor(strengthRank: number, divisionSize: number): BoardObjective {
  const titleContenders = Math.max(1, Math.floor((divisionSize * TITLE_CONTENDERS_PER_20) / 20))
  if (strengthRank <= titleContenders) return 'TITLE'
  if (strengthRank <= Math.floor(divisionSize / 2)) return 'TOP_HALF'
  return 'MID_TABLE'
}

export function targetPositionOf(objective: BoardObjective, divisionSize: number): number {
  const half = Math.floor(divisionSize / 2)
  const raw =
    objective === 'TITLE' ? 1
    : objective === 'TOP_HALF' ? Math.max(1, half)
    : Math.max(half + 1, divisionSize - Math.max(1, Math.floor(divisionSize / 4)))
  return Math.min(Math.max(raw, 1), Math.max(1, divisionSize))
}

/** Metas da temporada para todos os clubes, divisão por divisão (empate → menor id). */
export function boardExpectations(season: number, clubs: BoardClub[]): Record<number, MockBoard> {
  const byDivision = new Map<number, BoardClub[]>()
  for (const club of clubs) byDivision.set(club.division, [...(byDivision.get(club.division) ?? []), club])

  const boards: Record<number, MockBoard> = {}
  for (const divisionClubs of byDivision.values()) {
    const ranked = [...divisionClubs].sort((a, b) => b.rating - a.rating || a.id - b.id)
    ranked.forEach((club, i) => {
      const objective = objectiveFor(i + 1, ranked.length)
      boards[club.id] = {
        season,
        objective,
        targetPosition: targetPositionOf(objective, ranked.length),
        confidence: INITIAL_BOARD_CONFIDENCE,
      }
    })
  }
  return boards
}

export function confidenceDelta(targetPosition: number, position: number): number {
  const above = targetPosition - position
  return above >= 0
    ? Math.min(MAX_ROUND_GAIN, ON_TARGET_GAIN + GAIN_PER_POSITION_ABOVE * above)
    : -Math.min(MAX_ROUND_LOSS, LOSS_PER_POSITION_BELOW * -above)
}

export function afterRound(board: MockBoard, position: number): MockBoard {
  const confidence = Math.min(100, Math.max(0, board.confidence + confidenceDelta(board.targetPosition, position)))
  return { ...board, confidence }
}

export function moodOf(confidence: number): BoardMood {
  if (confidence <= FIRING_CONFIDENCE) return 'AT_RISK'
  if (confidence >= CONFIDENT_CONFIDENCE) return 'CONFIDENT'
  return 'WARY'
}

export function verdictOf(board: MockBoard, finalPosition: number): BoardVerdict {
  if (finalPosition <= board.targetPosition) return { type: 'TargetMet', bonusCents: BOARD_BONUS_CENTS[board.objective] }
  if (board.confidence <= FIRING_CONFIDENCE) return { type: 'Fired' }
  return { type: 'TargetMissed' }
}

/**
 * Confiança depois de uma rodada de LIGA — só nas divisões que jogaram (tabela
 * com algum clube de [playedClubIds]). Divisão que folga (a menor, no fim de um
 * calendário desigual) tem a tabela parada e não mexe na confiança — como o
 * `boardEvaluator` do backend.
 */
export function boardsAfterRound(
  boards: Record<number, MockBoard>,
  tables: Standings[],
  playedClubIds: ReadonlySet<number>,
): Record<number, MockBoard> {
  const positions = positionsOf(tables.filter((t) => t.rows.some((r) => playedClubIds.has(r.clubId))))
  return Object.fromEntries(
    Object.entries(boards).map(([id, board]) => {
      const position = positions.get(Number(id))
      return [id, position == null ? board : afterRound(board, position)]
    }),
  )
}

/** Técnico recém-chegado: a meta é do clube e fica, a confiança recomeça (claim no backend). */
export function forNewManager(board: MockBoard): MockBoard {
  return { ...board, confidence: INITIAL_BOARD_CONFIDENCE }
}

/** Registro de demissão do balanço — `BoardReview.toDismissal()` do backend; `null` se não foi demitido. */
export function toDismissal(review: BoardReview): Dismissal | null {
  switch (review.verdict.type) {
    case 'TargetMet':
    case 'TargetMissed':
      return null
    case 'Fired':
      return {
        clubId: review.clubId,
        clubName: review.clubName,
        season: review.season,
        objective: review.objective,
        targetPosition: review.targetPosition,
        finalPosition: review.finalPosition,
      }
  }
}

/** Posição de cada clube nas tabelas (todas as divisões). */
export function positionsOf(tables: Standings[]): Map<number, number> {
  return new Map(tables.flatMap((t) => t.rows.map((r) => [r.clubId, r.position] as const)))
}

/** Painel do clube — `BoardService.status` + `BoardStatusDto` do backend. */
export function boardStatusOf(clubId: number, board: MockBoard | undefined, tables: Standings[]): BoardStatus | null {
  const table = tables.find((t) => t.rows.some((r) => r.clubId === clubId))
  const row = table?.rows.find((r) => r.clubId === clubId)
  if (!board || !table || !row) return null
  return {
    clubId,
    season: board.season,
    objective: board.objective,
    targetPosition: board.targetPosition,
    currentPosition: row.position,
    onTarget: row.position <= board.targetPosition,
    divisionSize: table.rows.length,
    matchday: table.round,
    confidence: board.confidence,
    mood: moodOf(board.confidence),
    bonusCents: BOARD_BONUS_CENTS[board.objective],
    firingConfidence: FIRING_CONFIDENCE,
  }
}

/** Balanço da temporada encerrada — `seasonBoardReviews` do backend. */
export function seasonBoardReviews(
  season: number,
  finalTables: Standings[],
  boards: Record<number, MockBoard>,
  clubName: (clubId: number) => string,
): BoardReview[] {
  const positions = positionsOf(finalTables)
  return Object.entries(boards)
    .map(([id, board]) => ({ clubId: Number(id), board }))
    .filter(({ clubId, board }) => board.season === season && positions.has(clubId))
    .sort((a, b) => a.clubId - b.clubId)
    .map(({ clubId, board }) => {
      const finalPosition = positions.get(clubId)!
      return {
        clubId,
        clubName: clubName(clubId),
        season,
        objective: board.objective,
        targetPosition: board.targetPosition,
        finalPosition,
        confidence: board.confidence,
        verdict: verdictOf(board, finalPosition),
      }
    })
}
