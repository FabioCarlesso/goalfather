import { Link, useParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import { useCompetition } from '../api/queries/useCompetitions'
import { useMyClubId } from '../auth/useMyClubId'
import { CountryFlag } from '../components/CountryFlag'
import { PageTitle } from '../components/PageTitle'
import { EmptyState } from '../components/ui/EmptyState'
import { ErrorState } from '../components/ui/ErrorState'
import { LoadingState } from '../components/ui/LoadingState'
import { cx } from '../components/ui/cx'
import { COMPETITION_FORMAT_LABEL, competitionTabs, tabOf, type CompetitionView } from '../domain/competitions'
import type { CompetitionDetail } from '../domain/types'
import {
  BracketTab,
  GroupsAndBracketTab,
  MatchesTab,
  ScorersTab,
  TableTab,
  type CompetitionTabProps,
} from './competition/tabs'

/**
 * Uma competição do mundo (issue #133): `/competitions/:id/:tab?`. As abas
 * saem do FORMATO que o backend manda (`competitionTabs`, `switch` exaustivo):
 * pontos corridos abre na Tabela, mata-mata na Chave, grupos na visão de
 * grupos e chave. Sem aba na URL (ou aba de outro formato), abre a padrão.
 */
export function CompetitionPage() {
  const { id = '', tab } = useParams()
  const myClubId = useMyClubId()
  const { data, isPending, isLoadingError, error, refetch } = useCompetition(id)

  if (isPending) return <><PageTitle>Competição</PageTitle><LoadingState label="Carregando competição…" shape="cards" /></>
  if (isLoadingError) {
    if (error instanceof ApiError && error.status === 404) {
      return (
        <section className="space-y-4">
          <PageTitle>Competição</PageTitle>
          <h1 className="text-3xl font-bold text-slate-100">Competição</h1>
          <EmptyState
            message="Esta competição não existe no seu mundo."
            action={<Link to="/world" className="text-emerald-300 hover:underline">Ver as competições do mundo</Link>}
          />
        </section>
      )
    }
    return <><PageTitle>Competição</PageTitle><ErrorState error={error} onRetry={refetch} /></>
  }

  const active = tabOf(data.format, tab)
  return (
    <section className="space-y-6">
      <PageTitle>{data.name}</PageTitle>
      <header className="space-y-1">
        <p className="text-sm">
          <Link to="/world" className="text-slate-400 hover:text-emerald-300 hover:underline">Mundo</Link>
          <span aria-hidden="true" className="text-text-muted"> / </span>
          <span className="text-slate-300">{data.country ? <CountryFlag country={data.country} withName /> : data.confederation}</span>
        </p>
        <h1 className="text-3xl font-bold text-slate-100">{data.name}</h1>
        <p className="text-sm text-slate-400">
          {COMPETITION_FORMAT_LABEL[data.format]}
          {data.seasonLabel && <> · Temporada {data.seasonLabel}</>}
          {' · '}{data.clubCount} clubes
        </p>
      </header>

      {/* Mesmo padrão das abas do Clube: links (URL compartilhável), não `role=tablist`. */}
      <nav aria-label="Seções da competição" className="flex gap-1 overflow-x-auto overflow-y-hidden border-b border-slate-800">
        {competitionTabs(data.format).map((t) => (
          <Link
            key={t.slug}
            to={`/competitions/${encodeURIComponent(data.id)}/${t.slug}`}
            aria-current={t.slug === active.slug ? 'page' : undefined}
            className={cx(
              'whitespace-nowrap border-b-2 px-3 py-2 text-sm font-medium transition-colors',
              'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400',
              t.slug === active.slug
                ? 'border-emerald-500 text-slate-100'
                : 'border-transparent text-slate-400 hover:text-slate-200',
            )}
          >
            {t.label}
          </Link>
        ))}
      </nav>

      <TabBody view={active.view} competition={data} myClubId={myClubId} />
    </section>
  )
}

/**
 * A aba aberta. `switch` exaustivo sobre a visão: `groups` é a visão de
 * grupos e chave da Libertadores (#132).
 */
function TabBody({ view, ...props }: { view: CompetitionView; competition: CompetitionDetail; myClubId: number }) {
  const tabProps: CompetitionTabProps = props
  switch (view) {
    case 'table':
      return <TableTab {...tabProps} />
    case 'bracket':
      return <BracketTab {...tabProps} />
    case 'groups':
      return <GroupsAndBracketTab {...tabProps} />
    case 'matches':
      return <MatchesTab {...tabProps} />
    case 'scorers':
      return <ScorersTab {...tabProps} />
  }
}
