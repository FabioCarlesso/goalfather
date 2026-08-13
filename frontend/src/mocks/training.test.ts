// Testes do mock de treino semanal (issue #58) — garantem que a engine de
// mock espelha TrainingRules.kt do backend até o backend assumir de vez. São
// os mesmos invariantes de TrainingRulesTest.kt, na forma TS.

import { describe, expect, it } from 'vitest'
import {
  MulberryRng,
  STARTER_STAMINA_LOSS_MIN,
  TRAINING_ATTRIBUTE_GAIN,
  TRAINING_FOCUS_EFFECTS,
  TRAINING_INJURY_ROUNDS,
  TRAINING_UPGRADE_CHANCE,
  trainSquad,
} from './engine'
import type { Player, TrainingFocus } from '../domain/types'

const player = (id: number, age: number, overall = 70, stamina = 100): Player => ({
  id,
  name: `Player ${id}`,
  position: 'MF',
  overall,
  pace: overall,
  shooting: overall,
  passing: overall,
  defending: overall,
  stamina,
  salary: 10_000_00,
  age,
  goals: 0,
  yellowCards: 0,
  redCards: 0,
  availability: { type: 'Available' },
  star: overall >= 82,
})

const squadAged = (age: number, size = 20, overall = 70, stamina = 100): Player[] =>
  Array.from({ length: size }, (_, i) => player(i + 1, age, overall, stamina))

const improvements = (focus: TrainingFocus, squad: Player[], seeds = 50): number => {
  let count = 0
  for (let seed = 1; seed <= seeds; seed++) {
    count += trainSquad(squad, focus, new MulberryRng(seed)).events
      .filter((e) => e.type === 'Improved').length
  }
  return count
}

describe('treino semanal', () => {
  it('descanso recupera stamina de todo o elenco sem produzir eventos', () => {
    const squad = squadAged(20, 5, 70, 50)

    const { squad: after, events } = trainSquad(squad, 'DESCANSO', new MulberryRng(42))

    expect(events).toHaveLength(0)
    for (const p of after) {
      expect(p.stamina).toBe(50 + TRAINING_FOCUS_EFFECTS.DESCANSO.staminaRecovery)
    }
  })

  it('recuperação respeita o teto de 100', () => {
    const { squad } = trainSquad([player(1, 25, 70, 95)], 'DESCANSO', new MulberryRng(1))

    expect(squad[0]!.stamina).toBe(100)
  })

  it('nenhum foco devolve tudo o que uma partida cansa', () => {
    // Se o descanso zerasse o desgaste, a fadiga da issue #54 sumiria — o
    // treino alivia a conta, não a apaga. Mesmo invariante do backend.
    for (const effect of Object.values(TRAINING_FOCUS_EFFECTS)) {
      expect(effect.staminaRecovery).toBeLessThan(STARTER_STAMINA_LOSS_MIN)
    }
  })

  it('foco técnico evolui o atributo do foco e o overall', () => {
    const squad = squadAged(20)
    let seen = 0

    for (let seed = 1; seed <= 50; seed++) {
      const before = new Map(squad.map((p) => [p.id, p]))
      const { events } = trainSquad(squad, 'ATAQUE', new MulberryRng(seed))
      for (const event of events) {
        if (event.type !== 'Improved') continue
        seen++
        const old = before.get(event.player.id)!
        expect(event.attribute).toBe('SHOOTING')
        expect(event.player.shooting).toBe(old.shooting + TRAINING_ATTRIBUTE_GAIN)
        expect(event.player.overall).toBe(old.overall + TRAINING_ATTRIBUTE_GAIN)
        // Atributo que o foco não treina fica intacto.
        expect(event.player.defending).toBe(old.defending)
      }
    }

    expect(seen).toBeGreaterThan(0)
  })

  it('ganho respeita o teto 99 e não inventa evolução no topo', () => {
    const capped = squadAged(18, 20, 99)

    for (let seed = 1; seed <= 30; seed++) {
      const { squad, events } = trainSquad(capped, 'DEFESA', new MulberryRng(seed))

      expect(events.filter((e) => e.type === 'Improved')).toHaveLength(0)
      for (const p of squad) expect(p.overall).toBeLessThanOrEqual(99)
    }
  })

  it('jovens evoluem mais que veteranos com as mesmas seeds', () => {
    expect(improvements('ATAQUE', squadAged(21))).toBeGreaterThan(
      improvements('ATAQUE', squadAged(32)),
    )
    expect(TRAINING_UPGRADE_CHANCE.YOUNG).toBeGreaterThan(TRAINING_UPGRADE_CHANCE.VETERAN)
  })

  it('treino intenso machuca de vez em quando, com afastamento curto', () => {
    const squad = squadAged(25)
    let injuries = 0

    for (let seed = 1; seed <= 50; seed++) {
      for (const event of trainSquad(squad, 'FISICO', new MulberryRng(seed)).events) {
        if (event.type !== 'Injured') continue
        injuries++
        expect(event.roundsOut).toBe(TRAINING_INJURY_ROUNDS)
        expect(event.player.availability).toEqual({
          type: 'Injured',
          roundsOut: TRAINING_INJURY_ROUNDS,
        })
      }
    }

    expect(injuries).toBeGreaterThan(0)
  })

  it('lesionado não treina nem se machuca de novo, só recupera', () => {
    const injured: Player = {
      ...player(1, 19, 70, 40),
      availability: { type: 'Injured', roundsOut: 3 },
    }

    for (let seed = 1; seed <= 30; seed++) {
      const { squad, events } = trainSquad([injured], 'FISICO', new MulberryRng(seed))

      expect(events).toHaveLength(0)
      expect(squad[0]!.overall).toBe(injured.overall)
      // O contador de lesão é responsabilidade de applyRoundFitness.
      expect(squad[0]!.availability).toEqual({ type: 'Injured', roundsOut: 3 })
      expect(squad[0]!.stamina).toBe(40 + TRAINING_FOCUS_EFFECTS.FISICO.staminaRecovery)
    }
  })

  it('mesma seed produz exatamente a mesma semana', () => {
    const squad = squadAged(20)

    const a = trainSquad(squad, 'ATAQUE', new MulberryRng(2026))
    const b = trainSquad(squad, 'ATAQUE', new MulberryRng(2026))

    expect(a).toEqual(b)
  })
})
