import { describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { LibertadoresPage } from './LibertadoresPage'
import { renderWithProviders } from '../test/render'
import { server } from '../test/setup'
import { mockLibertadores } from '../mocks/libertadores'
import type { Cup, Libertadores } from '../domain/types'

describe('LibertadoresPage (issue #132)', () => {
  it('desenha os 8 grupos com tabela, classificados e jogos, e o mata-mata a sortear', async () => {
    renderWithProviders(<LibertadoresPage />)

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Copa Libertadores 2026' })).toBeInTheDocument()
    })
    for (const letter of 'ABCDEFGH') {
      expect(screen.getByRole('region', { name: `Grupo ${letter}` })).toBeInTheDocument()
    }
    const groupA = screen.getByTestId('libertadores-group-grupo-a')
    expect(within(groupA).getAllByRole('row')).toHaveLength(5) // cabeçalho + 4 clubes
    // A faixa vem do backend: 2 classificados por grupo.
    expect(groupA.querySelectorAll('[data-zone="QUALIFICATION"]')).toHaveLength(2)
    expect(within(groupA).getAllByText(/Rodada \d/)).toHaveLength(6)
    expect(screen.getByText(/O mata-mata é sorteado quando a fase de grupos termina/)).toBeInTheDocument()
    expect(screen.getByText('Estadio Monumental U')).toBeInTheDocument()
    // 8 tabelas + 96 jogos: sob carga, o render passa dos 5 s padrão (mesma folga do LineupPage).
  }, 15_000)

  it('mostra a chave com a sede da final e o campeão quando o backend os manda', async () => {
    const knockout: Cup = {
      season: 2026,
      competitionId: 'conmebol-libertadores',
      competitionName: 'Copa Libertadores',
      phases: [{ name: 'Final', teams: 2, legs: 'SINGLE', entrants: [], prizeCents: 200_000_000 }],
      stages: [{
        name: 'Final',
        legs: 'SINGLE',
        ties: [{
          type: 'Single', index: 0, home: { id: 1, name: 'Flamengo' }, away: { id: 101, name: 'Boca Juniors' },
          result: { homeGoals: 1, awayGoals: 1, penalties: { home: 4, away: 3 } }, winnerId: 1,
        }],
      }],
      champion: { id: 1, name: 'Flamengo' },
    }
    const edition: Libertadores = { ...mockLibertadores(), knockout, champion: { id: 1, name: 'Flamengo' } }
    server.use(http.get('/api/libertadores', () => HttpResponse.json(edition)))

    renderWithProviders(<LibertadoresPage />)

    await waitFor(() => {
      expect(screen.getByText('Flamengo é campeão da Libertadores!')).toBeInTheDocument()
    })
    const bracket = screen.getByRole('region', { name: 'Chave da Libertadores' })
    expect(within(bracket).getByText('Sede: Estadio Monumental U')).toBeInTheDocument()
    expect(within(bracket).getByText('(4)')).toBeInTheDocument()
  })

  it('explica quando o mundo não tem Libertadores (404)', async () => {
    server.use(http.get('/api/libertadores', () =>
      HttpResponse.json({ code: 'LIBERTADORES_NOT_FOUND', message: 'sem' }, { status: 404 })))

    renderWithProviders(<LibertadoresPage />)

    await waitFor(() => {
      expect(screen.getByText('Este mundo não tem Libertadores.')).toBeInTheDocument()
    })
  })
})
