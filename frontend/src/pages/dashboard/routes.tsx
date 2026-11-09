import { Navigate, Route } from 'react-router-dom'
import { DepartmentTab } from './DepartmentTab'
import { FinancesTab } from './FinancesTab'
import { OverviewTab } from './OverviewTab'
import { SquadTab } from './SquadTab'
import { DEFAULT_DASHBOARD_TAB } from './tabs'

/**
 * Sub-rotas da Dashboard (issue #107), filhas de `/dashboard`. Ficam num
 * fragmento compartilhado para o `router.tsx` e o teste da página montarem a
 * mesma árvore: `createRoutesFromElements` e `<Routes>` achatam fragmentos.
 * `/dashboard` puro (e qualquer aba desconhecida) cai na visão geral.
 */
export const dashboardTabRoutes = (
  <>
    <Route index element={<Navigate to={DEFAULT_DASHBOARD_TAB} replace />} />
    <Route path="visao-geral"  element={<OverviewTab />} />
    <Route path="elenco"       element={<SquadTab />} />
    <Route path="financas"     element={<FinancesTab />} />
    <Route path="departamento" element={<DepartmentTab />} />
    <Route path="*" element={<Navigate to={DEFAULT_DASHBOARD_TAB} replace />} />
  </>
)
