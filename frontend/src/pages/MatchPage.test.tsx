import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClientProvider } from '@tanstack/react-query'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { MATCH_NOT_STARTED_MESSAGE, MatchPage } from './MatchPage'
import { AuthProvider } from '../auth/AuthContext'
import { makeQueryClient } from '../test/render'
import { FakeWebSocket } from '../test/fakeWebSocket'
import { STREAM_SPEED_KEY } from '../lib/useStreamSpeed'
import { WS_CLOSE_NOT_STARTED, WS_REASON_NOT_STARTED } from '../api/wsProtocol'

// Rota com `:matchId` de verdade (o `renderWithProviders` usa curinga), para
// navegar de uma partida para outra sem remontar a página inteira.
function renderMatchRoute(initial: string) {
  const router = createMemoryRouter([{ path: '/round/match/:matchId', element: <MatchPage /> }], {
    initialEntries: [initial],
  })
  render(
    <QueryClientProvider client={makeQueryClient()}>
      <AuthProvider initialAuth={{ user: { id: 1, username: 'tester', clubId: 1 }, status: 'authenticated' }}>
        <RouterProvider router={router} />
      </AuthProvider>
    </QueryClientProvider>,
  )
  return router
}

describe('MatchPage — velocidade da transmissão (issue #115)', () => {
  beforeEach(() => {
    FakeWebSocket.instances = []
    vi.stubGlobal('WebSocket', FakeWebSocket)
    localStorage.setItem(STREAM_SPEED_KEY, 'SLOW')
  })
  afterEach(() => {
    vi.unstubAllGlobals()
    localStorage.removeItem(STREAM_SPEED_KEY)
  })

  it('o pulo vale só para a partida atual: outra partida abre na velocidade preferida', async () => {
    const router = renderMatchRoute('/round/match/10')
    await waitFor(() => expect(FakeWebSocket.instances).toHaveLength(1))
    const first = FakeWebSocket.last()
    expect(first.url).toContain('/ws/matches/10')
    expect(first.url).toContain('speed=SLOW')
    first.open()

    await userEvent.click(await screen.findByRole('button', { name: 'Pular para o fim' }))
    const skipped = FakeWebSocket.last()
    expect(skipped.url).toContain('/ws/matches/10')
    expect(skipped.url).toContain('speed=INSTANT')
    expect(first.closed).toBe(true)

    // Trocar de partida remonta a MatchView (`key={matchId}`): o INSTANT do
    // pulo não vaza para a próxima transmissão.
    await act(() => router.navigate('/round/match/11'))
    await waitFor(() => expect(FakeWebSocket.last().url).toContain('/ws/matches/11'))
    expect(FakeWebSocket.last().url).toContain('speed=SLOW')
    expect(skipped.closed).toBe(true)
  })
})

describe('MatchPage — encaixe não liberado (issue #151)', () => {
  beforeEach(() => {
    FakeWebSocket.instances = []
    vi.stubGlobal('WebSocket', FakeWebSocket)
  })
  afterEach(() => vi.unstubAllGlobals())

  it('close 4409 vira aviso de partida não iniciada, sem controles de transmissão', async () => {
    renderMatchRoute('/round/match/10')
    await waitFor(() => expect(FakeWebSocket.instances).toHaveLength(1))
    const socket = FakeWebSocket.last()
    socket.open()
    socket.serverClose(WS_CLOSE_NOT_STARTED, WS_REASON_NOT_STARTED)

    expect(await screen.findByRole('status')).toHaveTextContent(MATCH_NOT_STARTED_MESSAGE)
    expect(screen.queryByText(WS_REASON_NOT_STARTED)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Pular para o fim' })).not.toBeInTheDocument()
  })
})
