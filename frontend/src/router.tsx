import { createBrowserRouter, createRoutesFromElements, Route } from 'react-router-dom'
import { DashboardPage } from './pages/DashboardPage'
import { dashboardTabRoutes } from './pages/dashboard/routes'
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
import { RouteErrorPage } from './pages/RouteErrorPage'
import { BareLayout, PublicOnly, RequireAuth, RequireClub, RootRedirect } from './routeGuards'

// Data router (issue #109): `useBlocker`, que protege a escalação não salva, só
// funciona sob `RouterProvider` (e lança erro sob `<BrowserRouter>`). Criado uma
// única vez, no módulo, porque ele guarda o estado da navegação; recriar em
// render reiniciaria o histórico.
export const router = createBrowserRouter(
  createRoutesFromElements(
    <Route errorElement={<RouteErrorPage />}>
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
            <Route path="/dashboard" element={<DashboardPage />}>{dashboardTabRoutes}</Route>
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
    </Route>,
  ),
)
