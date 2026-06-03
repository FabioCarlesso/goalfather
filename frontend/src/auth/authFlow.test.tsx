import { describe, expect, it, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClientProvider } from '@tanstack/react-query'
import { App } from '../App'
import { makeQueryClient } from '../test/render'

/**
 * Fluxo ponta a ponta das issues #18/#19 contra os mocks MSW: um novo usuário
 * se cadastra, é levado a `/select-club`, reivindica um clube e cai no
 * dashboard daquele clube. Cobre os critérios de aceite principais.
 */
describe('Fluxo de auth + seleção de clube', () => {
  beforeEach(() => {
    localStorage.clear()
    window.history.pushState({}, '', '/')
  })

  it('cadastra, escolhe clube e chega ao dashboard', async () => {
    const user = userEvent.setup()
    render(
      <QueryClientProvider client={makeQueryClient()}>
        <App />
      </QueryClientProvider>,
    )

    // Sem token → tela de login. Alterna para cadastro.
    await user.click(await screen.findByRole('button', { name: /cadastre-se/i }))
    await user.type(screen.getByLabelText(/usuário/i), 'fabio')
    await user.type(screen.getByLabelText(/senha/i), 'secret123')
    await user.click(screen.getByRole('button', { name: /cadastrar/i }))

    // Autenticado mas sem clube → seleção de clube.
    expect(await screen.findByRole('heading', { name: 'Escolha seu clube' })).toBeInTheDocument()

    // Reivindica o primeiro clube (Goal Father FC).
    const claimButtons = await screen.findAllByRole('button', { name: /treinar este clube/i })
    await user.click(claimButtons[0]!)

    // Cai no dashboard do clube reivindicado.
    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Goal Father FC' })).toBeInTheDocument()
    })
  })
})
