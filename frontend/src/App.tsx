import { BrowserRouter, Routes, Route, NavLink, Navigate, Outlet, useNavigate } from 'react-router-dom'
import { Toaster } from 'react-hot-toast'
import { AuthProvider, useAuth } from './auth/AuthContext'
import { DashboardPage } from './pages/DashboardPage'
import { StandingsPage } from './pages/StandingsPage'
import { MarketPage } from './pages/MarketPage'
import { LineupPage } from './pages/LineupPage'
import { RoundPage } from './pages/RoundPage'
import { MatchPage } from './pages/MatchPage'
import { WelcomePage } from './pages/WelcomePage'
import { LoginPage } from './pages/LoginPage'
import { SelectClubPage } from './pages/SelectClubPage'
import { isOnboarded } from './lib/onboarding'

const nav = [
  { to: '/dashboard', label: 'Clube' },
  { to: '/lineup',    label: 'Escalação' },
  { to: '/round',     label: 'Rodada' },
  { to: '/market',    label: 'Mercado' },
  { to: '/standings', label: 'Tabela' },
  { to: '/welcome',   label: 'Tutorial' },
] as const

/** Só autenticados passam; senão volta ao login (issue #18). */
function RequireAuth() {
  const { status } = useAuth()
  if (status === 'loading') return <p className="text-slate-400">Carregando sessão…</p>
  if (status === 'unauthenticated') return <Navigate to="/login" replace />
  return <Outlet />
}

/** Autenticado mas sem clube → escolhe um primeiro (issue #19). */
function RequireClub() {
  const { user } = useAuth()
  if (user?.clubId == null) return <Navigate to="/select-club" replace />
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

/** Layout do app autenticado — cabeçalho de navegação + logout. */
function AppLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const onLogout = () => {
    logout()
    navigate('/login')
  }

  return (
    <>
      <header className="border-b border-slate-800 bg-slate-900/80 backdrop-blur">
        <div className="mx-auto max-w-5xl px-6 py-3 flex items-center gap-6">
          <span className="text-xl">⚽ <strong className="text-slate-100">GoalFather</strong></span>
          <nav className="flex gap-1 ml-4">
            {nav.map((n) => (
              <NavLink
                key={n.to}
                to={n.to}
                className={({ isActive }) =>
                  `px-3 py-1.5 rounded-md text-sm transition-colors ${
                    isActive
                      ? 'bg-slate-800 text-slate-100'
                      : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/50'
                  }`
                }
              >
                {n.label}
              </NavLink>
            ))}
          </nav>
          <div className="ml-auto flex items-center gap-3">
            {user && <span className="text-xs text-slate-400">{user.username}</span>}
            <button
              onClick={onLogout}
              className="rounded-md border border-slate-700 px-2.5 py-1 text-xs text-slate-400 transition-colors hover:border-red-600 hover:text-red-400"
            >
              Sair
            </button>
            <span className="text-xs text-slate-500 font-mono">
              {import.meta.env.VITE_USE_MOCKS === 'false' ? 'API real' : 'MSW mock'}
            </span>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-5xl px-6 py-8">
        <Outlet />
      </main>
    </>
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
                </Route>
              </Route>
            </Route>
          </Routes>
        </div>
      </BrowserRouter>
    </AuthProvider>
  )
}
