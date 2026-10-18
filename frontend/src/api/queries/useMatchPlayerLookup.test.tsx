import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { useMatchPlayerLookup } from './useMatchPlayerLookup'
import { MatchEventFeed } from '../../components/MatchEventFeed'
import { renderWithProviders } from '../../test/render'
import { server } from '../../test/setup'
import type { MatchEvent } from '../../domain/types'

// Gol do mandante (clube 1, elenco do seed) e cartão do visitante (clube 4,
// da IA — o mock materializa o elenco com ids 4001..4011).
const events: MatchEvent[] = [
  { type: 'Goal', minute: 44, scorerId: 10, home: true },
  { type: 'Card', minute: 54, playerId: 4008, red: false, home: false },
]

function Harness({ homeClubId, awayClubId }: { homeClubId: number; awayClubId: number }) {
  const lookup = useMatchPlayerLookup(homeClubId, awayClubId)
  return <MatchEventFeed events={events} playerLookup={lookup} />
}

describe('useMatchPlayerLookup (issue #84)', () => {
  it('resolve os jogadores do adversário além dos do próprio clube', async () => {
    renderWithProviders(<Harness homeClubId={1} awayClubId={4} />)

    expect(await screen.findByText(/Tupinambás FC 8/)).toBeInTheDocument()
    expect(await screen.findByText(/GOL! Renato Silva/)).toBeInTheDocument()
    expect(screen.queryByText(/#4008/)).not.toBeInTheDocument()
  })

  it('mantém o fallback "#N" quando o elenco do adversário não carrega', async () => {
    server.use(
      http.get('/api/clubs/4', () =>
        HttpResponse.json({ code: 'NOT_FOUND', message: 'x' }, { status: 404 }),
      ),
    )
    renderWithProviders(<Harness homeClubId={1} awayClubId={4} />)

    expect(await screen.findByText(/GOL! Renato Silva/)).toBeInTheDocument()
    expect(screen.getByText(/#4008/)).toBeInTheDocument()
  })
})
