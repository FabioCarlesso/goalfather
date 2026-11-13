import { describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { StandingsPage } from './StandingsPage'
import { renderWithProviders } from '../test/render'
import { server } from '../test/setup'
import type { Standings, TableZone } from '../domain/types'

/** Tabela fabricada com a faixa de cada posição já decidida pelo "backend". */
const zonedTable = (zones: TableZone[]): Standings => ({
  season: 2026,
  division: 1,
  round: 38,
  promotionSpots: 0,
  relegationSpots: zones.filter((z) => z === 'RELEGATION').length,
  rows: zones.map((zone, i) => ({
    position: i + 1,
    clubId: 100 + i,
    clubName: `Clube ${i + 1}`,
    played: 38, wins: 0, draws: 0, losses: 0,
    goalsFor: 0, goalsAgainst: 0, goalDifference: 0, points: 60 - i,
    zone,
  })),
})

const serveTables = (...tables: Standings[]) =>
  server.use(http.get('/api/league/standings', () => HttpResponse.json(tables)))

const rowOf = (clubName: string) => screen.getByRole('link', { name: clubName }).closest('tr')

describe('StandingsPage (issue #108)', () => {
  it('destaca a linha do clube do usuário com aria-current', async () => {
    renderWithProviders(<StandingsPage />)

    const link = await screen.findByRole('link', { name: 'Goal Father FC' })
    expect(link.closest('tr')).toHaveAttribute('aria-current', 'true')
    expect(screen.getByText(/seu clube/)).toBeInTheDocument()

    const rival = screen.getByRole('link', { name: 'Atlético Bonsucesso' })
    expect(rival.closest('tr')).not.toHaveAttribute('aria-current')
  })

  it('mostra a divisão do usuário primeiro', async () => {
    // Clube 7 (Ferroviária da Serra) começa na divisão 2 do seed.
    renderWithProviders(<StandingsPage />, {
      auth: { user: { id: 7, username: 'serra', clubId: 7 }, status: 'authenticated' },
    })

    await waitFor(() => {
      expect(screen.getAllByRole('heading', { level: 2 }).length).toBeGreaterThan(1)
    })
    const headings = screen.getAllByRole('heading', { level: 2 }).map((h) => h.textContent)
    expect(headings).toEqual(['Divisão 2', 'Divisão 1'])
    expect(
      screen.getByRole('link', { name: 'Ferroviária da Serra' }).closest('tr'),
    ).toHaveAttribute('aria-current', 'true')
  })
})

describe('StandingsPage: faixas da tabela (issue #128)', () => {
  const continentalTable = zonedTable(['QUALIFICATION', 'QUALIFICATION', 'NONE', 'PLAYOFF', 'RELEGATION'])

  it('pinta cada linha pela faixa que vem do contrato, sem recalcular pela posição', async () => {
    serveTables(continentalTable)
    renderWithProviders(<StandingsPage />)

    await screen.findByRole('link', { name: 'Clube 1' })
    expect(rowOf('Clube 1')).toHaveAttribute('data-zone', 'QUALIFICATION')
    expect(rowOf('Clube 1')).toHaveAttribute('title', 'Zona de vaga continental')
    expect(rowOf('Clube 1')?.className).toContain('border-indigo-400')
    expect(rowOf('Clube 3')).toHaveAttribute('data-zone', 'NONE')
    expect(rowOf('Clube 3')).not.toHaveAttribute('title')
    expect(rowOf('Clube 4')).toHaveAttribute('title', 'Zona de playoff')
    expect(rowOf('Clube 4')?.className).toContain('border-amber-400')
    expect(rowOf('Clube 5')).toHaveAttribute('title', 'Zona de rebaixamento')
    expect(rowOf('Clube 5')?.className).toContain('border-red-500')
  })

  it('a legenda mostra só as faixas presentes, com a contagem de linhas', async () => {
    serveTables(continentalTable)
    renderWithProviders(<StandingsPage />)

    const heading = await screen.findByRole('heading', { level: 2, name: 'Divisão 1' })
    const header = within(heading.parentElement as HTMLElement)
    expect(header.getByText('Vaga continental (2)')).toBeInTheDocument()
    expect(header.getByText('Playoff (1)')).toBeInTheDocument()
    expect(header.getByText('Rebaixamento (1)')).toBeInTheDocument()
    expect(header.queryByText(/Promoção/)).not.toBeInTheDocument()
  })

  it('liga fechada (tudo NONE) não pinta nem mostra legenda', async () => {
    serveTables(zonedTable(['NONE', 'NONE', 'NONE']))
    renderWithProviders(<StandingsPage />)

    await screen.findByRole('link', { name: 'Clube 1' })
    expect(screen.queryByText(/Rebaixamento|Promoção|Playoff|Vaga continental/)).not.toBeInTheDocument()
    expect(rowOf('Clube 2')).not.toHaveAttribute('title')
  })

  it('no seed do mock, a elite traz o rebaixamento e a segunda divisão o acesso', async () => {
    renderWithProviders(<StandingsPage />)

    await screen.findByRole('link', { name: 'Goal Father FC' })
    expect(screen.getAllByText('Rebaixamento (2)')).toHaveLength(1)
    expect(screen.getAllByText('Promoção (2)')).toHaveLength(1)
  })
})
