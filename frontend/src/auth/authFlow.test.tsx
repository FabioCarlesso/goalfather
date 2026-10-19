import { describe, expect, it, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClientProvider } from '@tanstack/react-query'
import { App } from '../App'
import { makeQueryClient } from '../test/render'
import { auth, TOKEN_PREFIX } from '../mocks/seed'
import { setToken } from '../api/token'

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

  it('técnico demitido cai na tela de game over e recomeça pela seleção (issue #62)', async () => {
    const user = userEvent.setup()
    // Usuário que a diretoria demitiu na virada: sem clube, com a demissão.
    const fired = {
      id: auth.nextId++,
      username: 'demitido',
      password: 'secret123',
      clubId: null,
      dismissal: {
        clubId: 1, clubName: 'Goal Father FC', season: 2026,
        objective: 'TITLE' as const, targetPosition: 1, finalPosition: 6,
      },
    }
    auth.users.push(fired)
    setToken(TOKEN_PREFIX + fired.id)
    window.history.pushState({}, '', '/dashboard')

    render(
      <QueryClientProvider client={makeQueryClient()}>
        <App />
      </QueryClientProvider>,
    )

    expect(await screen.findByRole('heading', { name: 'Você foi demitido' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Recomeçar em outro clube' }))
    expect(await screen.findByRole('heading', { name: 'Escolha seu clube' })).toBeInTheDocument()
  })
})
