import type { ReactElement } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { onlineManager } from '@tanstack/react-query'
import { http, HttpResponse } from 'msw'
import { Route, Routes } from 'react-router-dom'
import { ClubPage } from './ClubPage'
import { CupPage } from './CupPage'
import { DashboardPage } from './DashboardPage'
import { FiredPage } from './FiredPage'
import { HistoryPage } from './HistoryPage'
import { LineupPage } from './LineupPage'
import { MarketPage } from './MarketPage'
import { RoundPage } from './RoundPage'
import { SelectClubPage } from './SelectClubPage'
import { StandingsPage } from './StandingsPage'
import { renderWithProviders } from '../test/render'
import { renderDashboard } from '../test/renderDashboard'
import { server } from '../test/setup'
import { seasonRecordOf, state } from '../mocks/seed'
import { standingsKey } from '../api/queries/useStandings'
import type { AuthUser, Standings } from '../domain/types'

const SERVER_ERROR = 'Erro no servidor. Tente novamente em instantes.'

/** Só a próxima chamada falha; a seguinte cai no handler normal do mock. */
const failOnce = (url: string, method: 'get' | 'post' = 'get') =>
  server.use(http[method](url, () => HttpResponse.json({}, { status: 500 }), { once: true }))

interface PageCase {
  name: string
  url: string
  title: string
  ui: ReactElement
  initialEntries?: string[]
}

const clubRoute = (
  <Routes>
    <Route path="/clubs/:id" element={<ClubPage />} />
  </Routes>
)

const PAGES: PageCase[] = [
  { name: 'StandingsPage', url: '/api/league/standings', title: 'Tabela', ui: <StandingsPage /> },
  { name: 'CupPage', url: '/api/league/cup', title: 'Copa', ui: <CupPage /> },
  { name: 'HistoryPage', url: '/api/league/history', title: 'Histórico', ui: <HistoryPage /> },
  { name: 'DashboardPage', url: '/api/clubs/1', title: 'Clube', ui: <DashboardPage /> },
  { name: 'RoundPage', url: '/api/league/round/current', title: 'Rodada', ui: <RoundPage /> },
  { name: 'SelectClubPage', url: '/api/clubs/available', title: 'Escolha seu clube', ui: <SelectClubPage /> },
  { name: 'LineupPage', url: '/api/clubs/1', title: 'Escalação', ui: <LineupPage /> },
  { name: 'MarketPage', url: '/api/market', title: 'Mercado', ui: <MarketPage /> },
  { name: 'ClubPage', url: '/api/clubs/2', title: 'Clube', ui: clubRoute, initialEntries: ['/clubs/2'] },
]

