import { afterEach, describe, expect, it } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HistoryPage } from './HistoryPage'
import { renderWithProviders } from '../test/render'
import { seasonRecordOf, state } from '../mocks/seed'
import type { Standings } from '../domain/types'

const finalTables: Standings[] = [
  {
    season: 2026,
    division: 1,
    round: 5,
    promotionSpots: 0,
    relegationSpots: 2,
    rows: [1, 2, 3].map((clubId, i) => ({
      position: i + 1,
      clubId,
      clubName: `Club ${clubId}`,
      played: 5, wins: 0, draws: 0, losses: 0,
      goalsFor: 0, goalsAgainst: 0, goalDifference: 0,
      points: 15 - i * 3,
    })),
  },
]

describe('HistoryPage (issue #60)', () => {
  afterEach(() => {
    state.history = []
  })

  it('mostra o estado vazio enquanto nenhuma temporada terminou', async () => {
    renderWithProviders(<HistoryPage />)

    await waitFor(() => {
      expect(screen.getByText(/nenhuma temporada encerrada ainda/i)).toBeInTheDocument()
    })
  })

  it('lista temporadas com campeão e artilheiro e detalha a escolhida', async () => {
    const user = userEvent.setup()
    // Artilheiro plantado: o record é montado a partir do elenco vigente.
    const my = state.clubs[1]!
    state.clubs[1] = { ...my, squad: my.squad.map((p, i) => (i === 9 ? { ...p, goals: 21 } : p)) }
    state.history = [seasonRecordOf(2026, finalTables)]

    renderWithProviders(<HistoryPage />)

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /2026/ })).toBeInTheDocument()
    })
    expect(screen.getByText(/🏆 Club 1/)).toBeInTheDocument()
    expect(screen.getByText(/⚽ Renato Silva \(21\)/)).toBeInTheDocument()

    // Drill-down: a classificação final vem do endpoint da temporada.
    await user.click(screen.getByRole('button', { name: /2026/ }))
    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Temporada 2026' })).toBeInTheDocument()
    })
    expect(screen.getByText('Divisão 1')).toBeInTheDocument()
    // Aproveitamento calculado no snapshot: 15 pontos em 5 jogos.
    expect(screen.getByText('100%')).toBeInTheDocument()

    state.clubs[1] = my
  })

  it('mostra o perfil do técnico com títulos e melhor campanha', async () => {
    state.history = [seasonRecordOf(2026, finalTables)]

    renderWithProviders(<HistoryPage />)

    await waitFor(() => {
      expect(screen.getByText('Temporadas')).toBeInTheDocument()
    })
    expect(screen.getByText('Títulos')).toBeInTheDocument()
    // Clube 1 (o do técnico nos testes) terminou em 1º na elite.
    expect(screen.getByText(/1º na divisão 1 em 2026/)).toBeInTheDocument()
  })
})
