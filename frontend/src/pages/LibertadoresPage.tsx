import { useLibertadores } from '../api/queries/useLibertadores'
import { useMyClubId } from '../auth/useMyClubId'
import { PageTitle } from '../components/PageTitle'
import { GroupCard } from '../components/libertadores/GroupCard'
import { KnockoutBracket } from '../components/libertadores/KnockoutBracket'
import { EmptyState } from '../components/ui/EmptyState'
import { ErrorState } from '../components/ui/ErrorState'
import { LoadingState } from '../components/ui/LoadingState'

/**
 * Copa Libertadores (issue #132): os 8 grupos e a chave do mata-mata. A página
 * só lê o hook e repassa por props; quem decide classificação, confrontos e
 * campeão é o backend. Sem Libertadores no mundo (o recorte do E2E), um estado
 * vazio explica por quê.
 */
export function LibertadoresPage() {
  const myClubId = useMyClubId()
  const { data, isPending, isLoadingError, error, refetch } = useLibertadores()

  if (isPending) return <><PageTitle>Libertadores</PageTitle><LoadingState label="Carregando Libertadores…" shape="cards" /></>
  if (isLoadingError) return <><PageTitle>Libertadores</PageTitle><ErrorState error={error} onRetry={refetch} /></>

  if (!data) {
    return (
      <section className="space-y-4">
        <PageTitle>Libertadores</PageTitle>
        <h1 className="text-3xl font-bold text-slate-100">Libertadores</h1>
        <EmptyState message="Este mundo não tem Libertadores." />
      </section>
    )
  }

  return (
    <section className="space-y-6">
      <PageTitle>Libertadores</PageTitle>
      <header>
        <h1 className="text-3xl font-bold text-slate-100">{data.competitionName} {data.season}</h1>
        <p className="text-sm text-slate-400">
          8 grupos de 4, ida e volta: os {data.qualifiersPerGroup} primeiros vão às oitavas. Mata-mata em ida e volta e
          final em jogo único{data.finalVenue ? <> no <strong className="text-slate-200">{data.finalVenue.name}</strong></> : null}.
        </p>
      </header>

      {data.champion && (
        <div className="rounded-lg border border-amber-500/60 bg-gradient-to-r from-amber-900/30 to-amber-700/20 p-4 text-center">
          <div className="text-3xl" aria-hidden="true">🏆</div>
          <div className="text-lg font-bold text-amber-100">{data.champion.name} é campeão da Libertadores!</div>
        </div>
      )}

      <div className="space-y-2">
        <h2 className="text-lg font-semibold text-slate-100">Fase de grupos</h2>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
          {data.groups.map((group) => <GroupCard key={group.name} group={group} myClubId={myClubId} />)}
        </div>
      </div>

      <div className="space-y-2">
        <h2 className="text-lg font-semibold text-slate-100">Mata-mata</h2>
        {data.knockout
          ? <KnockoutBracket cup={data.knockout} myClubId={myClubId} finalVenue={data.finalVenue?.name} label="Chave da Libertadores" />
          : <EmptyState message="O mata-mata é sorteado quando a fase de grupos termina: 1º contra 2º de outro grupo." />}
      </div>
    </section>
  )
}
