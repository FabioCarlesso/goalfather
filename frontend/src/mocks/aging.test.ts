// Testes do mock de envelhecimento (issue #55) — garantem que a engine de mock
// espelha AgingRules.kt do backend até o backend assumir de vez. São os mesmos
// invariantes de AgingRulesTest.kt, na forma TS.

import { describe, expect, it } from 'vitest'
import {
  AGE_BAND_DELTA,
  FORCED_RETIREMENT_AGE,
  MulberryRng,
  PEAK_MAX_AGE,
  RETIREMENT_MAX_OVERALL,
  RETIREMENT_MIN_AGE,
  YOUNG_MAX_AGE,
  YOUTH_AGE_MAX,
  YOUTH_AGE_MIN,
  YOUTH_INTAKE_MAX,
  YOUTH_INTAKE_MIN,
  YOUTH_SALARY_CENTS,
  ageBandOf,
  ageSquadOneSeason,
  youthIntake,
} from './engine'
import { WORLD_COUNTRIES, mockPlayerName } from './world'
import type { Player } from '../domain/types'

const guy = (
  id: number,
  age: number,
  overall: number,
  salary = 10_000_00,
  position: Player['position'] = 'MF',
): Player => ({
  id,
  name: `Player ${id}`,
  position,
  age,
  overall,
  pace: overall,
  shooting: overall,
  passing: overall,
  defending: overall,
  stamina: 100,
  salary,
  goals: 0,
  yellowCards: 0,
  redCards: 0,
  availability: { type: 'Available' }, suspensions: [],
  star: overall >= 82,
})

const BRAZIL = WORLD_COUNTRIES.BRA
const NAME = (rng: MulberryRng) => mockPlayerName(BRAZIL, rng)

/** Sorteios repetidos: as asserções de faixa são propriedades, não um caso. */
const seeds = Array.from({ length: 200 }, (_, i) => i + 1)

/** Atalho para os testes que só olham o elenco resultante. */
const age1 = (p: Player, seed: number): Player =>
  ageSquadOneSeason([p], new MulberryRng(seed)).squad[0]!

describe('faixas etárias', () => {
  it('classifica pela idade nova', () => {
    expect(ageBandOf(YOUNG_MAX_AGE)).toBe('YOUNG')
    expect(ageBandOf(YOUNG_MAX_AGE + 1)).toBe('PEAK')
    expect(ageBandOf(PEAK_MAX_AGE)).toBe('PEAK')
    expect(ageBandOf(PEAK_MAX_AGE + 1)).toBe('VETERAN')
  })

  it('jovem nunca regride mais do que o piso da faixa', () => {
    for (const seed of seeds) {
      expect(age1(guy(1, 19, 70), seed).overall - 70).toBeGreaterThanOrEqual(AGE_BAND_DELTA.YOUNG.min)
    }
  })

  it('veterano nunca evolui mais do que o teto da faixa', () => {
    for (const seed of seeds) {
      expect(age1(guy(1, 33, 75), seed).overall - 75).toBeLessThanOrEqual(AGE_BAND_DELTA.VETERAN.max)
    }
  })

  it('jovem tende a evoluir e veterano a regredir no agregado', () => {
    const sum = (age: number) =>
      seeds.reduce((acc, seed) => acc + (age1(guy(1, age, 75), seed).overall - 75), 0)

    expect(sum(19)).toBeGreaterThan(0)
    expect(sum(32)).toBeLessThan(0)
  })

  it('atributos acompanham a variação do overall', () => {
    const before = guy(1, 19, 70)
    const after = age1(before, 3)
    const delta = after.overall - before.overall

    expect(after.pace).toBe(before.pace + delta)
    expect(after.shooting).toBe(before.shooting + delta)
    expect(after.passing).toBe(before.passing + delta)
    expect(after.defending).toBe(before.defending + delta)
  })

  it('overall respeita o intervalo 0..99 do contrato', () => {
    for (const seed of seeds) {
      const { squad } = ageSquadOneSeason([guy(1, 18, 99), guy(2, 33, 0)], new MulberryRng(seed))
      for (const p of squad) {
        expect(p.overall).toBeGreaterThanOrEqual(0)
        expect(p.overall).toBeLessThanOrEqual(99)
      }
    }
  })
})

