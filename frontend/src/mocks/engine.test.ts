import { describe, expect, it } from 'vitest'
import {
  simulateMatch,
  MulberryRng,
  averageOverall,
  drawShooter,
  matchStats,
  EMPTY_MATCH_STATS,
  type MatchSetup,
  type SquadMember,
} from './engine'
import type { MatchEvent, Position, Posture } from '../domain/types'

// 4-4-2: as posições importam desde a issue #57 (peso de quem finaliza).
const FORMATION_4_4_2: Position[] =
  ['GK', 'CB', 'CB', 'CB', 'CB', 'MF', 'MF', 'MF', 'MF', 'FW', 'FW']

const squad = (offset: number): SquadMember[] =>
  FORMATION_4_4_2.map((pos, i) => ({ id: offset + i, pos }))

const baseSetup: MatchSetup = {
  matchId: 12345,
  homeName: 'Goal Father FC',
  awayName: 'Atlético Bonsucesso',
  homeStrength: 78,
  awayStrength: 75,
  homeSquad: squad(1),
  awaySquad: squad(101),
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

// ─── Artilheiro ponderado, Miss e sumário (issue #57) ─────────────────────
// Espelha os testes de MatchSimulatorTest.kt / MatchStatsTest.kt.
describe('simulateMatch: finalizações e sumário', () => {
  const overSeeds = (seeds: number): MatchEvent[] => {
    const all: MatchEvent[] = []
    for (let seed = 1; seed <= seeds; seed++) all.push(...collect(baseSetup, seed))
    return all
  }

  const positionOf = (id: number): Position =>
    [...baseSetup.homeSquad, ...baseSetup.awaySquad].find((p) => p.id === id)!.pos

  it('distribuicao de artilheiros respeita o peso da posicao', () => {
    const scorers = overSeeds(200)
      .filter((e) => e.type === 'Goal')
      .map((e) => positionOf(e.scorerId))

    const count = (pos: Position) => scorers.filter((p) => p === pos).length
    expect(count('GK')).toBe(0)
    expect(count('FW')).toBeGreaterThan(0)
    expect(count('FW')).toBeGreaterThan(count('CB'))
  })

  it('chute para fora sai com autor apto do squad correto', () => {
    const misses = overSeeds(50).filter((e) => e.type === 'Miss')
    expect(misses.length).toBeGreaterThan(0)
    for (const miss of misses) {
      const roster = miss.home ? baseSetup.homeSquad : baseSetup.awaySquad
      expect(roster.some((p) => p.id === miss.playerId)).toBe(true)
      expect(positionOf(miss.playerId)).not.toBe('GK')
    }
  })

  it('defesa identifica o goleiro do lado que defendeu', () => {
    const saves = overSeeds(50).filter((e) => e.type === 'Save')
    expect(saves.length).toBeGreaterThan(0)
    for (const save of saves) {
      const roster = save.home ? baseSetup.homeSquad : baseSetup.awaySquad
      expect(save.goalkeeperId).toBe(roster.find((p) => p.pos === 'GK')!.id)
    }
  })

  it('estatisticas do FullTime batem com os eventos emitidos', () => {
    for (let seed = 1; seed <= 30; seed++) {
      const events = collect(baseSetup, seed)
      const last = events[events.length - 1]!
      expect(last.type).toBe('FullTime')
      if (last.type !== 'FullTime') continue
      expect(last.stats).toEqual(matchStats(events.slice(0, -1)))
      expect(last.stats.home.shotsOnTarget).toBeGreaterThanOrEqual(last.homeGoals)
      expect(last.stats.away.shotsOnTarget).toBeGreaterThanOrEqual(last.awayGoals)
    }
  })

  it('escalacao so de goleiros nao produz finalizacao', () => {
    const keepersOnly: MatchSetup = {
      ...baseSetup,
      homeSquad: Array.from({ length: 11 }, (_, i) => ({ id: 900 + i, pos: 'GK' as Position })),
    }
    const events = Array.from({ length: 30 }, (_, i) => collect(keepersOnly, i + 1)).flat()
    expect(events.some((e) => e.type === 'Goal' && e.home)).toBe(false)
    expect(events.some((e) => e.type === 'Miss' && e.home)).toBe(false)
  })
})

describe('drawShooter', () => {
  it('nunca sorteia goleiro e favorece o atacante', () => {
    const rng = new MulberryRng(7)
    const roster: SquadMember[] = [
      { id: 1, pos: 'GK' },
      { id: 2, pos: 'CB' },
      { id: 3, pos: 'MF' },
      { id: 4, pos: 'FW' },
    ]
    const draws = Array.from({ length: 5_000 }, () => drawShooter(roster, rng)!.pos)
    const count = (pos: Position) => draws.filter((p) => p === pos).length

    expect(count('GK')).toBe(0)
    expect(count('FW')).toBeGreaterThan(count('MF'))
    expect(count('MF')).toBeGreaterThan(count('CB'))
  })

  it('devolve null quando ninguem pode finalizar', () => {
    expect(drawShooter([], new MulberryRng(1))).toBeNull()
    expect(drawShooter([{ id: 1, pos: 'GK' }], new MulberryRng(1))).toBeNull()
  })
})

describe('matchStats', () => {
  it('uma defesa conta para os dois lados', () => {
    const stats = matchStats([{ type: 'Save', minute: 5, goalkeeperId: 1, home: true }])
    expect(stats.home.saves).toBe(1)
    expect(stats.home.shots).toBe(0)
    expect(stats.away.shots).toBe(1)
    expect(stats.away.shotsOnTarget).toBe(1)
  })

  it('separa cartoes por time e ignora eventos neutros', () => {
    const stats = matchStats([
      { type: 'Card', minute: 10, playerId: 2, red: false, home: true },
      { type: 'Card', minute: 20, playerId: 3, red: true, home: false },
      { type: 'Injury', minute: 30, playerId: 4, roundsOut: 2 },
    ])
    expect(stats.home.yellowCards).toBe(1)
    expect(stats.home.redCards).toBe(0)
    expect(stats.away.redCards).toBe(1)
  })

  it('stream vazio produz sumario zerado', () => {
    expect(matchStats([])).toEqual(EMPTY_MATCH_STATS)
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
