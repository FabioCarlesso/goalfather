import { describe, expect, it } from 'vitest'
import { isoWeekOf, visibleSeasonRounds } from './worldNavigation'
import { seasonSlots, state } from './seed'

describe('navegação do mundo no mock (issue #133)', () => {
  it('semana ISO: a quinta-feira decide o ano', () => {
    expect(isoWeekOf(Date.UTC(2025, 11, 29))).toEqual({ year: 2026, week: 1 })
    expect(isoWeekOf(Date.UTC(2026, 1, 2))).toEqual({ year: 2026, week: 6 })
    expect(isoWeekOf(Date.UTC(2026, 11, 28))).toEqual({ year: 2026, week: 53 })
  })

  it('da copa só se conhece o PRIMEIRO encaixe adiante, como no backend', () => {
    const slots = seasonSlots(state.currentRound.season)
    const rounds = visibleSeasonRounds()
    expect(rounds).toHaveLength(slots.length)
    const cupRounds = rounds.filter((_, i) => slots[i]!.stage.type === 'Cup')
    // O encaixe corrente é de liga: a fase em disputa aparece no 1º encaixe de
    // copa adiante, e os seguintes vêm vazios (fase ainda não sorteada).
    expect(cupRounds[0]!.matches.length).toBeGreaterThan(0)
    expect(cupRounds.slice(1).every((r) => r.matches.length === 0)).toBe(true)
  })
})
