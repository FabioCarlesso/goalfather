import { describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { NationalTeamsPage } from './NationalTeamsPage'
import { CallUpNotice } from '../components/CallUpNotice'
import { renderWithProviders } from '../test/render'
import { server } from '../test/setup'
import { mockNationalTeams } from '../mocks/nationalTeams'
import { BRAZIL } from '../test/fixtures'
import type { ClubCallUps, NationalTeamsOverview } from '../domain/types'

const ARGENTINA = { code: 'ARG', name: 'Argentina', confederation: 'CONMEBOL', flag: '🇦🇷' } as const

describe('Seleções nas datas FIFA (issue #134)', () => {
  it('a página mostra as datas FIFA, a tabela das Eliminatórias e o aviso de convocação', async () => {
    renderWithProviders(<NationalTeamsPage />)

    await waitFor(() => expect(screen.getByRole('heading', { name: 'Seleções', level: 1 })).toBeInTheDocument())
    expect(screen.getByText('23/03/2026 a 31/03/2026')).toBeInTheDocument()
    const qualifiers = screen.getByRole('region', { name: /Tabela: Eliminatórias CONMEBOL 2026–2027/ })
    expect(within(qualifiers).getAllByRole('row')).toHaveLength(11) // cabeçalho + 10 seleções
    expect(screen.getByText('O ranking começa depois da primeira data FIFA.')).toBeInTheDocument()
    // Prévia do mock: os 3 melhores do elenco.
    expect(await screen.findByRole('heading', { name: /3 jogadores convocados para a próxima data FIFA/ }))
      .toBeInTheDocument()
  })

  it('mostra os resultados da última janela e o ranking quando já houve data FIFA', async () => {
    const overview: NationalTeamsOverview = {
      ...mockNationalTeams(),
      lastWindow: { start: '2026-03-23', end: '2026-03-31', status: 'PLAYED', matchSlots: 2 },
      lastResults: [
        { home: BRAZIL, away: ARGENTINA, kind: 'QUALIFIER', matchNumber: 1, qualifierRound: 1, homeGoals: 2, awayGoals: 1 },
      ],
      ranking: [
        { position: 1, country: BRAZIL, played: 1, won: 1, drawn: 0, lost: 0, goalsFor: 2, goalsAgainst: 1, goalDifference: 1, points: 6 },
      ],
    }
    server.use(http.get('/api/national-teams', () => HttpResponse.json(overview)))
    renderWithProviders(<NationalTeamsPage />)

    const results = await screen.findByRole('list', { name: 'Jogos de seleção' })
    expect(within(results).getByText('2 x 1')).toBeInTheDocument()
    expect(within(results).getByText(/Eliminatórias · 1ª rodada/)).toBeInTheDocument()
    const ranking = screen.getByRole('region', { name: 'Tabela: Ranking de seleções' })
    expect(within(ranking).getByRole('rowheader', { name: /Brasil/ })).toBeInTheDocument()
  })

  it('o aviso some sem convocados e muda o texto durante a janela', () => {
    const base: ClubCallUps = {
      window: { start: '2026-03-23', end: '2026-03-31', status: 'CALLED', matchSlots: 2 },
      players: [
        { playerId: 1, name: 'Fulano', position: 'FW', overall: 80, clubId: 1, clubName: 'Clube', country: BRAZIL },
      ],
    }
    // O router do helper guarda o 1º elemento (sem `rerender`): um render por caso.
    const { unmount } = renderWithProviders(<CallUpNotice callUps={base} />)
    expect(screen.getByRole('heading', { name: '1 jogador convocado na data FIFA' })).toBeInTheDocument()
    expect(screen.getByText(/fora de todos os jogos do clube/)).toBeInTheDocument()
    unmount()

    renderWithProviders(<CallUpNotice callUps={{ ...base, players: [] }} />)
    expect(screen.queryByRole('heading')).not.toBeInTheDocument()
  })
})
