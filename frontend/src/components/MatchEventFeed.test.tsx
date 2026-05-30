import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MatchEventFeed } from './MatchEventFeed'
import type { MatchEvent } from '../domain/types'

const events: MatchEvent[] = [
  { type: 'KickOff', minute: 0, homeClubName: 'A', awayClubName: 'B', homeStrength: 80, awayStrength: 70 },
  { type: 'Goal', minute: 23, scorerId: 10, home: true },
  { type: 'Card', minute: 40, playerId: 7, red: false },
  { type: 'Injury', minute: 55, playerId: 999 },
]

describe('MatchEventFeed', () => {
  it('mostra "#N" quando não há lookup', () => {
    render(<MatchEventFeed events={events} />)
    expect(screen.getByText(/GOL! #10/)).toBeInTheDocument()
    expect(screen.getByText(/#7/)).toBeInTheDocument()
  })

  it('resolve nomes a partir do lookup, com fallback para "#N"', () => {
    const lookup = new Map<number, string>([
      [10, 'Renato Silva'],
      [7, 'Diego Lobato'],
    ])
    render(<MatchEventFeed events={events} playerLookup={lookup} />)
    expect(screen.getByText(/GOL! Renato Silva/)).toBeInTheDocument()
    expect(screen.getByText(/Diego Lobato/)).toBeInTheDocument()
    // 999 não está no lookup → cai no fallback.
    expect(screen.getByText(/#999/)).toBeInTheDocument()
  })

  it('mostra o emptyLabel quando não há eventos', () => {
    render(<MatchEventFeed events={[]} emptyLabel="Nada ainda." />)
    expect(screen.getByText('Nada ainda.')).toBeInTheDocument()
  })
})
