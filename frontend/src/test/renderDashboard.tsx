import { Route, Routes } from 'react-router-dom'
import { DashboardPage } from '../pages/DashboardPage'
import { dashboardTabRoutes } from '../pages/dashboard/routes'
import { renderWithProviders } from './render'

/** Dashboard com as mesmas sub-rotas do app (issue #107), aberta em `path`. */
export function renderDashboard(path = '/dashboard', options: Parameters<typeof renderWithProviders>[1] = {}) {
  return renderWithProviders(
    <Routes>
      <Route path="/dashboard" element={<DashboardPage />}>{dashboardTabRoutes}</Route>
    </Routes>,
    { initialEntries: [path], ...options },
  )
}
