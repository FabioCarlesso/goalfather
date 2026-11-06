import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from './auth/AuthContext'
import { isOnboarded } from './lib/onboarding'

/** Só autenticados passam; senão volta ao login (issue #18). */
export function RequireAuth() {
  const { status } = useAuth()
  if (status === 'loading') return <p className="text-slate-400">Carregando sessão…</p>
  if (status === 'unauthenticated') return <Navigate to="/login" replace />
  return <Outlet />
}

/**
 * Autenticado mas sem clube → escolhe um primeiro (issue #19). Sem clube POR
 * DEMISSÃO (issue #62) → antes, a tela de game over, que leva à escolha.
 */
export function RequireClub() {
  const { user } = useAuth()
  if (user?.clubId == null) {
    return <Navigate to={user?.dismissal ? '/fired' : '/select-club'} replace />
  }
  return <Outlet />
}

/** Raiz: primeira visita cai no tutorial; depois vai direto ao dashboard. */
export function RootRedirect() {
  return <Navigate to={isOnboarded() ? '/dashboard' : '/welcome'} replace />
}

/** Quem já está logado não vê o login. */
export function PublicOnly() {
  const { status } = useAuth()
  if (status === 'authenticated') return <Navigate to="/" replace />
  return <Outlet />
}

/** Layout sem cabeçalho — login e seleção de clube. */
export function BareLayout() {
  return (
    <main className="mx-auto max-w-5xl px-6 py-12">
      <Outlet />
    </main>
  )
}
