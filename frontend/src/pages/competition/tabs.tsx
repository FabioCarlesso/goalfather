import {
  useCompetitionBracket,
  useCompetitionMatches,
  useCompetitionScorers,
  useCompetitionStandings,
} from '../../api/queries/useCompetitions'
import { useLibertadores } from '../../api/queries/useLibertadores'
import { CupBracket, MyCupStatus } from '../../components/CupBracket'
import { LeagueTable } from '../../components/LeagueTable'
import { LibertadoresView } from '../../components/libertadores/LibertadoresView'
import { CompetitionMatches } from '../../components/competition/CompetitionMatches'
import { ScorersTable } from '../../components/competition/ScorersTable'
import { EmptyState } from '../../components/ui/EmptyState'
import { ErrorState } from '../../components/ui/ErrorState'
import { LoadingState } from '../../components/ui/LoadingState'
import { TIEBREAKER_LABEL } from '../../domain/competitions'
import type { CompetitionDetail } from '../../domain/types'

// Abas de `/competitions/:id` (issue #133). Cada aba busca os PRÓPRIOS dados
// (só a aba aberta dispara a requisição) e passa por props aos componentes.

export interface CompetitionTabProps {
  competition: CompetitionDetail
  myClubId: number
}

/** Tabela com as faixas do backend + a ordem dos critérios de desempate. */
export function TableTab({ competition, myClubId }: CompetitionTabProps) {
  const { data, isPending, isLoadingError, error, refetch } = useCompetitionStandings(competition.id)
  if (isPending) return <LoadingState label="Carregando tabela…" shape="table" />
  if (isLoadingError) return <ErrorState error={error} onRetry={refetch} />
  if (!data) return <EmptyState message="A tabela aparece quando a temporada desta competição começar." />
  return (
    <div className="space-y-3">
      <LeagueTable table={data} myClubId={myClubId} />
      <p className="text-xs text-text-muted" data-testid="tiebreakers">
        Critérios de desempate, em ordem: {competition.tiebreakers.map((t) => TIEBREAKER_LABEL[t]).join(' › ')}.
      </p>
    </div>
  )
}

export function MatchesTab({ competition, myClubId }: CompetitionTabProps) {
  const { data, isPending, isLoadingError, error, refetch } = useCompetitionMatches(competition.id)
  if (isPending) return <LoadingState label="Carregando jogos…" shape="list" />
  if (isLoadingError) return <ErrorState error={error} onRetry={refetch} />
  return <CompetitionMatches days={data} myClubId={myClubId} />
}

export function ScorersTab({ competition, myClubId }: CompetitionTabProps) {
  const { data, isPending, isLoadingError, error, refetch } = useCompetitionScorers(competition.id)
  if (isPending) return <LoadingState label="Carregando artilharia…" shape="table" />
  if (isLoadingError) return <ErrorState error={error} onRetry={refetch} />
  return <ScorersTable scorers={data} myClubId={myClubId} />
}

/** Chave de mata-mata (Copa do Brasil; fase final da fase de liga suíça). */
export function BracketTab({ competition, myClubId }: CompetitionTabProps) {
  const { data, isPending, isLoadingError, error, refetch } = useCompetitionBracket(competition.id)
  if (isPending) return <LoadingState label="Carregando chave…" shape="cards" />
  if (isLoadingError) return <ErrorState error={error} onRetry={refetch} />
  if (!data) return <EmptyState message="A chave aparece quando a competição for sorteada." />
  return (
    <div className="space-y-6">
      {data.currentStage && (
        <p className="text-sm text-slate-400">
          Em disputa: <strong className="text-slate-200">{data.currentStage}</strong>.
        </p>
      )}
      <MyCupStatus cup={data} myClubId={myClubId} />
      <CupBracket cup={data} myClubId={myClubId} />
    </div>
  )
}

/**
 * Grupos + chave (`GROUPS_THEN_KNOCKOUT`): a Libertadores da issue #132, a
 * única competição do mundo nesse formato até a #145. Busca a edição pelo
 * `/api/libertadores` e mostra a mesma visão da página própria.
 */
export function GroupsAndBracketTab({ myClubId }: CompetitionTabProps) {
  const { data, isPending, isLoadingError, error, refetch } = useLibertadores()
  if (isPending) return <LoadingState label="Carregando grupos…" shape="cards" />
  if (isLoadingError) return <ErrorState error={error} onRetry={refetch} />
  if (!data) return <EmptyState message="Os grupos aparecem quando a edição for sorteada." />
  return <LibertadoresView edition={data} myClubId={myClubId} />
}
