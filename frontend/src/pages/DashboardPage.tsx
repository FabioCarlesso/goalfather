import { NavLink, Outlet } from 'react-router-dom'
import { PageTitle } from '../components/PageTitle'
import { useClub } from '../api/queries/useClub'
import { useMyClubId } from '../auth/useMyClubId'
import { ErrorState } from '../components/ui/ErrorState'
import { LoadingState } from '../components/ui/LoadingState'
import { cx } from '../components/ui/cx'
import { DASHBOARD_TABS } from './dashboard/tabs'

/**
 * Dashboard do próprio clube (issue #107): cabeçalho + abas em sub-rotas. Cada
 * aba (`pages/dashboard/*Tab`) lê os próprios dados e passa por props aos
 * painéis de `components/`; aqui só carregamos o clube, que todas usam.
 */
export function DashboardPage() {
  const myClubId = useMyClubId()
  const { data: club, isPending, isLoadingError, error, refetch } = useClub(myClubId)

  if (isPending) return <><PageTitle>Clube</PageTitle><LoadingState label="Carregando clube…" shape="cards" /></>
  if (isLoadingError) return <><PageTitle>Clube</PageTitle><ErrorState error={error} onRetry={refetch} /></>

  return (
    <section className="space-y-6">
      <PageTitle>Clube</PageTitle>
      <header>
        <h1 className="text-3xl font-bold text-slate-100">{club.name}</h1>
      </header>

      {/* `overflow-y-hidden` explícito: com só `overflow-x-auto`, o eixo Y vira
          `auto` e qualquer pixel a mais de altura desenha uma barra vertical. */}
      <nav aria-label="Seções do clube" className="flex gap-1 overflow-x-auto overflow-y-hidden border-b border-slate-800">
        {DASHBOARD_TABS.map((tab) => (
          // `NavLink` marca a aba ativa com `aria-current="page"` sozinho.
          <NavLink
            key={tab.path}
            to={tab.path}
            className={({ isActive }) =>
              cx(
                'whitespace-nowrap border-b-2 px-3 py-2 text-sm font-medium transition-colors',
                'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400',
                isActive
                  ? 'border-emerald-500 text-slate-100'
                  : 'border-transparent text-slate-400 hover:text-slate-200',
              )
            }
          >
            {tab.label}
          </NavLink>
        ))}
      </nav>

      {/* O clube já carregado desce para a aba ativa — ver `useDashboardClub`. */}
      <Outlet context={club} />
    </section>
  )
}
