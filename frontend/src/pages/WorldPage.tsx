import { Link } from 'react-router-dom'
import { useCompetitions } from '../api/queries/useCompetitions'
import { useStandings } from '../api/queries/useStandings'
import { useMyClubId } from '../auth/useMyClubId'
import { PageTitle } from '../components/PageTitle'
import { Badge } from '../components/ui/Badge'
import { Card } from '../components/ui/Card'
import { EmptyState } from '../components/ui/EmptyState'
import { ErrorState } from '../components/ui/ErrorState'
import { LoadingState } from '../components/ui/LoadingState'
import { COMPETITION_FORMAT_LABEL, groupByCountry, type CompetitionGroup } from '../domain/competitions'
import type { CompetitionSummary } from '../domain/types'

/**
 * O mundo do jogo (issue #133): país → competições. Cada competição leva à
 * `/competitions/:id`, com tabela, chave, jogos e artilharia. A liga do clube
 * do técnico ganha uma etiqueta: é a tabela em que o clube aparece (a mesma
 * leitura da página Tabela), não uma regra recalculada aqui.
 */
export function WorldPage() {
  const myClubId = useMyClubId()
  const { data, isPending, isLoadingError, error, refetch } = useCompetitions()
  // Secundária: sem as tabelas, a página só perde a etiqueta "sua liga".
  const { data: tables } = useStandings()
  const myLeagueId = tables?.find((t) => t.rows.some((r) => r.clubId === myClubId))?.competitionId

  if (isPending) return <><PageTitle>Mundo</PageTitle><LoadingState label="Carregando o mundo…" shape="cards" /></>
  if (isLoadingError) return <><PageTitle>Mundo</PageTitle><ErrorState error={error} onRetry={refetch} /></>

  return (
    <section className="space-y-6">
      <PageTitle>Mundo</PageTitle>
      <header>
        <h1 className="text-3xl font-bold text-slate-100">Mundo</h1>
        <p className="text-sm text-slate-400">Países e competições. Escolha uma para ver tabela, chave, jogos e artilharia.</p>
        {/* Seleções (issue #134): datas FIFA, Eliminatórias e ranking. */}
        <Link to="/national-teams" className="mt-1 inline-block text-sm text-emerald-300 hover:underline">
          Seleções e datas FIFA
        </Link>
      </header>
      {data.length === 0 ? (
        <EmptyState message="Nenhuma competição no mundo por enquanto." />
      ) : (
        <div className="grid gap-4 md:grid-cols-2">
          {groupByCountry(data).map((group) => (
            <CountryCard key={group.key} group={group} myLeagueId={myLeagueId} />
          ))}
        </div>
      )}
    </section>
  )
}

function CountryCard({ group, myLeagueId }: { group: CompetitionGroup; myLeagueId: string | undefined }) {
  const title = group.country ? (
    <><span aria-hidden="true">{group.country.flag}</span> {group.country.name}</>
  ) : (
    <>{group.confederation}</>
  )
  return (
    <Card as="section" title={title} className="space-y-3" data-testid={`world-country-${group.key}`}>
      <ul className="space-y-2">
        {group.competitions.map((c) => (
          <CompetitionItem key={c.id} competition={c} mine={c.id === myLeagueId} />
        ))}
      </ul>
    </Card>
  )
}

function CompetitionItem({ competition, mine }: { competition: CompetitionSummary; mine: boolean }) {
  return (
    <li>
      <Link
        to={`/competitions/${encodeURIComponent(competition.id)}`}
        className="block rounded-md border border-slate-800 px-3 py-2 hover:border-emerald-700 hover:bg-slate-900/60 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400"
      >
        <span className="flex flex-wrap items-center gap-2">
          <span className="font-medium text-slate-100">{competition.name}</span>
          {mine && <Badge tone="info">sua liga</Badge>}
        </span>
        <span className="block text-xs text-slate-400">
          {COMPETITION_FORMAT_LABEL[competition.format]}
          {competition.seasonLabel && <> · {competition.seasonLabel}</>}
          {' · '}{competition.clubCount} clubes
        </span>
      </Link>
    </li>
  )
}
