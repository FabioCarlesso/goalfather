import { describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { StandingsPage } from './StandingsPage'
import { renderWithProviders } from '../test/render'
import { server } from '../test/setup'
import { leagueOfTable } from '../test/fixtures'
import type { Standings, TableZone } from '../domain/types'

/** Tabela fabricada com a faixa de cada posição já decidida pelo "backend". */
const zonedTable = (zones: TableZone[]): Standings => ({
  ...leagueOfTable(),
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
const tableTitle = () => screen.getByRole('heading', { level: 2 })

describe('StandingsPage (issues #108 e #131)', () => {
  it('destaca a linha do clube do usuário com aria-current', async () => {
    renderWithProviders(<StandingsPage />)

    const link = await screen.findByRole('link', { name: 'Flamengo' })
    expect(link.closest('tr')).toHaveAttribute('aria-current', 'true')
    expect(screen.getByText(/seu clube/)).toBeInTheDocument()

    const rival = screen.getByRole('link', { name: 'Palmeiras' })
    expect(rival.closest('tr')).not.toHaveAttribute('aria-current')
  })

  it('abre na liga do técnico, com o nome e a temporada da liga no título', async () => {
    // Clube 13 (Liverpool) é da Premier League no seed do mock.
    renderWithProviders(<StandingsPage />, {
      auth: { user: { id: 7, username: 'reds', clubId: 13 }, status: 'authenticated' },
    })

    await screen.findByRole('link', { name: 'Liverpool' })
    expect(tableTitle()).toHaveTextContent('Premier League 2026/27')
    expect(screen.getByText(/Inglaterra · Rodada 0/)).toBeInTheDocument()
    expect(screen.getByLabelText('Liga')).toHaveValue('eng-premier-league')
    expect(rowOf('Liverpool')).toHaveAttribute('aria-current', 'true')
    // Uma liga por vez: a Série A não aparece junto.
    expect(screen.queryByRole('link', { name: 'Flamengo' })).not.toBeInTheDocument()
  })

  it('o seletor agrupa as ligas por país e troca a tabela exibida', async () => {
    const user = userEvent.setup()
    renderWithProviders(<StandingsPage />)
    await screen.findByRole('link', { name: 'Flamengo' })
    expect(tableTitle()).toHaveTextContent('Campeonato Brasileiro Série A 2026')

    const select = screen.getByLabelText('Liga')
    const groups = within(select).getAllByRole('group')
    expect(groups.map((g) => g.getAttribute('label'))).toEqual(['🇧🇷 Brasil', expect.stringMatching(/Inglaterra$/)])
    expect(within(groups[0]!).getAllByRole('option').map((o) => o.textContent)).toEqual([
      'Campeonato Brasileiro Série A (sua liga)',
      'Campeonato Brasileiro Série B',
    ])

    await user.selectOptions(select, 'bra-serie-b')
    await waitFor(() => expect(tableTitle()).toHaveTextContent('Campeonato Brasileiro Série B 2026'))
    expect(screen.getByRole('link', { name: 'Fortaleza' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Flamengo' })).not.toBeInTheDocument()
  })

  it('a liga escolhida na URL vence a do técnico', async () => {
    renderWithProviders(<StandingsPage />, { initialEntries: ['/standings?league=eng-premier-league'] })

    await screen.findByRole('link', { name: 'Arsenal' })
    expect(tableTitle()).toHaveTextContent('Premier League 2026/27')
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

    await screen.findByRole('link', { name: 'Clube 1' })
    expect(screen.getByText('Vaga continental (2)')).toBeInTheDocument()
    expect(screen.getByText('Playoff (1)')).toBeInTheDocument()
    expect(screen.getByText('Rebaixamento (1)')).toBeInTheDocument()
    expect(screen.queryByText(/Promoção/)).not.toBeInTheDocument()
  })

  it('liga fechada (tudo NONE) não pinta nem mostra legenda', async () => {
    serveTables(zonedTable(['NONE', 'NONE', 'NONE']))
    renderWithProviders(<StandingsPage />)

    await screen.findByRole('link', { name: 'Clube 1' })
    expect(screen.queryByText(/Rebaixamento|Promoção|Playoff|Vaga continental/)).not.toBeInTheDocument()
    expect(rowOf('Clube 2')).not.toHaveAttribute('title')
  })

  it('no seed do mock, a Série A traz o rebaixamento e a Série B o acesso', async () => {
    const user = userEvent.setup()
    renderWithProviders(<StandingsPage />)

    await screen.findByRole('link', { name: 'Flamengo' })
    expect(screen.getByText('Rebaixamento (2)')).toBeInTheDocument()
    expect(screen.queryByText(/Promoção/)).not.toBeInTheDocument()

    await user.selectOptions(screen.getByLabelText('Liga'), 'bra-serie-b')
    expect(await screen.findByText('Promoção (2)')).toBeInTheDocument()
    expect(screen.queryByText(/Rebaixamento/)).not.toBeInTheDocument()
  })
})
