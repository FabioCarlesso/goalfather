// Testes do mock de divisões (issue #47) — garantem que a engine de mock
// espelha as regras do backend (PromotionRelegationRules/RoundFixturesGenerator)
// até o backend assumir de vez.

import { describe, expect, it } from 'vitest'
import {
  applyPromotionRelegation,
  initialStandings,
  PROMOTION_RELEGATION_SPOTS,
  SEASON_ROUNDS,
  state,
} from './seed'
import type { Standings } from '../domain/types'

const table = (division: number, clubIds: number[], overrides?: Partial<Standings>): Standings => ({
  season: 2026,
  division,
  round: 5,
  promotionSpots: division === 1 ? 0 : PROMOTION_RELEGATION_SPOTS,
  relegationSpots: division === 1 ? PROMOTION_RELEGATION_SPOTS : 0,
  rows: clubIds.map((clubId, i) => ({
    position: i + 1,
    clubId,
    clubName: `Club ${clubId}`,
    played: 5, wins: 0, draws: 0, losses: 0,
    goalsFor: 0, goalsAgainst: 0, goalDifference: 0, points: 0,
  })),
  ...overrides,
})

describe('seed de divisões (issue #47)', () => {
  it('gera duas divisões com tabelas e zonas corretas', () => {
    expect(initialStandings.map((t) => t.division)).toEqual([1, 2])
    expect(initialStandings[0]!.rows).toHaveLength(6)
    expect(initialStandings[1]!.rows).toHaveLength(6)
    // Elite não sobe; última divisão não desce.
    expect(initialStandings[0]!.promotionSpots).toBe(0)
    expect(initialStandings[0]!.relegationSpots).toBe(PROMOTION_RELEGATION_SPOTS)
    expect(initialStandings[1]!.promotionSpots).toBe(PROMOTION_RELEGATION_SPOTS)
    expect(initialStandings[1]!.relegationSpots).toBe(0)
  })

  it('rodada corrente tem partidas das duas divisões sem colisão de matchId', () => {
    const matches = state.currentRound.matches
    expect(matches).toHaveLength(6)
    expect(new Set(matches.map((m) => m.matchId)).size).toBe(6)
    const div2 = matches.filter((m) => m.division === 2)
    expect(div2).toHaveLength(3)
    // Divisões nunca se cruzam dentro de uma partida.
    for (const m of div2) {
      expect(m.homeClubId).toBeGreaterThanOrEqual(7)
      expect(m.awayClubId).toBeGreaterThanOrEqual(7)
    }
  })

  it('temporada dura o turno da maior divisão (6 clubes → 5 rodadas)', () => {
    expect(SEASON_ROUNDS).toBe(5)
  })

  it('promoção/rebaixamento troca os últimos da elite com os primeiros da 2ª divisão', () => {
    const next = applyPromotionRelegation([
      table(1, [1, 2, 3, 4, 5, 6]),
      table(2, [7, 8, 9, 10, 11, 12]),
    ])

    expect(next[5]).toBe(2)
    expect(next[6]).toBe(2)
    expect(next[7]).toBe(1)
    expect(next[8]).toBe(1)
    expect(next[1]).toBe(1)
    expect(next[12]).toBe(2)
    // Balanceado: 6 clubes em cada divisão após a troca.
    expect(Object.values(next).filter((d) => d === 1)).toHaveLength(6)
    expect(Object.values(next).filter((d) => d === 2)).toHaveLength(6)
  })
})
