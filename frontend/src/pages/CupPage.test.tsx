import { afterEach, describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { CupPage } from './CupPage'
import { renderWithProviders } from '../test/render'
import { server } from '../test/setup'
import { state } from '../mocks/seed'
import { cupRound, recordCupRound } from '../mocks/cup'
import { matchdayOf } from '../mocks/calendar'

describe('CupPage (issues #61 e #131)', () => {
  const seeded = state.cup

  afterEach(() => {
    state.cup = seeded
  })

  it('desenha a chave inteira: fases sorteadas, formato, entrantes e vagas a definir', async () => {
    renderWithProviders(<CupPage />)

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Copa do Brasil 2026' })).toBeInTheDocument()
    })
    const opening = screen.getByTestId('cup-phase-primeira-fase')
    expect(within(opening).getByText('Primeira fase')).toBeInTheDocument()
    expect(within(opening).getByText(/jogo único/)).toBeInTheDocument()
    expect(within(opening).getAllByText('a disputar')).toHaveLength(4)
    // Quartas ainda não sorteadas: vagas a definir e os 4 que entram nelas.
    const quarters = screen.getByTestId('cup-phase-quartas-de-final')
    expect(within(quarters).getByText(/ida e volta/)).toBeInTheDocument()
    expect(within(quarters).getByText('Entram nesta fase: 4 clubes')).toBeInTheDocument()
    expect(within(quarters).getAllByText('A definir')).toHaveLength(4)
    // O clube do técnico (o mais forte do seed) só entra nas quartas.
    expect(screen.getByText('Seu clube entra na copa em: Quartas de final.')).toBeInTheDocument()
    // Prêmio de cada fase vem do contrato.
    expect(within(screen.getByTestId('cup-phase-final')).getByText(/Prêmio por vitória/)).toBeInTheDocument()
  })

  it('mostra ida, volta, agregado, pênaltis e o campeão quando a copa acaba', async () => {
    let cup = state.cup!
    for (let date = 2; cup.currentStage; date++) {
      const round = cupRound(matchdayOf(2026, date, 'MIDWEEK'), 2026, cup, () => 1)
      cup = recordCupRound(cup, {
        ...round,
        // Todo jogo 2 × 2: jogo único e voltas (agregado 4 × 4) vão aos pênaltis.
        matches: round.matches.map((m) => ({
          ...m, status: 'Finished' as const, homeGoals: 2, awayGoals: 2,
          ...(m.stage.type === 'Cup' && m.stage.leg !== 'FIRST' ? { penalties: { home: 4, away: 2 } } : {}),
        })),
      }).cup
    }
    state.cup = cup

    renderWithProviders(<CupPage />)

    await waitFor(() => {
      expect(screen.getByText(new RegExp(`${cup.champion!.name} é campeão da copa`))).toBeInTheDocument()
    })
    expect(screen.getAllByText('(4)').length).toBeGreaterThan(0)
    // Agregado 4 × 4 nos confrontos de ida e volta.
    const quarters = screen.getByTestId('cup-phase-quartas-de-final')
    expect(within(quarters).getAllByText('4').length).toBeGreaterThanOrEqual(8)
    expect(within(quarters).getAllByText('ida · volta · agregado')).toHaveLength(4)
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
