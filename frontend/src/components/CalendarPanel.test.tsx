import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { CalendarPanel } from './CalendarPanel'
import { matchCompetition, testMatchday } from '../test/fixtures'
import type { Calendar } from '../domain/types'

describe('CalendarPanel (issue #125)', () => {
  it('mostra a data corrente e o próximo jogo com a competição', () => {
    const calendar: Calendar = {
      current: testMatchday(5),
      season: 2026,
      nextMatch: {
        matchday: testMatchday(6, 'MIDWEEK'),
        match: {
          matchId: 9, homeClubId: 7, awayClubId: 1, homeClubName: 'Rival', awayClubName: 'Goal Father FC',
          status: 'Scheduled', homeGoals: 0, awayGoals: 0, minute: 0, division: 1,
          ...matchCompetition({ type: 'Cup', phase: 'QUARTER_FINALS' }),
        },
      },
    }
    render(<CalendarPanel calendar={calendar} clubId={1} />)

    expect(screen.getByTestId('calendar-current')).toHaveTextContent('Semana 5 · Fim de semana · 01/02/2026 · Temporada 2026')
    const next = screen.getByTestId('calendar-next-match')
    expect(next).toHaveTextContent('Copa · Quartas de final: contra Rival (fora)')
    expect(next).toHaveTextContent('Semana 6 · Meio de semana · 04/02/2026')
  })

  it('sem jogo marcado avisa em vez de quebrar', () => {
    render(<CalendarPanel calendar={{ current: testMatchday(50), season: 2026 }} clubId={1} />)
    expect(screen.getByText('Sem jogo marcado nesta temporada')).toBeInTheDocument()
  })
})
