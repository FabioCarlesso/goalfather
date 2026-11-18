import { describe, expect, it } from 'vitest'
import { competitionTabs, groupByCountry, openMatchdayIndex, tabOf } from './competitions'
import type { CompetitionMatchday, CompetitionSummary, RoundMatch } from './types'

const BRA = { code: 'BRA', name: 'Brasil', confederation: 'CONMEBOL', flag: '🇧🇷' } as const
const ENG = { code: 'ENG', name: 'Inglaterra', confederation: 'UEFA', flag: '🏴' } as const

const summary = (id: string, extra: Partial<CompetitionSummary>): CompetitionSummary => ({
  id, name: id, format: 'ROUND_ROBIN', kind: 'LEAGUE', confederation: 'CONMEBOL', clubCount: 6, ...extra,
})

const day = (key: string, statuses: RoundMatch['status'][]): CompetitionMatchday => ({
  matchday: { key, year: 2026, week: 1, slot: 'WEEKEND', date: '2026-01-04' },
  matches: statuses.map((status, i) => ({
    matchId: i, homeClubId: 1, awayClubId: 2, homeClubName: 'A', awayClubName: 'B', status,
    homeGoals: 0, awayGoals: 0, minute: 0, division: 1, stage: { type: 'League', matchday: 1 },
    competitionId: 'bra-serie-a', competitionName: 'Série A',
  })),
})

describe('abas por formato (issue #133)', () => {
  it('pontos corridos abre na Tabela e mata-mata na Chave', () => {
    expect(competitionTabs('ROUND_ROBIN').map((t) => t.view)).toEqual(['table', 'matches', 'scorers'])
    expect(competitionTabs('KNOCKOUT')[0]!.view).toBe('bracket')
    expect(competitionTabs('GROUPS_THEN_KNOCKOUT')[0]!.view).toBe('groups')
    expect(competitionTabs('SWISS_LEAGUE').map((t) => t.view)).toContain('bracket')
  })

  it('aba da URL vale só se for do formato; senão, a padrão', () => {
    expect(tabOf('ROUND_ROBIN', 'jogos').view).toBe('matches')
    expect(tabOf('ROUND_ROBIN', 'chave').view).toBe('table')
    expect(tabOf('KNOCKOUT', undefined).view).toBe('bracket')
  })
})

describe('groupByCountry', () => {
  it('agrupa por país na ordem recebida e põe continentais pela confederação', () => {
    const groups = groupByCountry([
      summary('bra-serie-a', { country: BRA }),
      summary('eng-premier-league', { country: ENG, confederation: 'UEFA' }),
      summary('bra-copa-do-brasil', { country: BRA, format: 'KNOCKOUT', kind: 'NATIONAL_CUP' }),
      summary('conmebol-libertadores', { kind: 'CONTINENTAL', format: 'GROUPS_THEN_KNOCKOUT' }),
    ])
    expect(groups.map((g) => g.key)).toEqual(['BRA', 'ENG', 'CONMEBOL'])
    expect(groups[0]!.competitions.map((c) => c.id)).toEqual(['bra-serie-a', 'bra-copa-do-brasil'])
    expect(groups[2]!.country).toBeUndefined()
  })
})

describe('openMatchdayIndex', () => {
  it('abre no primeiro encaixe com jogo em aberto, ou no último', () => {
    const played = day('a', ['Finished'])
    const open = day('b', ['Scheduled'])
    expect(openMatchdayIndex([played, open, day('c', ['Scheduled'])])).toBe(1)
    expect(openMatchdayIndex([played, day('d', ['Finished'])])).toBe(1)
    expect(openMatchdayIndex([played, day('e', [])])).toBe(1)
    expect(openMatchdayIndex([])).toBe(0)
  })
})
