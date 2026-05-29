import { describe, expect, it } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import { DashboardPage } from './DashboardPage'
import { renderWithProviders } from '../test/render'

describe('DashboardPage', () => {
  it('renderiza o clube e elenco do mock', async () => {
    renderWithProviders(<DashboardPage />)

    expect(screen.getByText(/carregando clube/i)).toBeInTheDocument()

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Goal Father FC' })).toBeInTheDocument()
    })

    // Stat cards
    expect(screen.getByText('Caixa')).toBeInTheDocument()
    expect(screen.getByText('Capacidade estádio')).toBeInTheDocument()
    expect(screen.getByText('11 jogadores')).toBeInTheDocument()
    // "Elenco" aparece tanto no stat card quanto como heading da secao
    expect(screen.getAllByText('Elenco')).toHaveLength(2)

    // Alguns jogadores conhecidos do seed
    expect(screen.getByText('Marcos Figueiredo')).toBeInTheDocument()
    expect(screen.getByText('Renato Silva')).toBeInTheDocument()
  })
})
