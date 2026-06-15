import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { api } from '../api/client'
import { getToken, setToken, clearToken } from '../api/token'
import { onSessionExpired } from '../api/sessionEvents'
import type { AuthUser, AuthResponse } from '../domain/types'

/**
 * Sessão de autenticação (issue #18). Estado de servidor "leve" que não cabe
 * no TanStack Query porque dirige o roteamento (guardas de rota) — fica num
 * Context dedicado. Quem fala com a API de auth é só este provider.
 */
type Status = 'loading' | 'authenticated' | 'unauthenticated'

interface AuthContextValue {
  user: AuthUser | null
  status: Status
  login: (username: string, password: string) => Promise<void>
  register: (username: string, password: string) => Promise<void>
  logout: () => void
  /** Atualiza o clube local após reivindicar (issue #19) — sem refetch. */
  setClubId: (clubId: number) => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth deve ser usado dentro de <AuthProvider>')
  return ctx
}

/** Estado inicial injetável — usado nos testes para evitar a chamada a /me. */
export interface InitialAuth {
  user: AuthUser | null
  status: Status
}

export function AuthProvider({
  children,
  initialAuth,
}: {
  children: ReactNode
  initialAuth?: InitialAuth
}) {
  const [user, setUser] = useState<AuthUser | null>(initialAuth?.user ?? null)
  const [status, setStatus] = useState<Status>(
    initialAuth?.status ?? (getToken() ? 'loading' : 'unauthenticated'),
  )

  // Restaura a sessão no load: com token salvo, busca /me (sobrevive a refresh).
  // Sem token, o status inicial já é 'unauthenticated' (no `useState` acima),
  // então o efeito só roda quando há o que validar — evitando `setState`
  // síncrono no corpo do efeito.
  useEffect(() => {
    if (initialAuth || !getToken()) return
    let active = true
    api
      .get<AuthUser>('/api/auth/me')
      .then((u) => {
        if (active) {
          setUser(u)
          setStatus('authenticated')
        }
      })
      .catch(() => {
        if (active) {
          clearToken()
          setUser(null)
          setStatus('unauthenticated')
        }
      })
    return () => {
      active = false
    }
  }, [initialAuth])

  // Sessão expirou no meio do uso: um 401 inesperado em qualquer `/api/**`
  // (não no login) derruba a sessão aqui → guardas de rota levam ao /login
  // (issue #28). Os setters do `useState` são estáveis, então `[]` basta.
  useEffect(() => {
    return onSessionExpired(() => {
      clearToken()
      setUser(null)
      setStatus('unauthenticated')
    })
  }, [])

  const apply = (res: AuthResponse) => {
    setToken(res.token)
    setUser(res.user)
    setStatus('authenticated')
  }

  const login = async (username: string, password: string) => {
    apply(await api.post<AuthResponse>('/api/auth/login', { username, password }))
  }

  const register = async (username: string, password: string) => {
    apply(await api.post<AuthResponse>('/api/auth/register', { username, password }))
  }

  const logout = () => {
    clearToken()
    setUser(null)
    setStatus('unauthenticated')
  }

  const setClubId = (clubId: number) => {
    setUser((u) => (u ? { ...u, clubId } : u))
  }

  return (
    <AuthContext.Provider value={{ user, status, login, register, logout, setClubId }}>
      {children}
    </AuthContext.Provider>
  )
}
