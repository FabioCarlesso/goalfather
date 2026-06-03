import type { ReactElement, ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
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

/** Renderiza componente sob QueryClient + AuthProvider + MemoryRouter (jsdom). */
export function renderWithProviders(ui: ReactElement, options: Options = {}) {
  const { initialEntries = ['/'], queryClient = makeQueryClient(), auth = DEFAULT_AUTH, ...rest } = options

  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>
      <AuthProvider initialAuth={auth}>
        <MemoryRouter initialEntries={initialEntries}>{children}</MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>
  )

  return { queryClient, ...render(ui, { wrapper: Wrapper, ...rest }) }
}
