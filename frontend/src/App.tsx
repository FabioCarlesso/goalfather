import { BrowserRouter, Routes, Route, Navigate, Outlet } from 'react-router-dom'
import { Toaster } from 'react-hot-toast'
import { AuthProvider, useAuth } from './auth/AuthContext'
import { DashboardPage } from './pages/DashboardPage'
import { StandingsPage } from './pages/StandingsPage'
import { HistoryPage } from './pages/HistoryPage'
import { CupPage } from './pages/CupPage'
import { MarketPage } from './pages/MarketPage'
import { LineupPage } from './pages/LineupPage'
import { RoundPage } from './pages/RoundPage'
import { MatchPage } from './pages/MatchPage'
import { WelcomePage } from './pages/WelcomePage'
import { LoginPage } from './pages/LoginPage'
import { SelectClubPage } from './pages/SelectClubPage'
import { FiredPage } from './pages/FiredPage'
import { ClubPage } from './pages/ClubPage'
import { AppLayout } from './components/layout/AppLayout'
import { isOnboarded } from './lib/onboarding'

/** Só autenticados passam; senão volta ao login (issue #18). */
function RequireAuth() {
  const { status } = useAuth()
  if (status === 'loading') return <p className="text-slate-400">Carregando sessão…</p>
  if (status === 'unauthenticated') return <Navigate to="/login" replace />
  return <Outlet />
}

/**
 * Autenticado mas sem clube → escolhe um primeiro (issue #19). Sem clube POR
 * DEMISSÃO (issue #62) → antes, a tela de game over, que leva à escolha.
 */
function RequireClub() {
  const { user } = useAuth()
  if (user?.clubId == null) {
    return <Navigate to={user?.dismissal ? '/fired' : '/select-club'} replace />
  }
  return <Outlet />
}

/** Raiz: primeira visita cai no tutorial; depois vai direto ao dashboard. */
function RootRedirect() {
  return <Navigate to={isOnboarded() ? '/dashboard' : '/welcome'} replace />
}

/** Quem já está logado não vê o login. */
function PublicOnly() {
  const { status } = useAuth()
  if (status === 'authenticated') return <Navigate to="/" replace />
  return <Outlet />
}

/** Layout sem cabeçalho — login e seleção de clube. */
function BareLayout() {
  return (
    <main className="mx-auto max-w-5xl px-6 py-12">
      <Outlet />
    </main>
  )
}

export function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Toaster
          position="bottom-right"
          toastOptions={{
            duration: 4000,
            error: { duration: Infinity }, // erros persistem até dismiss (issue #6)
            style: { background: '#1e293b', color: '#e2e8f0', border: '1px solid #334155' },
          }}
        />
        <div className="min-h-full bg-slate-950 text-slate-200">
          <Routes>
            {/* Públicas / sem clube — layout sem cabeçalho */}
            <Route element={<BareLayout />}>
              <Route element={<PublicOnly />}>
                <Route path="/login" element={<LoginPage />} />
              </Route>
              <Route element={<RequireAuth />}>
                <Route path="/select-club" element={<SelectClubPage />} />
                <Route path="/fired" element={<FiredPage />} />
              </Route>
            </Route>

            {/* App autenticado com clube — cabeçalho de navegação */}
            <Route element={<RequireAuth />}>
              <Route element={<RequireClub />}>
                <Route element={<AppLayout />}>
                  <Route path="/" element={<RootRedirect />} />
                  <Route path="/welcome"   element={<WelcomePage />} />
                  <Route path="/dashboard" element={<DashboardPage />} />
                  <Route path="/lineup"    element={<LineupPage />} />
                  <Route path="/round"     element={<RoundPage />} />
                  <Route path="/round/match/:matchId" element={<MatchPage />} />
                  <Route path="/market"    element={<MarketPage />} />
                  <Route path="/standings" element={<StandingsPage />} />
                  <Route path="/clubs/:id" element={<ClubPage />} />
                  <Route path="/cup"       element={<CupPage />} />
                  <Route path="/history"   element={<HistoryPage />} />
                </Route>
              </Route>
            </Route>
          </Routes>
        </div>
      </BrowserRouter>
    </AuthProvider>
  )
}
