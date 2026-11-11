import { describe, expect, it } from 'vitest'
import { formatMatchday, hasKnockout, matchCompetitionLabel, primaryStage, roundTitle } from './calendar'
import { matchCompetition, testMatchday } from '../test/fixtures'
import type { RoundMatch } from './types'

const match = (stage: RoundMatch['stage']): RoundMatch => ({
  matchId: 1, homeClubId: 1, awayClubId: 2, homeClubName: 'A', awayClubName: 'B',
  status: 'Scheduled', homeGoals: 0, awayGoals: 0, minute: 0, division: 1,
  ...matchCompetition(stage),
})

describe('rótulos do calendário semanal (issue #125)', () => {
  it('formata o encaixe com semana, slot e data', () => {
    expect(formatMatchday(testMatchday(5))).toBe('Semana 5 · Fim de semana · 01/02/2026')
    expect(formatMatchday(testMatchday(5, 'MIDWEEK'))).toBe('Semana 5 · Meio de semana · 28/01/2026')
  })

  it('título junta liga e copa quando dividem o encaixe', () => {
    const league = match({ type: 'League', matchday: 3 })
    const cup = match({ type: 'Cup', phase: 'SEMI_FINALS' })
    expect(roundTitle({ matches: [league, league] })).toBe('Rodada 3')
    expect(roundTitle({ matches: [league, cup] })).toBe('Rodada 3 + Copa — Semifinal')
    expect(roundTitle({ matches: [] })).toBe('Encaixe sem jogos')
    expect(hasKnockout({ matches: [league] })).toBe(false)
    expect(hasKnockout({ matches: [league, cup] })).toBe(true)
    // O resumo destaca a rodada de liga, que é a que mexe na tabela.
    expect(primaryStage({ matches: [cup, league] })).toEqual({ type: 'League', matchday: 3 })
  })

  it('rótulo da partida diz a competição e o que ela disputa', () => {
    expect(matchCompetitionLabel(match({ type: 'League', matchday: 2 }))).toBe('Liga — Divisão 1 · Rodada 2')
    expect(matchCompetitionLabel(match({ type: 'Cup', phase: 'FINAL' }))).toBe('Copa · Final')
  })
})
