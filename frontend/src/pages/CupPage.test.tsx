import { afterEach, describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { CupPage } from './CupPage'
import { renderWithProviders } from '../test/render'
import { server } from '../test/setup'
import { state } from '../mocks/seed'
import { cupRound, recordCupRound } from '../mocks/cup'

describe('CupPage (issue #61)', () => {
  const seeded = state.cup

  afterEach(() => {
    state.cup = seeded
  })

  it('desenha a chave inteira: fases sorteadas, folgas e vagas a definir', async () => {
    renderWithProviders(<CupPage />)

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Copa 2026' })).toBeInTheDocument()
    })
    const r16 = screen.getByTestId('cup-phase-ROUND_OF_16')
    expect(within(r16).getByText('Oitavas de final')).toBeInTheDocument()
    expect(within(r16).getAllByText(/folga/)).toHaveLength(4)
    expect(within(r16).getAllByText('a disputar')).toHaveLength(4)
    // Fase ainda não sorteada: as vagas aparecem, sem clubes.
    expect(within(screen.getByTestId('cup-phase-FINAL')).getByText('A definir')).toBeInTheDocument()
    // Prêmio de cada fase vem do contrato.
    expect(within(screen.getByTestId('cup-phase-FINAL')).getByText(/Prêmio por vitória/)).toBeInTheDocument()
  })

  it('mostra placar, pênaltis e o campeão quando a copa acaba', async () => {
    let cup = state.cup!
    for (let date = 2; cup.currentPhase; date++) {
      const round = cupRound(date, 2026, cup, () => 1)
      cup = recordCupRound(cup, {
        ...round,
        matches: round.matches.map((m) => ({
          ...m, status: 'Finished' as const, homeGoals: 2, awayGoals: 2, penalties: { home: 4, away: 2 },
        })),
      }).cup
    }
    state.cup = cup

    renderWithProviders(<CupPage />)

    await waitFor(() => {
      expect(screen.getByText(new RegExp(`${cup.champion!.name} é campeão da copa`))).toBeInTheDocument()
    })
    expect(screen.getAllByText('(4)').length).toBeGreaterThan(0)
  })

  it('liga sem copa mostra o estado vazio em vez de erro', async () => {
    server.use(
      http.get('/api/league/cup', () =>
        HttpResponse.json({ code: 'CUP_NOT_FOUND', message: 'sem copa' }, { status: 404 })),
    )

    renderWithProviders(<CupPage />)

    await waitFor(() => {
      expect(screen.getByText(/Nenhuma copa nesta temporada/)).toBeInTheDocument()
    })
  })
})