describe('idade e aposentadoria', () => {
  it('todo jogador ganha um ano na virada', () => {
    const { squad } = ageSquadOneSeason([guy(1, 20, 70), guy(2, 28, 80)], new MulberryRng(7))
    expect(squad.map((p) => p.age)).toEqual([21, 29])
  })

  it('veterano de overall baixo sai do elenco sem reposição 1:1 (issue 139)', () => {
    const squad = [
      guy(1, 25, 80, 30_000_00),
      guy(2, 38, RETIREMENT_MAX_OVERALL - 15, 12_000_00, 'GK'), // aposenta
      guy(3, 20, 70, 8_000_00),
    ]

    const turn = ageSquadOneSeason(squad, new MulberryRng(9))

    expect(turn.squad.map((p) => p.id)).toEqual([1, 3])
    expect(turn.retirements.map((r) => r.retired.id)).toEqual([2])
  })

  it('a base sobe de 2 a 4 garotos na posição mais carente (issue 139)', () => {
    const squad = Array.from({ length: 11 }, (_, i) => guy(i + 1, 25, 70))
    for (const seed of seeds) {
      const youths = youthIntake(squad, new MulberryRng(seed), (slot) => 900 + slot, undefined, BRAZIL, NAME)
      expect(youths.length).toBeGreaterThanOrEqual(YOUTH_INTAKE_MIN)
      expect(youths.length).toBeLessThanOrEqual(YOUTH_INTAKE_MAX)
      for (const y of youths) {
        expect(y.age).toBeGreaterThanOrEqual(YOUTH_AGE_MIN)
        expect(y.age).toBeLessThanOrEqual(YOUTH_AGE_MAX)
        expect(y.salary).toBe(YOUTH_SALARY_CENTS)
        expect(y.position).not.toBe('MF') // 11 meias: o garoto vem de outra posição
      }
    }
    // Clube humano cheio não recebe ninguém.
    expect(youthIntake(squad, new MulberryRng(1), (s) => s, undefined, BRAZIL, NAME, 11)).toEqual([])
  })

  it('veterano que ainda rende segue jogando', () => {
    for (const seed of seeds) {
      const turn = ageSquadOneSeason([guy(1, RETIREMENT_MIN_AGE + 1, 90)], new MulberryRng(seed))
      expect(turn.retirements).toHaveLength(0)
    }
  })

  it('o teto duro de carreira aposenta até quem ainda rende', () => {
    const turn = ageSquadOneSeason([guy(1, FORCED_RETIREMENT_AGE, 99)], new MulberryRng(1))

    expect(turn.retirements).toHaveLength(1)
    // Ganha o ano como todo mundo — o extrato não mente a idade de quem parou.
    expect(turn.retirements[0]!.retired.age).toBe(FORCED_RETIREMENT_AGE + 1)
  })

  it('vinte temporadas seguidas não esvaziam o elenco', () => {
    // Regressão do achado da review: sem a base, um elenco todo da mesma idade
    // zerava por volta da décima primeira temporada. Desde a #139 a base sobe
    // de 2 a 4 garotos por ano, haja aposentadoria ou não.
    let squad = Array.from({ length: 11 }, (_, i) => guy(i + 1, 25, 70))

    for (let season = 2026; season <= 2045; season++) {
      const aged = ageSquadOneSeason(squad, new MulberryRng(season), 1).squad
      squad = [...aged, ...youthIntake(aged, new MulberryRng(season + 7), (slot) => season * 1_000 + slot, undefined, BRAZIL, NAME)]
      expect(squad.length).toBeGreaterThanOrEqual(11)
    }
    expect(squad.some((p) => p.id <= 11)).toBe(false)
  })
})

describe('determinismo', () => {
  it('mesma seed produz exatamente a mesma virada', () => {
    const squad = [guy(1, 19, 70), guy(2, 26, 80), guy(3, 31, 75)]

    expect(ageSquadOneSeason(squad, new MulberryRng(2026)))
      .toEqual(ageSquadOneSeason(squad, new MulberryRng(2026)))
  })

  it('seeds diferentes produzem viradas diferentes', () => {
    const squad = Array.from({ length: 20 }, (_, i) => guy(i + 1, 20, 70))

    expect(ageSquadOneSeason(squad, new MulberryRng(1)).squad.map((p) => p.overall))
      .not.toEqual(ageSquadOneSeason(squad, new MulberryRng(2)).squad.map((p) => p.overall))
  })
})