describe('estados de tela (issue #105)', () => {
  it.each(PAGES)('$name: skeleton → erro → retry → sucesso, com título em todos os passos', async (page) => {
    const user = userEvent.setup()
    failOnce(page.url)
    renderWithProviders(page.ui, { initialEntries: page.initialEntries })

    expect(screen.getByRole('status')).toBeInTheDocument()
    expect(document.title).toBe(`${page.title} · GoalFather`)

    expect(await screen.findByRole('alert')).toHaveTextContent(SERVER_ERROR)
    expect(document.title).toBe(`${page.title} · GoalFather`)

    await user.click(screen.getByRole('button', { name: 'Tentar novamente' }))

    expect(await screen.findByRole('heading', { level: 1 })).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('StandingsPage: tabela sem divisões explica, em vez de ficar em branco', async () => {
    server.use(http.get('/api/league/standings', () => HttpResponse.json([])))
    renderWithProviders(<StandingsPage />)

    expect(await screen.findByText(/nenhuma divisão com tabela/i)).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 1, name: 'Tabela' })).toBeInTheDocument()
    expect(document.title).toBe('Tabela · GoalFather')
  })

  it('falha de refetch em background mantém os dados na tela (issue #23)', async () => {
    const { queryClient } = renderWithProviders(<StandingsPage />)
    await screen.findByRole('link', { name: 'Goal Father FC' })

    failOnce('/api/league/standings')
    await queryClient.invalidateQueries({ queryKey: standingsKey })

    await waitFor(() => expect(queryClient.getQueryState(standingsKey)?.status).toBe('error'))
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Goal Father FC' })).toBeInTheDocument()
  })

  it('sem rede, a query pausada mostra skeleton em vez de estado vazio e retoma sozinha', async () => {
    onlineManager.setOnline(false)
    try {
      renderWithProviders(<StandingsPage />)

      // Dá tempo de um eventual fetch resolver: pausada, ela não sai do lugar.
      await new Promise((resolve) => setTimeout(resolve, 50))
      expect(screen.getByRole('status')).toBeInTheDocument()
      expect(screen.queryByText(/nenhuma divisão com tabela/i)).not.toBeInTheDocument()
    } finally {
      onlineManager.setOnline(true)
    }

    expect(await screen.findByRole('link', { name: 'Goal Father FC' })).toBeInTheDocument()
  })

  it('ClubPage: id inválido mostra "não encontrado", sem skeleton eterno', () => {
    renderWithProviders(clubRoute, { initialEntries: ['/clubs/abc'] })

    expect(screen.getByText('Clube não encontrado.')).toBeInTheDocument()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  describe('HistoryPage: drill-down da temporada', () => {
    const finalTables: Standings[] = [
      {
        season: 2026,
        division: 1,
        round: 5,
        promotionSpots: 0,
        relegationSpots: 0,
        rows: [{
          position: 1,
          clubId: 1,
          clubName: 'Club 1',
          played: 5, wins: 5, draws: 0, losses: 0,
          goalsFor: 5, goalsAgainst: 0, goalDifference: 5,
          points: 15,
          zone: 'NONE',
        }],
      },
    ]

    afterEach(() => {
      state.history = []
    })

    it('falha com retry, em vez de "carregando" para sempre', async () => {
      const user = userEvent.setup()
      state.history = [seasonRecordOf(2026, finalTables)]
      failOnce('/api/league/history/2026')
      renderWithProviders(<HistoryPage />)

      await user.click(await screen.findByRole('button', { name: /2026/ }))
      expect(await screen.findByRole('alert')).toHaveTextContent(SERVER_ERROR)
      // A lista continua de pé: só o bloco do detalhe falhou.
      expect(screen.getByRole('button', { name: /2026/ })).toBeInTheDocument()

      await user.click(screen.getByRole('button', { name: 'Tentar novamente' }))
      expect(await screen.findByRole('heading', { name: 'Temporada 2026' })).toBeInTheDocument()
    })
  })

  it('FiredPage: falha no histórico do clube não derruba a tela', async () => {
    const user = userEvent.setup()
    const fired: AuthUser = {
      id: 1,
      username: 'tester',
      clubId: null,
      dismissal: {
        clubId: 1,
        clubName: 'Goal Father FC',
        season: 2026,
        objective: 'TITLE',
        targetPosition: 1,
        finalPosition: 6,
      },
    }
    failOnce('/api/league/history/club/1')
    renderWithProviders(<FiredPage />, { auth: { user: fired, status: 'authenticated' } })

    expect(await screen.findByRole('alert')).toHaveTextContent(SERVER_ERROR)
    expect(screen.getByRole('heading', { name: 'Você foi demitido' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Tentar novamente' }))
    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument())
  })

  describe('erros de mutation inline usam errorMessage()', () => {
    it.each([
      { button: 'Ampliar', url: '/api/clubs/1/stadium/expand', tab: 'financas' },
      { button: 'Tratar elenco', url: '/api/clubs/1/medical', tab: 'departamento' },
    ])('DashboardPage: $button', async ({ button, url, tab }) => {
      const user = userEvent.setup()
      failOnce(url, 'post')
      renderDashboard(`/dashboard/${tab}`)

      await user.click(await screen.findByRole('button', { name: button }))

      expect(await screen.findByText(SERVER_ERROR)).toBeInTheDocument()
      expect(screen.queryByText(/ApiError/)).not.toBeInTheDocument()
    })

    it('RoundPage: falha ao iniciar a rodada', async () => {
      const user = userEvent.setup()
      vi.stubGlobal('WebSocket', vi.fn())
      server.use(
        http.get('/api/league/round/readiness', () =>
          HttpResponse.json({
            matchday: '2026-W01-WEEKEND',
            readyCount: 1,
            totalCount: 1,
            pendingUsernames: [],
            secondsRemaining: 0,
            timedOut: false,
            allReady: true,
            canStart: true,
          }),
        ),
      )
      failOnce('/api/league/round/play', 'post')
      renderWithProviders(<RoundPage />)

      const play = await screen.findByRole('button', { name: 'Jogar rodada' })
      await waitFor(() => expect(play).toBeEnabled())
      await user.click(play)

      expect(await screen.findByText(`Falha ao iniciar rodada: ${SERVER_ERROR}`)).toBeInTheDocument()
      vi.unstubAllGlobals()
    })
  })
})
