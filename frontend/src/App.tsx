import { BrowserRouter, Routes, Route, NavLink, Navigate } from 'react-router-dom'
import { Toaster } from 'react-hot-toast'
import { DashboardPage } from './pages/DashboardPage'
import { StandingsPage } from './pages/StandingsPage'
import { MarketPage } from './pages/MarketPage'
import { LineupPage } from './pages/LineupPage'
import { RoundPage } from './pages/RoundPage'
import { WelcomePage, isOnboarded } from './pages/WelcomePage'

const nav = [
  { to: '/dashboard', label: 'Clube' },
  { to: '/lineup',    label: 'Escalação' },
  { to: '/round',     label: 'Rodada' },
  { to: '/market',    label: 'Mercado' },
  { to: '/standings', label: 'Tabela' },
  { to: '/welcome',   label: 'Tutorial' },
] as const

export function App() {
  return (
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
            <span className="ml-auto text-xs text-slate-500 font-mono">
              {import.meta.env.VITE_USE_MOCKS === 'false' ? 'API real' : 'MSW mock'}
            </span>
          </div>
        </header>

        <main className="mx-auto max-w-5xl px-6 py-8">
          <Routes>
            <Route
              path="/"
              element={<Navigate to={isOnboarded() ? '/dashboard' : '/welcome'} replace />}
            />
            <Route path="/welcome"   element={<WelcomePage />} />
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/lineup"    element={<LineupPage />} />
            <Route path="/round"     element={<RoundPage />} />
            <Route path="/market"    element={<MarketPage />} />
            <Route path="/standings" element={<StandingsPage />} />
          </Routes>
        </main>
      </div>
    </BrowserRouter>
  )
}
