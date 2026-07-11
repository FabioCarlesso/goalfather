import { describe, expect, it } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import { SelectClubPage } from './SelectClubPage'
import { renderWithProviders } from '../test/render'

describe('SelectClubPage', () => {
  const noClubAuth = {
    user: { id: 9, username: 'novato', clubId: null },
    status: 'authenticated' as const,
  }

  it('lista os clubes disponíveis do mock com botão de reivindicar', async () => {
    renderWithProviders(<SelectClubPage />, { auth: noClubAuth })

    expect(screen.getByText(/carregando clubes/i)).toBeInTheDocument()

    await waitFor(() => {
      expect(screen.getByText('Goal Father FC')).toBeInTheDocument()
    })

    // Clubes do seed (12 em 2 divisões — issue #47) começam todos sem dono.
    expect(screen.getByText('Atlético Bonsucesso')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Escolha seu clube' })).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: /treinar este clube/i }).length).toBe(12)

    // Cada card informa a divisão do clube (issue #47, follow-up da review).
    expect(screen.getAllByText('Divisão 1').length).toBe(6)
    expect(screen.getAllByText('Divisão 2').length).toBe(6)
  })
})
