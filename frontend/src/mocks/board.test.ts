// Testes do mock da diretoria (issue #62) — garantem que a engine de mock
// espelha BoardRules.kt do backend com os mesmos invariantes do BoardRulesTest.

import { describe, expect, it } from 'vitest'
import {
  BOARD_BONUS_CENTS,
  FIRING_CONFIDENCE,
  INITIAL_BOARD_CONFIDENCE,
  afterRound,
  boardExpectations,
  boardStatusOf,
  confidenceDelta,
  moodOf,
  objectiveFor,
  seasonBoardReviews,
  squadRating,
  targetPositionOf,
  verdictOf,
  type MockBoard,
} from './board'
import type { BoardObjective, Standings } from '../domain/types'

const titleBoard = (confidence = INITIAL_BOARD_CONFIDENCE): MockBoard =>
  ({ season: 2026, objective: 'TITLE', targetPosition: 1, confidence })

const table = (clubIds: number[]): Standings => ({
  season: 2026,
  division: 1,
  round: 3,
  promotionSpots: 0,
  relegationSpots: 0,
  rows: clubIds.map((clubId, i) => ({
    position: i + 1, clubId, clubName: `C${clubId}`,
    played: 3, wins: 0, draws: 0, losses: 0, goalsFor: 0, goalsAgainst: 0, goalDifference: 0, points: 0,
  })),
})

describe('mock da diretoria (issue #62)', () => {
  it('meta é determinística dada a composição da liga', () => {
    const clubs = [85, 80, 76, 72, 68, 64].map((rating, i) => ({ id: i + 1, division: 1, rating }))

    const boards = boardExpectations(2026, clubs)

    expect(boardExpectations(2026, [...clubs].reverse())).toEqual(boards)
    expect(boards[1]).toMatchObject({ objective: 'TITLE', targetPosition: 1 })
    expect(boards[2]).toMatchObject({ objective: 'TOP_HALF', targetPosition: 3 })
    expect(boards[6]).toMatchObject({ objective: 'MID_TABLE', targetPosition: 5 })
  })

  it('ranqueia cada clube só contra a própria divisão', () => {
    const boards = boardExpectations(2026, [
      { id: 1, division: 1, rating: 85 }, { id: 2, division: 1, rating: 84 },
      { id: 3, division: 2, rating: 70 }, { id: 4, division: 2, rating: 60 },
    ])
    expect(boards[3]!.objective).toBe('TITLE')
  })

  it('candidatos ao título são 3 numa liga de 20 e 1 numa de 6', () => {
    expect(objectiveFor(3, 20)).toBe('TITLE')
    expect(objectiveFor(4, 20)).toBe('TOP_HALF')
    expect(objectiveFor(1, 6)).toBe('TITLE')
    expect(objectiveFor(2, 6)).toBe('TOP_HALF')
  })

  it('posição alvo cabe na divisão e segue a ordem de ambição', () => {
    const objectives: BoardObjective[] = ['TITLE', 'TOP_HALF', 'MID_TABLE']
    for (let size = 1; size <= 24; size++) {
      const targets = objectives.map((o) => targetPositionOf(o, size))
      expect(targets.every((t) => t >= 1 && t <= size)).toBe(true)
      expect(targets).toEqual([...targets].sort((a, b) => a - b))
    }
  })

  it('força do elenco é a dos 11 melhores', () => {
    expect(squadRating([...Array(11).fill(80), ...Array(9).fill(50)])).toBe(80)
    // Elenco desmontado: vaga vazia conta zero.
    expect(squadRating([88, 86])).toBeLessThan(squadRating(Array(11).fill(65)))
  })

  it('líder disparado satura em 100; lanterna afunda até 0', () => {
    let leader = titleBoard()
    let last = titleBoard()
    for (let i = 0; i < 30; i++) {
      leader = afterRound(leader, 1)
      last = afterRound(last, 20)
    }
    expect(leader.confidence).toBe(100)
    expect(moodOf(leader.confidence)).toBe('CONFIDENT')
    expect(last.confidence).toBe(0)
    expect(moodOf(last.confidence)).toBe('AT_RISK')
  })

  it('confiança sobe na meta e cai abaixo dela', () => {
    expect(confidenceDelta(5, 5)).toBeGreaterThan(0)
    expect(confidenceDelta(5, 6)).toBeLessThan(0)
    expect(confidenceDelta(5, 9)).toBeLessThan(confidenceDelta(5, 6))
  })

  it('veredito: bônus, permanência ou demissão', () => {
    expect(verdictOf(titleBoard(0), 1)).toEqual({ type: 'TargetMet', bonusCents: BOARD_BONUS_CENTS.TITLE })
    expect(verdictOf(titleBoard(FIRING_CONFIDENCE), 4)).toEqual({ type: 'Fired' })
    expect(verdictOf(titleBoard(80), 2)).toEqual({ type: 'TargetMissed' })
  })

  it('balanço da temporada ignora meta de outra temporada', () => {
    const reviews = seasonBoardReviews(
      2026,
      [table([1, 2])],
      { 1: titleBoard(), 2: { ...titleBoard(), season: 2025 } },
      (id) => `C${id}`,
    )
    expect(reviews).toHaveLength(1)
    expect(reviews[0]).toMatchObject({ clubId: 1, finalPosition: 1, verdict: { type: 'TargetMet' } })
  })

  it('painel traz posição, tamanho da divisão e o limiar de demissão', () => {
    const status = boardStatusOf(2, titleBoard(20), [table([1, 2, 3])])
    expect(status).toMatchObject({
      currentPosition: 2, divisionSize: 3, matchday: 3, mood: 'AT_RISK', firingConfidence: FIRING_CONFIDENCE,
    })
    expect(boardStatusOf(99, titleBoard(), [table([1, 2])])).toBeNull()
  })
})
