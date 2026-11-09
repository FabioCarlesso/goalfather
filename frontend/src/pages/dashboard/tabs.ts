import { useOutletContext } from 'react-router-dom'
import type { Club } from '../../domain/types'

/**
 * Abas da Dashboard (issue #107), como sub-rotas `/dashboard/<path>`: o link de
 * cada aba é compartilhável, recarregar mantém a aba e o "voltar" funciona.
 */
export const DASHBOARD_TABS = [
  { path: 'visao-geral',  label: 'Visão geral' },
  { path: 'elenco',       label: 'Elenco' },
  { path: 'financas',     label: 'Finanças' },
  { path: 'departamento', label: 'Departamento' },
] as const

/** Aba padrão: é onde ficam as propostas, destino do badge do cabeçalho. */
export const DEFAULT_DASHBOARD_TAB = `/dashboard/${DASHBOARD_TABS[0].path}`

/**
 * Clube já carregado pela `DashboardPage`, entregue às abas pelo `<Outlet context>`.
 * A página só renderiza o `Outlet` depois do loading/erro, então aqui ele existe.
 */
export const useDashboardClub = (): Club => useOutletContext<Club>()
