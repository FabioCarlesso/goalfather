import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MatchEventFeed } from './MatchEventFeed'
import type { MatchEvent } from '../domain/types'

const events: MatchEvent[] = [
  {
    type: 'KickOff', minute: 0,
    homeClubName: 'A', awayClubName: 'B',
    homeStrength: 80, awayStrength: 70,
    // Obrigatórias desde a issue #56; faltavam aqui porque o tsc não cobre
    // arquivos de teste — mesma causa do fixture de fitness.test.ts.
    homePosture: 'BALANCED', awayPosture: 'BALANCED',
  },
  { type: 'Goal', minute: 23, scorerId: 10, home: true },
  { type: 'Card', minute: 40, playerId: 7, red: false, home: true },
  { type: 'Injury', minute: 55, playerId: 999, roundsOut: 2 },   // obrigatório desde a issue #54
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

  // Novos eventos da issue #57: o feed nomeia quem participou do lance.
  it('mostra chute para fora com o autor', () => {
    const lookup = new Map<number, string>([[11, 'João Faria']])
    render(
      <MatchEventFeed
        events={[{ type: 'Miss', minute: 12, playerId: 11, home: true }]}
        playerLookup={lookup}
      />,
    )
    expect(screen.getByText(/Para fora — João Faria/)).toBeInTheDocument()
  })

  it('identifica o goleiro na defesa e cai no genérico sem goleiro', () => {
    const lookup = new Map<number, string>([[1, 'Marcos Figueiredo']])
    const { rerender } = render(
      <MatchEventFeed
        events={[{ type: 'Save', minute: 30, goalkeeperId: 1, home: true }]}
        playerLookup={lookup}
      />,
    )
    expect(screen.getByText(/Defesa de Marcos Figueiredo/)).toBeInTheDocument()

    // Escalação sem goleiro: `goalkeeperId` nulo no contrato.
    rerender(<MatchEventFeed events={[{ type: 'Save', minute: 30, goalkeeperId: null, home: false }]} />)
    expect(screen.getByText(/Defesa difícil/)).toBeInTheDocument()
  })

  it('mostra o emptyLabel quando não há eventos', () => {
    render(<MatchEventFeed events={[]} emptyLabel="Nada ainda." />)
    expect(screen.getByText('Nada ainda.')).toBeInTheDocument()
  })
})
