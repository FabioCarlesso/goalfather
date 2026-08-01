// Testes do mock de fadiga/lesões (issue #54) — garantem que a engine de mock
// espelha FitnessRules.kt do backend até o backend assumir de vez. São os
// mesmos invariantes de FitnessRulesTest.kt, na forma TS.

import { describe, expect, it } from 'vitest'
import {
  MulberryRng,
  applyRoundFitness,
  applyMedicalTreatment,
  effectiveOverall,
  averageOverall,
  simulateMatch,
  BENCH_STAMINA_RECOVERY,
  INJURY_DURATION_MIN,
  INJURY_DURATION_MAX,
  MEDICAL_STAMINA_RECOVERY,
  STAMINA_FULL_PERFORMANCE,
  STAMINA_MATCH_FLOOR,
  STARTER_STAMINA_LOSS_MIN,
  STARTER_STAMINA_LOSS_MAX,
} from './engine'
import type { Availability } from '../domain/types'

const fit = (id: number, stamina = 100, availability: Availability = { type: 'Available' }) =>
  ({ id, stamina, availability })

describe('desgaste de rodada', () => {
  it('titular perde stamina dentro da faixa do protótipo', () => {
    const squad = [1, 2, 3].map((id) => fit(id))
    const after = applyRoundFitness(squad, new Set([1, 2, 3]), new Map(), new MulberryRng(42))

    for (const p of after) {
      const lost = 100 - p.stamina
      expect(lost).toBeGreaterThanOrEqual(STARTER_STAMINA_LOSS_MIN)
      expect(lost).toBeLessThanOrEqual(STARTER_STAMINA_LOSS_MAX)
    }
  })

  it('reserva recupera respeitando o teto de 100', () => {
    const after = applyRoundFitness(
      [fit(1, 50), fit(2, 95)],
      new Set(),
      new Map(),
      new MulberryRng(1),
    )

    expect(after[0]!.stamina).toBe(50 + BENCH_STAMINA_RECOVERY)
    expect(after[1]!.stamina).toBe(100)
  })

  it('desgaste respeita o piso e nunca zera o jogador', () => {
    const after = applyRoundFitness(
      [fit(1, STAMINA_MATCH_FLOOR)],
      new Set([1]),
      new Map(),
      new MulberryRng(7),
    )

    expect(after[0]!.stamina).toBe(STAMINA_MATCH_FLOOR)
  })

  it('mesma seed produz o mesmo desgaste', () => {
    const squad = [1, 2, 3].map((id) => fit(id))
    const a = applyRoundFitness(squad, new Set([1, 2, 3]), new Map(), new MulberryRng(2026))
    const b = applyRoundFitness(squad, new Set([1, 2, 3]), new Map(), new MulberryRng(2026))

    expect(a.map((p) => p.stamina)).toEqual(b.map((p) => p.stamina))
  })
})

describe('lesões com duração', () => {
  it('lesão nova entra em vigor com a duração do evento', () => {
    const after = applyRoundFitness([fit(1)], new Set([1]), new Map([[1, 3]]), new MulberryRng(1))

    expect(after[0]!.availability).toEqual({ type: 'Injured', roundsOut: 3 })
  })

  it('lesão sofrida na rodada não é decrementada na mesma virada', () => {
    const after = applyRoundFitness([fit(1)], new Set([1]), new Map([[1, 1]]), new MulberryRng(1))

    expect(after[0]!.availability).toEqual({ type: 'Injured', roundsOut: 1 })
  })

  it('lesão em curso decrementa até liberar o jogador', () => {
    let player = fit(1, 100, { type: 'Injured', roundsOut: 2 })

    player = applyRoundFitness([player], new Set(), new Map(), new MulberryRng(1))[0]!
    expect(player.availability).toEqual({ type: 'Injured', roundsOut: 1 })

    player = applyRoundFitness([player], new Set(), new Map(), new MulberryRng(1))[0]!
    expect(player.availability).toEqual({ type: 'Available' })
  })

  it('lesão nova nunca encurta a que já estava em curso', () => {
    const after = applyRoundFitness(
      [fit(1, 100, { type: 'Injured', roundsOut: 4 })],
      new Set(),
      new Map([[1, 2]]),
      new MulberryRng(1),
    )

    expect(after[0]!.availability).toEqual({ type: 'Injured', roundsOut: 3 })
  })

  it('engine sorteia a duração dentro da faixa', () => {
    const setup = {
      matchId: 1,
      homeName: 'A', awayName: 'B',
      homeStrength: 75, awayStrength: 75,
      homeSquad: [1, 2, 3], awaySquad: [4, 5, 6],
    }
    const injuries = Array.from({ length: 60 }, (_, i) => i + 1).flatMap((matchId) =>
      [...simulateMatch({ ...setup, matchId })].filter((e) => e.type === 'Injury'),
    )

    expect(injuries.length).toBeGreaterThan(0)
    for (const injury of injuries) {
      expect(injury.roundsOut).toBeGreaterThanOrEqual(INJURY_DURATION_MIN)
      expect(injury.roundsOut).toBeLessThanOrEqual(INJURY_DURATION_MAX)
    }
  })
})

describe('stamina × força do time', () => {
  it('rendimento é integral acima do piso de forma', () => {
    expect(effectiveOverall({ overall: 80, stamina: 100 })).toBe(80)
    expect(effectiveOverall({ overall: 80, stamina: STAMINA_FULL_PERFORMANCE })).toBe(80)
  })

  it('rendimento cai proporcionalmente abaixo do piso', () => {
    expect(effectiveOverall({ overall: 80, stamina: 35 })).toBe((80 * 35) / STAMINA_FULL_PERFORMANCE)
  })

  it('elencos idênticos com staminas distintas têm forças distintas', () => {
    const rested = Array.from({ length: 11 }, () => ({ overall: 80, stamina: 100 }))
    const worn = Array.from({ length: 11 }, () => ({ overall: 80, stamina: 40 }))

    expect(averageOverall(rested)).toBe(80)
    expect(averageOverall(worn)).toBeLessThan(averageOverall(rested))
  })
})

describe('departamento médico', () => {
  it('devolve stamina respeitando o teto e encurta a lesão', () => {
    const treated = applyMedicalTreatment([
      fit(1, 40),
      fit(2, 95),
      fit(3, 60, { type: 'Injured', roundsOut: 3 }),
    ])

    expect(treated[0]!.stamina).toBe(40 + MEDICAL_STAMINA_RECOVERY)
    expect(treated[1]!.stamina).toBe(100)
    expect(treated[2]!.availability).toEqual({ type: 'Injured', roundsOut: 2 })
  })

  it('tratamento na última rodada de lesão libera o jogador', () => {
    const treated = applyMedicalTreatment([fit(1, 100, { type: 'Injured', roundsOut: 1 })])

    expect(treated[0]!.availability).toEqual({ type: 'Available' })
  })
})
