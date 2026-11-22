import { describe, expect, it } from 'vitest'
import type { Player } from '../domain/types'
import {
  MulberryRng,
  contractFor,
  contractTurnover,
  isInFinalSeason,
  maxContractSeasons,
  playerResponseOf,
  releasedPlayer,
  renewalCooldownOf,
  salaryDemandOf,
  squadReputationOf,
  seasonEndOf,
  typicalContractSeasons,
} from './engine'

// Espelho dos testes de domain/rules/ContractRules.kt (issue #136).

const player = (id: number, end: string, age = 25): Player => ({
  id, name: `P${id}`, position: 'MF', overall: 70, pace: 70, shooting: 70, passing: 70, defending: 70,
  stamina: 100, salary: 10_000_00, age, goals: 0, yellowCards: 0, redCards: 0,
  availability: { type: 'Available' }, suspensions: [], star: false,
  nationality: { code: 'BRA', name: 'Brasil', confederation: 'CONMEBOL', flag: '🇧🇷' },
  contract: { start: '2024-01-01', end, finalSeason: false },
})

describe('contratos do mock', () => {
  it('termina no fim da temporada da liga do clube', () => {
    expect(seasonEndOf(2026, 'BRA')).toBe('2026-12-31')
    expect(seasonEndOf(2026, 'ENG')).toBe('2027-05-31')
    expect(contractFor('2026-03-10', 2026, 2, 'BRA').end).toBe('2027-12-31')
  })

  it('menor de 18 assina no máximo 3 temporadas, e a duração típica respeita a regra', () => {
    expect(maxContractSeasons(17)).toBe(3)
    expect(maxContractSeasons(18)).toBe(5)
    const rng = new MulberryRng(136)
    for (let age = 15; age <= 40; age++) {
      const seasons = typicalContractSeasons(age, rng)
      expect(seasons).toBeGreaterThanOrEqual(1)
      expect(seasons).toBeLessThanOrEqual(maxContractSeasons(age))
    }
  })

  it('último ano = passou do término menos um ano', () => {
    expect(isInFinalSeason({ end: '2027-12-31' }, '2027-03-10')).toBe(true)
    expect(isInFinalSeason({ end: '2027-12-31' }, '2026-12-31')).toBe(false)
  })

  it('virada do humano solta quem venceu sem contrato nem salário; a IA renova', () => {
    const squad = [player(1, '2026-12-31'), player(2, '2027-12-31')]

    const human = contractTurnover(squad, 2026, 'BRA', true, new MulberryRng(1))
    expect(human.squad.map((p) => p.id)).toEqual([2])
    expect(human.released).toHaveLength(1)
    expect(human.released[0]).not.toHaveProperty('contract')
    expect(human.released[0]!.salary).toBe(0)

    const ai = contractTurnover(squad, 2026, 'BRA', false, new MulberryRng(1))
    expect(ai.released).toEqual([])
    expect(ai.squad[0]!.contract!.end > '2026-12-31').toBe(true)
    expect(ai.squad[0]!.contract!.start).toBe('2027-01-01')
  })

  it('jogador liberado perde o campo contract', () => {
    expect(releasedPlayer(player(1, '2026-12-31'))).not.toHaveProperty('contract')
  })
})

// Espelho de domain/rules/ContractNegotiationRules.kt (issue #138).
describe('termos pessoais do mock', () => {
  const ref = { clubId: 1, reputation: 70, leagueStrength: 70 }
  const p = player(1, '2027-12-31', 25)

  it('exigência na referência é o salário do valor, e abaixo dela é LOW_SALARY', () => {
    const demand = salaryDemandOf(p, ref, null)
    expect(playerResponseOf(p, { salary: demand - 10_00, years: 3 }, ref, null)).toMatchObject({ reason: 'LOW_SALARY', demandedSalary: demand, minYears: 1, maxYears: 4 })
    expect(playerResponseOf(p, { salary: demand, years: 3 }, ref, null)).toBeNull()
    expect(playerResponseOf(p, { salary: demand * 10, years: 5 }, ref, null)?.reason).toBe('TOO_LONG')
  })

  it('descer para clube muito mais fraco cobra +50%', () => {
    const strong = { clubId: 2, reputation: 80, leagueStrength: 70 }
    const weak = { clubId: 1, reputation: 74, leagueStrength: 70 }
    const base = salaryDemandOf(p, weak, strong)
    const refusal = playerResponseOf(p, { salary: base, years: 2 }, weak, strong)
    expect(refusal?.reason).toBe('WEAKER_CLUB')
    expect(playerResponseOf(p, { salary: refusal!.demandedSalary, years: 2 }, weak, strong)).toBeNull()
  })

  it('renovação não aceita redução e oferta ofensiva esfria 4 semanas', () => {
    expect(salaryDemandOf({ ...p, salary: 999_000_00 }, ref, ref)).toBe(999_000_00)
    const refusal = { reason: 'LOW_SALARY' as const, demandedSalary: 4_000_00, minYears: 1, maxYears: 4 }
    expect(renewalCooldownOf({ salary: 2_960_00, years: 2 }, refusal, 3_500_00, '2026-03-10')).toBe('2026-04-07')
    expect(renewalCooldownOf({ salary: 3_000_00, years: 2 }, refusal, 3_500_00, '2026-03-10')).toBeUndefined()
    // Mantendo o que ele já ganha, não ofende.
    expect(renewalCooldownOf({ salary: 2_000_00, years: 2 }, refusal, 2_000_00, '2026-03-10')).toBeUndefined()
  })

  it('reputação é a média dos 11 melhores', () => {
    expect(squadReputationOf([{ overall: 60 }, { overall: 70 }])).toBe(65)
    expect(squadReputationOf([])).toBe(0)
  })
})
