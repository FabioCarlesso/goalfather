import { describe, expect, it, beforeEach } from 'vitest'
import { render, screen, act } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { AuthProvider, useAuth } from './AuthContext'
import { server } from '../test/setup'
import { api } from '../api/client'
import { onSessionExpired, emitSessionExpired } from '../api/sessionEvents'
import type { AuthUser, ErrorResponse } from '../domain/types'

const unauthorized = () =>
  HttpResponse.json(
    { code: 'UNAUTHORIZED', message: 'Autenticação necessária' } satisfies ErrorResponse,
    { status: 401 },
  )

/**
 * Issue #28: quando o token expira com o app aberto, qualquer `/api/**` passa a
 * responder 401. O tratamento é centralizado no `client.ts`, que sinaliza a
 * expiração; o `AuthProvider` reage derrubando a sessão (→ guardas levam ao
 * `/login`). O 401 esperado do próprio login (credenciais inválidas) não conta.
 */
describe('Recuperação de sessão ao receber 401 fora do /me', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('o client sinaliza expiração no 401 de /api/**, mas não no 401 do login', async () => {
    let fired = 0
    const off = onSessionExpired(() => {
      fired++
    })

    // 401 numa rota protegida qualquer → expira a sessão.
    server.use(http.get('/api/league/standings', unauthorized))
    await expect(api.get('/api/league/standings')).rejects.toThrow()
    expect(fired).toBe(1)

    // 401 no login é resposta de negócio (credenciais inválidas), não expira.
    server.use(http.post('/api/auth/login', unauthorized))
    await expect(
      api.post('/api/auth/login', { username: 'x', password: 'y' }),
    ).rejects.toThrow()
    expect(fired).toBe(1)

    off()
  })

  it('o AuthProvider cai para unauthenticated ao receber o sinal de expiração', () => {
    const user: AuthUser = { id: 1, username: 'tester', clubId: 1 }
    function StatusProbe() {
      return <span data-testid="status">{useAuth().status}</span>
    }

    render(
      <AuthProvider initialAuth={{ user, status: 'authenticated' }}>
        <StatusProbe />
      </AuthProvider>,
    )
    expect(screen.getByTestId('status')).toHaveTextContent('authenticated')

    // Sessão expira (um 401 inesperado chegou pelo client em algum lugar).
    act(() => {
      emitSessionExpired()
    })

    // As guardas de rota leem este status e redirecionam para /login.
    expect(screen.getByTestId('status')).toHaveTextContent('unauthenticated')
  })
})
