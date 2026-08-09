import { describe, expect, it } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import { MatchStatsSummary } from './MatchStatsSummary'
import type { MatchStats } from '../domain/types'

const stats: MatchStats = {
  home: { shots: 9, shotsOnTarget: 5, saves: 2, yellowCards: 1, redCards: 0 },
  away: { shots: 4, shotsOnTarget: 2, saves: 3, yellowCards: 2, redCards: 1 },
}

describe('MatchStatsSummary', () => {
  it('exibe os números dos dois lados sem recalcular nada', () => {
    const { container } = render(
      <MatchStatsSummary stats={stats} homeName="Goal Father FC" awayName="Real Capela" />,
    )

    expect(screen.getByText('Goal Father FC')).toBeInTheDocument()
    expect(screen.getByText('Real Capela')).toBeInTheDocument()

    // Os valores saem na ordem mandante → rótulo → visitante.
    const cells = within(container).getAllByText(/^\d+$/).map((el) => el.textContent)
    expect(cells).toEqual(['9', '4', '5', '2', '2', '3', '1', '2', '0', '1'])
  })

  it('divide a barra ao meio quando os dois lados estão zerados', () => {
    // Guarda contra divisão por zero em partida sem finalização nenhuma.
    const zeroed: MatchStats = {
      home: { shots: 0, shotsOnTarget: 0, saves: 0, yellowCards: 0, redCards: 0 },
      away: { shots: 0, shotsOnTarget: 0, saves: 0, yellowCards: 0, redCards: 0 },
    }
    const { container } = render(<MatchStatsSummary stats={zeroed} homeName="A" awayName="B" />)

    const bars = container.querySelectorAll<HTMLElement>('[style*="width"]')
    expect(bars.length).toBeGreaterThan(0)
    for (const bar of bars) expect(bar.style.width).toBe('50%')
  })
})
