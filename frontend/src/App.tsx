import { BrowserRouter, Routes, Route, NavLink, Navigate } from 'react-router-dom'
import { DashboardPage } from './pages/DashboardPage'
import { StandingsPage } from './pages/StandingsPage'
import { MarketPage } from './pages/MarketPage'
import { MatchPage } from './pages/MatchPage'
import { LineupPage } from './pages/LineupPage'

const nav = [
  { to: '/dashboard', label: 'Clube' },
  { to: '/lineup',    label: 'Escalação' },
  { to: '/match',     label: 'Partida' },
  { to: '/market',    label: 'Mercado' },
  { to: '/standings', label: 'Tabela' },
] as const

export function App() {
  return (
    <BrowserRouter>
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
            <Route path="/" element={<Navigate to="/dashboard" replace />} />
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/lineup"    element={<LineupPage />} />
            <Route path="/match"     element={<MatchPage />} />
            <Route path="/market"    element={<MarketPage />} />
            <Route path="/standings" element={<StandingsPage />} />
          </Routes>
        </main>
      </div>
    </BrowserRouter>
  )
}
