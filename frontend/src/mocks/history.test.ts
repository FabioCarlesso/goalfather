// Testes do mock de histórico de temporadas (issue #60) — garantem que a
// engine de mock espelha SeasonHistoryRules.kt do backend, inclusive a ordem
// que é o critério de aceite da issue: snapshot ANTES do reset das estatísticas.

import { beforeEach, describe, expect, it } from 'vitest'
import { careerOf, seasonRecordOf, startNewSeason, state } from './seed'
import type { SeasonRecord, Standings } from '../domain/types'

const table = (division: number, clubIds: number[], points: number[]): Standings => ({
  season: 2026,
  division,
  round: 5,
  promotionSpots: division === 1 ? 0 : 2,
  relegationSpots: division === 1 ? 2 : 0,
  rows: clubIds.map((clubId, i) => ({
    position: i + 1,
    clubId,
    clubName: `Club ${clubId}`,
    played: 5, wins: 0, draws: 0, losses: 0,
    goalsFor: 0, goalsAgainst: 0, goalDifference: 0,
    points: points[i] ?? 0,
  })),
})

const finalTables = [
  table(2, [7, 8, 9, 10, 11, 12], [15, 12, 9, 6, 3, 0]),
  table(1, [3, 1, 2, 4, 5, 6], [15, 12, 9, 6, 3, 0]),
]

describe('mock de histórico (issue #60)', () => {
  beforeEach(() => {
    state.history = []
  })

  it('monta o record com campeão da elite e classificação na ordem de exibição', () => {
    const record = seasonRecordOf(2026, finalTables)

    expect(record.season).toBe(2026)
    expect(record.champion.row.clubId).toBe(3)
    expect(record.champion.division).toBe(1)
    // Elite inteira primeiro, depois a segunda divisão.
    expect(record.finalStandings.map((s) => s.division)).toEqual([1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2])
    expect(record.finalStandings[0]!.row.clubId).toBe(3)
  })

  it('aproveitamento é a fração dos pontos disputados', () => {
    const record = seasonRecordOf(2026, finalTables)

    // 15 pontos em 5 jogos = 100%; 12/15 = 80%; 0 pontos = 0%.
    expect(record.finalStandings[0]!.pointsPercentage).toBe(100)
    expect(record.finalStandings[1]!.pointsPercentage).toBe(80)
    expect(record.finalStandings[5]!.pointsPercentage).toBe(0)
  })

  it('a virada grava a história ANTES de zerar as estatísticas do elenco', () => {
    const my = state.clubs[1]!
    const scorer = my.squad[9]!
    state.clubs[1] = {
      ...my,
      squad: my.squad.map((p) => (p.id === scorer.id ? { ...p, goals: 17 } : p)),
    }

    startNewSeason(2027, finalTables)

    const record = state.history[0]!
    expect(record.season).toBe(2026)
    // O artilheiro só sobrevive se o snapshot for montado antes do reset.
    expect(record.topScorer?.playerName).toBe(scorer.name)
    expect(record.topScorer?.goals).toBe(17)
    // ...e o reset de fato aconteceu.
    expect(state.clubs[1]!.squad.every((p) => p.goals === 0)).toBe(true)
  })

  it('temporadas se acumulam da mais recente para a mais antiga', () => {
    startNewSeason(2027, finalTables)
    startNewSeason(2028, state.standings)

    expect(state.history.map((r) => r.season)).toEqual([2027, 2026])
  })

  it('carreira conta temporadas, títulos e a melhor campanha', () => {
    const records: SeasonRecord[] = [
      seasonRecordOf(2026, finalTables),
      // Em 2027 o clube 1 é campeão da elite.
      seasonRecordOf(2027, [table(1, [1, 3, 2, 4, 5, 6], [15, 12, 9, 6, 3, 0])]),
    ]

    const career = careerOf(1, records)!

    expect(career.seasonsPlayed).toBe(2)
    expect(career.titles).toEqual([2027])
    expect(career.bestCampaign?.season).toBe(2027)
    expect(career.clubName).toBe('Club 1')
  })

  it('clube sem temporada encerrada não tem carreira', () => {
    expect(careerOf(99, [seasonRecordOf(2026, finalTables)])).toBeNull()
    expect(careerOf(1, [])).toBeNull()
  })
})
