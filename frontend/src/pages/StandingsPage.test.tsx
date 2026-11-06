import { describe, expect, it } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import { StandingsPage } from './StandingsPage'
import { renderWithProviders } from '../test/render'

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
