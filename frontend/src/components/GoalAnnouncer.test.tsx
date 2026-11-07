import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { GoalAnnouncer } from './GoalAnnouncer'
import type { MatchEvent } from '../domain/types'

const kickOff: MatchEvent = {
  type: 'KickOff', minute: 0,
  homeClubName: 'Casa', awayClubName: 'Fora',
  homeStrength: 80, awayStrength: 70,
  homePosture: 'BALANCED', awayPosture: 'BALANCED',
}

describe('GoalAnnouncer (issue #113)', () => {
  it('é uma região aria-live polite, vazia antes do primeiro gol', () => {
    render(<GoalAnnouncer events={[kickOff]} homeName="Casa" awayName="Fora" homeGoals={0} awayGoals={0} />)
    const region = screen.getByTestId('goal-announcer')
    expect(region).toHaveAttribute('aria-live', 'polite')
    expect(region).toHaveClass('sr-only')
    expect(region).toBeEmptyDOMElement()
  })

  it('anuncia só o último gol, com o placar acumulado e o minuto', () => {
    const events: MatchEvent[] = [
      kickOff,
      { type: 'Goal', minute: 10, scorerId: 1, home: true },
      { type: 'Card', minute: 20, playerId: 7, red: false, home: false },
      { type: 'Goal', minute: 40, scorerId: 2, home: true },
      { type: 'Goal', minute: 63, scorerId: 3, home: false },
      { type: 'Miss', minute: 70, playerId: 4, home: true },
    ]
    render(<GoalAnnouncer events={events} homeName="Casa" awayName="Fora" homeGoals={2} awayGoals={1} />)
    expect(screen.getByTestId('goal-announcer')).toHaveTextContent(
      /^Gol do Fora, 2 a 1, aos 63 minutos$/,
    )
  })

  it('usa o placar da tela, não a contagem dos eventos (entrada no meio da partida)', () => {
    // Snapshot da rodada já tinha 1 a 0; o stream só trouxe o gol seguinte.
    const events: MatchEvent[] = [{ type: 'Goal', minute: 55, scorerId: 2, home: true }]
    render(<GoalAnnouncer events={events} homeName="Casa" awayName="Fora" homeGoals={2} awayGoals={0} />)
    expect(screen.getByTestId('goal-announcer')).toHaveTextContent('Gol do Casa, 2 a 0, aos 55 minutos')
  })

  it('usa o singular no primeiro minuto', () => {
    const events: MatchEvent[] = [kickOff, { type: 'Goal', minute: 1, scorerId: 1, home: true }]
    render(<GoalAnnouncer events={events} homeName="Casa" awayName="Fora" homeGoals={1} awayGoals={0} />)
    expect(screen.getByTestId('goal-announcer')).toHaveTextContent('Gol do Casa, 1 a 0, ao 1 minuto')
  })
})
