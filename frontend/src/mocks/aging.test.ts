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
  ageBandOf,
  ageSquadOneSeason,
} from './engine'

const guy = (id: number, age: number, overall: number, salary = 10_000_00) => ({
  id,
  age,
  overall,
  pace: overall,
  shooting: overall,
  passing: overall,
  defending: overall,
  salary,
})

/** Sorteios repetidos: as asserções de faixa são propriedades, não um caso. */
const seeds = Array.from({ length: 200 }, (_, i) => i + 1)

describe('faixas etárias', () => {
  it('classifica pela idade nova', () => {
    expect(ageBandOf(YOUNG_MAX_AGE)).toBe('YOUNG')
    expect(ageBandOf(YOUNG_MAX_AGE + 1)).toBe('PEAK')
    expect(ageBandOf(PEAK_MAX_AGE)).toBe('PEAK')
    expect(ageBandOf(PEAK_MAX_AGE + 1)).toBe('VETERAN')
  })

  it('jovem nunca regride mais do que o piso da faixa', () => {
    for (const seed of seeds) {
      const [after] = ageSquadOneSeason([guy(1, 19, 70)], new MulberryRng(seed))
      expect(after!.overall - 70).toBeGreaterThanOrEqual(AGE_BAND_DELTA.YOUNG.min)
    }
  })

  it('veterano nunca evolui mais do que o teto da faixa', () => {
    for (const seed of seeds) {
      const [after] = ageSquadOneSeason([guy(1, 33, 75)], new MulberryRng(seed))
      expect(after!.overall - 75).toBeLessThanOrEqual(AGE_BAND_DELTA.VETERAN.max)
    }
  })

  it('jovem tende a evoluir e veterano a regredir no agregado', () => {
    const sum = (age: number) =>
      seeds.reduce((acc, seed) => {
        const [after] = ageSquadOneSeason([guy(1, age, 75)], new MulberryRng(seed))
        return acc + (after!.overall - 75)
      }, 0)

    expect(sum(19)).toBeGreaterThan(0)
    expect(sum(32)).toBeLessThan(0)
  })

  it('atributos acompanham a variação do overall', () => {
    const before = guy(1, 19, 70)
    const [after] = ageSquadOneSeason([before], new MulberryRng(3))
    const delta = after!.overall - before.overall

    expect(after!.pace).toBe(before.pace + delta)
    expect(after!.shooting).toBe(before.shooting + delta)
    expect(after!.passing).toBe(before.passing + delta)
    expect(after!.defending).toBe(before.defending + delta)
  })

  it('overall respeita o intervalo 0..99 do contrato', () => {
    for (const seed of seeds) {
      const aged = ageSquadOneSeason([guy(1, 18, 99), guy(2, 33, 0)], new MulberryRng(seed))
      for (const p of aged) {
        expect(p.overall).toBeGreaterThanOrEqual(0)
        expect(p.overall).toBeLessThanOrEqual(99)
      }
    }
  })
})

describe('idade e aposentadoria', () => {
  it('todo jogador ganha um ano na virada', () => {
    const aged = ageSquadOneSeason([guy(1, 20, 70), guy(2, 28, 80)], new MulberryRng(7))
    expect(aged.map((p) => p.age)).toEqual([21, 29])
  })

  it('veterano de overall baixo sai do elenco e leva a folha junto', () => {
    const squad = [
      guy(1, 25, 80, 30_000_00),
      guy(2, 38, RETIREMENT_MAX_OVERALL - 15, 12_000_00), // aposenta
      guy(3, 20, 70, 8_000_00),
    ]

    const aged = ageSquadOneSeason(squad, new MulberryRng(9))

    expect(aged.map((p) => p.id)).toEqual([1, 3])
    expect(aged.reduce((acc, p) => acc + p.salary, 0)).toBe(38_000_00)
  })

  it('veterano que ainda rende segue jogando', () => {
    for (const seed of seeds) {
      const aged = ageSquadOneSeason([guy(1, RETIREMENT_MIN_AGE + 1, 90)], new MulberryRng(seed))
      expect(aged).toHaveLength(1)
    }
  })

  it('o teto duro de carreira aposenta até quem ainda rende', () => {
    const aged = ageSquadOneSeason([guy(1, FORCED_RETIREMENT_AGE, 99)], new MulberryRng(1))
    expect(aged).toHaveLength(0)
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

    expect(ageSquadOneSeason(squad, new MulberryRng(1)).map((p) => p.overall))
      .not.toEqual(ageSquadOneSeason(squad, new MulberryRng(2)).map((p) => p.overall))
  })
})
