import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { RoundPage } from './RoundPage'
import { renderWithProviders } from '../test/render'
import { server } from '../test/setup'
import { FakeWebSocket } from '../test/fakeWebSocket'
import type { MatchEvent, Round, RoundEvent, Standings } from '../domain/types'

describe('RoundPage — card de partida (issue #85)', () => {
  it('expõe o nome completo do clube no title, já que nomes longos são truncados', async () => {
    renderWithProviders(<RoundPage />, { initialEntries: ['/round'] })

    // Seed: "Atlético Bonsucesso" e "Independente Sul" estão na 1ª divisão.
    const home = await screen.findByTitle('Atlético Bonsucesso')
    expect(home).toHaveTextContent('Atlético Bonsucesso')
    expect(home).toHaveClass('truncate')
    expect(await screen.findByTitle('Independente Sul')).toHaveClass('truncate')
  })
})

describe('RoundPage — resumo da rodada (issue #112)', () => {
  const played: Round = {
    number: 1,
    season: 2026,
    status: 'Scheduled',
    stage: { type: 'League', matchday: 1 },
    matches: [{
      matchId: 10, homeClubId: 1, awayClubId: 2, homeClubName: 'Goal Father FC', awayClubName: 'Rival FC',
      status: 'Scheduled', homeGoals: 0, awayGoals: 0, minute: 0, division: 1,
    }],
  }
  const next: Round = {
    ...played,
    number: 2,
    stage: { type: 'Cup', phase: 'ROUND_OF_16' },
    matches: [{ ...played.matches[0]!, matchId: 20, awayClubId: 3, awayClubName: 'Copeiro EC' }],
  }
  const table: Standings = {
    season: 2026, division: 1, round: 1, promotionSpots: 0, relegationSpots: 2,
    rows: [{
      position: 1, clubId: 1, clubName: 'Goal Father FC', played: 1, wins: 1, draws: 0, losses: 0,
      goalsFor: 2, goalsAgainst: 0, goalDifference: 2, points: 3,
    }],
  }
  const update = (event: Partial<MatchEvent> & Pick<MatchEvent, 'type' | 'minute'>) =>
    ({ type: 'MatchUpdate', matchId: 10, event }) as RoundEvent

  afterEach(() => vi.unstubAllGlobals())

  it('ao fim, o resumo assume o topo e a grade continua na data jogada', async () => {
    const scrollIntoView = vi.fn()
    Element.prototype.scrollIntoView = scrollIntoView
    FakeWebSocket.instances = []
    vi.stubGlobal('WebSocket', FakeWebSocket)
    let current = played
    server.use(
      http.get('/api/league/round/current', () => HttpResponse.json(current)),
      http.post('/api/league/round/play', () => HttpResponse.json({ roundNumber: 1 })),
      http.get('/api/league/round/readiness', () =>
        HttpResponse.json({
          roundNumber: current.number, readyCount: 1, totalCount: 1, pendingUsernames: [],
          secondsRemaining: null, timedOut: false, allReady: true, canStart: true,
        }),
      ),
    )
    const user = userEvent.setup()
    renderWithProviders(<RoundPage />, { initialEntries: ['/round'] })

    const play = await screen.findByRole('button', { name: 'Jogar rodada' })
    await waitFor(() => expect(play).toBeEnabled())
    await user.click(play)

    const socket = FakeWebSocket.last()
    socket.open()
    socket.emit(update({ type: 'Goal', minute: 30, home: true }))
    socket.emit(update({ type: 'FullTime', minute: 90, homeGoals: 2, awayGoals: 0 }))
    // O servidor já abriu a próxima data quando o RoundFinished sai.
    current = next
    socket.emit({ type: 'RoundFinished', standings: [table], finances: [], training: [] } as RoundEvent)
    socket.serverClose()

    const summary = await screen.findByRole('region', { name: 'Resumo da rodada' })
    expect(within(summary).getByText('Vitória')).toBeInTheDocument()
    expect(within(summary).getByText('Pontuações atualizadas após a rodada 1')).toBeInTheDocument()
    // Um só "Próxima rodada": o do resumo substitui o do cabeçalho.
    expect(screen.getAllByRole('button', { name: 'Próxima rodada' })).toHaveLength(1)
    // O cabeçalho já fala da próxima data; a grade e o feed, da que foi jogada.
    expect(await screen.findByRole('heading', { level: 1, name: 'Copa — Oitavas de final' })).toBeInTheDocument()
    expect(screen.getByTitle('Rival FC')).toBeInTheDocument()
    expect(screen.queryByTitle('Copeiro EC')).not.toBeInTheDocument()

    await user.click(within(summary).getByRole('button', { name: 'Ver detalhes da partida' }))
    expect(scrollIntoView).toHaveBeenCalledOnce()
    expect(scrollIntoView.mock.contexts[0]).toHaveTextContent(/Detalhes da sua partida — Goal Father FC × Rival FC/)
  })
})
