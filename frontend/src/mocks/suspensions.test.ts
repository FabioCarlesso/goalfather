import { describe, expect, it } from 'vitest'
import { applyMatchDiscipline, isForeignAt, isSuspendedIn, startingEleven } from './engine'
import { myClub } from './seed'
import type { Player } from '../domain/types'

// Espelho do SuspensionRules.kt no mock (issue #130).
describe('suspensões e estrangeiros do mock (issue #130)', () => {
  const squad: Player[] = myClub.squad
  const p1 = squad[0]!
  const yellow = (id: number) => new Map([[id, { yellow: 1, red: 0 }]])

  it('o 3º amarelo numa competição suspende só nela', () => {
    let state = { squad, pending: {} }
    for (let i = 0; i < 3; i++) {
      state = applyMatchDiscipline(state.squad, 'liga-d1', 'Liga — Divisão 1', yellow(p1.id), state.pending)
    }
    const after = state.squad.find((p) => p.id === p1.id)!
    expect(isSuspendedIn(after, 'liga-d1')).toBe(true)
    expect(isSuspendedIn(after, 'copa')).toBe(false)
  })

  it('amarelos de competições diferentes não se somam', () => {
    let state = { squad, pending: {} }
    state = applyMatchDiscipline(state.squad, 'liga-d1', 'Liga', yellow(p1.id), state.pending)
    state = applyMatchDiscipline(state.squad, 'liga-d1', 'Liga', yellow(p1.id), state.pending)
    state = applyMatchDiscipline(state.squad, 'copa', 'Copa', yellow(p1.id), state.pending)
    expect(state.squad.find((p) => p.id === p1.id)!.suspensions).toEqual([])
  })

  it('vermelho suspende 1 jogo, cumprido no jogo seguinte da competição', () => {
    const red = new Map([[p1.id, { yellow: 0, red: 1 }]])
    const sent = applyMatchDiscipline(squad, 'copa', 'Copa', red, {})
    expect(sent.squad.find((p) => p.id === p1.id)!.suspensions).toEqual([
      { competitionId: 'copa', competitionName: 'Copa', matchesLeft: 1 },
    ])
    const served = applyMatchDiscipline(sent.squad, 'copa', 'Copa', new Map(), sent.pending)
    expect(served.squad.find((p) => p.id === p1.id)!.suspensions).toEqual([])
  })

  it('apito inicial tira o suspenso e respeita o limite de estrangeiros', () => {
    const suspended = squad.map((p) =>
      p.id === p1.id ? { ...p, suspensions: [{ competitionId: 'liga-d1', competitionName: 'Liga', matchesLeft: 1 }] } : p,
    )
    const rules = {
      suspended: (p: Player) => isSuspendedIn(p, 'liga-d1'),
      foreign: (p: Player) => isForeignAt(p, 'BRA'),
      foreignLimit: 9,
    }
    expect(startingEleven(suspended, squad.map((p) => p.id), rules).has(p1.id)).toBe(false)

    const allForeign = squad.map((p) => ({ ...p, nationality: { ...p.nationality, code: 'ARG' } }))
    const onField = startingEleven(allForeign, undefined, rules)
    expect(onField.size).toBe(9)
  })
})
