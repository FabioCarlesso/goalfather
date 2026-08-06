import { describe, expect, it } from 'vitest'
import { simulateMatch, MulberryRng, averageOverall, type MatchSetup } from './engine'
import type { Posture } from '../domain/types'

const baseSetup: MatchSetup = {
  matchId: 12345,
  homeName: 'Goal Father FC',
  awayName: 'Atlético Bonsucesso',
  homeStrength: 78,
  awayStrength: 75,
  homeSquad: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11],
  awaySquad: [101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111],
}

const collect = (setup: MatchSetup, seed = setup.matchId) =>
  Array.from(simulateMatch(setup, new MulberryRng(seed)))

describe('MulberryRng', () => {
  it('mesma seed gera mesma sequencia', () => {
    const a = new MulberryRng(42)
    const b = new MulberryRng(42)
    for (let i = 0; i < 100; i++) {
      expect(a.next()).toBe(b.next())
    }
  })

  it('seeds diferentes geram sequencias diferentes', () => {
    const a = new MulberryRng(1)
    const b = new MulberryRng(2)
    expect(a.next()).not.toBe(b.next())
  })
})

describe('simulateMatch', () => {
  it('primeiro evento sempre eh KickOff com metadata dos times', () => {
    const [first] = collect(baseSetup)
    expect(first?.type).toBe('KickOff')
    if (first?.type === 'KickOff') {
      expect(first.homeClubName).toBe('Goal Father FC')
      expect(first.awayClubName).toBe('Atlético Bonsucesso')
      expect(first.homeStrength).toBe(78)
      expect(first.awayStrength).toBe(75)
    }
  })

  it('ultimo evento sempre eh FullTime no minuto 90', () => {
    const events = collect(baseSetup)
    const last = events[events.length - 1]
    expect(last?.type).toBe('FullTime')
    expect(last?.minute).toBe(90)
  })

  it('total de gols casa com placar do FullTime', () => {
    const events = collect(baseSetup)
    const homeGoals = events.filter((e) => e.type === 'Goal' && e.home).length
    const awayGoals = events.filter((e) => e.type === 'Goal' && !e.home).length
    const last = events[events.length - 1]
    if (last?.type === 'FullTime') {
      expect(last.homeGoals).toBe(homeGoals)
      expect(last.awayGoals).toBe(awayGoals)
    }
  })

  it('mesma matchId produz mesma sequencia (determinismo)', () => {
    const a = collect(baseSetup)
    const b = collect(baseSetup)
    expect(a).toEqual(b)
  })

  it('minutos sao monotonicamente nao-decrescentes', () => {
    const events = collect(baseSetup)
    for (let i = 1; i < events.length; i++) {
      expect(events[i]!.minute).toBeGreaterThanOrEqual(events[i - 1]!.minute)
    }
  })

  it('respeita o intervalo de minutos 0..90', () => {
    const events = collect(baseSetup)
    for (const e of events) {
      expect(e.minute).toBeGreaterThanOrEqual(0)
      expect(e.minute).toBeLessThanOrEqual(90)
    }
  })
})

// ─── Tática (issue #56) — espelha MatchSimulatorTest.kt ────────────────────
describe('simulateMatch com postura', () => {
  const withPostures = (home: Posture, away: Posture): MatchSetup => ({
    ...baseSetup,
    homeTactics: { posture: home, formation: '4-4-2' },
    awayTactics: { posture: away, formation: '4-4-2' },
  })

  const totalGoals = (setup: MatchSetup, seeds: number): number => {
    let goals = 0
    for (let seed = 1; seed <= seeds; seed++) {
      const events = collect(setup, seed)
      const last = events[events.length - 1]
      if (last?.type === 'FullTime') goals += last.homeGoals + last.awayGoals
    }
    return goals
  }

  it('KickOff carrega a postura de cada lado', () => {
    const [first] = collect(withPostures('OFFENSIVE', 'DEFENSIVE'))
    expect(first?.type).toBe('KickOff')
    if (first?.type === 'KickOff') {
      expect(first.homePosture).toBe('OFFENSIVE')
      expect(first.awayPosture).toBe('DEFENSIVE')
    }
  })

  it('EQUILIBRADA em 4-4-2 preserva a simulacao neutra', () => {
    // Regressão: a tática é camada sobre a engine, não troca dela.
    expect(collect(withPostures('BALANCED', 'BALANCED'), 7)).toEqual(collect(baseSetup, 7))
  })

  it('mesma seed com posturas diferentes produz partidas diferentes', () => {
    expect(collect(withPostures('OFFENSIVE', 'OFFENSIVE'), 42))
      .not.toEqual(collect(baseSetup, 42))
    // ...e reprodutível.
    expect(collect(withPostures('OFFENSIVE', 'OFFENSIVE'), 42))
      .toEqual(collect(withPostures('OFFENSIVE', 'OFFENSIVE'), 42))
  })

  it('jogo aberto rende mais gols que dois times fechados', () => {
    const open = totalGoals(withPostures('OFFENSIVE', 'OFFENSIVE'), 100)
    const closed = totalGoals(withPostures('DEFENSIVE', 'DEFENSIVE'), 100)
    expect(open).toBeGreaterThan(closed)
  })

  it('fechar o time reduz os gols do adversario ofensivo', () => {
    const awayGoals = (home: Posture): number => {
      let goals = 0
      for (let seed = 1; seed <= 100; seed++) {
        const events = collect(withPostures(home, 'OFFENSIVE'), seed)
        const last = events[events.length - 1]
        if (last?.type === 'FullTime') goals += last.awayGoals
      }
      return goals
    }
    expect(awayGoals('DEFENSIVE')).toBeLessThan(awayGoals('OFFENSIVE'))
  })

  it('formacao inclina o placar mesmo com a mesma postura', () => {
    const attacking: MatchSetup = {
      ...baseSetup,
      homeTactics: { posture: 'BALANCED', formation: '4-3-3' },
    }
    const defending: MatchSetup = {
      ...baseSetup,
      homeTactics: { posture: 'BALANCED', formation: '5-3-2' },
    }
    expect(totalGoals(attacking, 100)).toBeGreaterThan(totalGoals(defending, 100))
  })
})

describe('averageOverall', () => {
  it('media simples', () => {
    expect(averageOverall([{ overall: 80 }, { overall: 60 }])).toBe(70)
  })

  it('default para 60 em elenco vazio', () => {
    expect(averageOverall([])).toBe(60)
  })
})
