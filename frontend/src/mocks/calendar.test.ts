import { describe, expect, it } from 'vitest'
import { matchdayOf, matchdaySerial, seasonMatchday } from './calendar'

describe('calendário do mock (issue #125)', () => {
  it('encaixes caem na quarta e no domingo da semana ISO, como no backend', () => {
    // Mesmas datas de MatchdayTest.kt: a semana 1 de 2026 começa em 29/12/2025.
    expect(matchdayOf(2026, 1, 'WEEKEND')).toEqual({
      key: '2026-W01-WEEKEND', year: 2026, week: 1, slot: 'WEEKEND', date: '2026-01-04',
    })
    expect(matchdayOf(2026, 5, 'MIDWEEK').date).toBe('2026-01-28')
    expect(matchdayOf(2026, 5, 'WEEKEND').date).toBe('2026-02-01')
  })

  it('serial cresce um por encaixe', () => {
    const mid = matchdayOf(2026, 5, 'MIDWEEK')
    const end = matchdayOf(2026, 5, 'WEEKEND')
    expect(matchdaySerial(end)).toBe(matchdaySerial(mid) + 1)
    expect(matchdaySerial(matchdayOf(2026, 6, 'MIDWEEK'))).toBe(matchdaySerial(end) + 1)
  })

  it('espalha as datas da temporada pelo ano, em ordem', () => {
    const keys = Array.from({ length: 9 }, (_, i) => seasonMatchday(2026, i, 9, i % 2 ? 'MIDWEEK' : 'WEEKEND'))
    expect(keys[0]!.key).toBe('2026-W01-WEEKEND')
    expect(keys.map((m) => m.date)).toEqual([...keys.map((m) => m.date)].sort())
    expect(keys.every((m) => m.date.startsWith('2026'))).toBe(true)
  })
})
