import { useState, type ReactElement, type ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { render, type RenderOptions } from '@testing-library/react'
import { AuthProvider, type InitialAuth } from '../auth/AuthContext'
import type { AuthUser } from '../domain/types'

/** Usuário autenticado padrão dos testes — dono do clube 1 (o do seed). */
const DEFAULT_USER: AuthUser = { id: 1, username: 'tester', clubId: 1 }
const DEFAULT_AUTH: InitialAuth = { user: DEFAULT_USER, status: 'authenticated' }

/** Cria um QueryClient sem retry para tornar testes deterministicos e rapidos. */
export function makeQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false, refetchOnWindowFocus: false, staleTime: 0, gcTime: 0 },
      mutations: { retry: false },
    },
  })
}

interface Options extends Omit<RenderOptions, 'wrapper'> {
  initialEntries?: string[]
  queryClient?: QueryClient
  /** Estado de auth injetado (default: autenticado, dono do clube 1). */
  auth?: InitialAuth
}

/**
 * Renderiza componente sob QueryClient + AuthProvider + data router (jsdom).
 * Data router, igual ao app, porque `useBlocker` lança erro sem ele (issue #109).
 * A rota curinga aceita qualquer `initialEntries` e `<Routes>` descendentes.
 * O router guarda o elemento da primeira renderização: o `rerender` do RTL não
 * troca a UI aqui; para isso, use `render` direto.
 */
export function renderWithProviders(ui: ReactElement, options: Options = {}) {
  const { initialEntries = ['/'], queryClient = makeQueryClient(), auth = DEFAULT_AUTH, ...rest } = options

  const Wrapper = ({ children }: { children: ReactNode }) => {
    const [router] = useState(() =>
      createMemoryRouter([{ path: '*', element: children }], { initialEntries }),
    )
    return (
      <QueryClientProvider client={queryClient}>
        <AuthProvider initialAuth={auth}>
          <RouterProvider router={router} />
        </AuthProvider>
      </QueryClientProvider>
    )
  }

  return { queryClient, ...render(ui, { wrapper: Wrapper, ...rest }) }
}
