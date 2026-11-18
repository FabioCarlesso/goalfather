import { useLibertadores } from '../api/queries/useLibertadores'
import { useMyClubId } from '../auth/useMyClubId'
import { PageTitle } from '../components/PageTitle'
import { LibertadoresView } from '../components/libertadores/LibertadoresView'
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
      <h1 className="text-3xl font-bold text-slate-100">{data.competitionName} {data.season}</h1>
      <LibertadoresView edition={data} myClubId={myClubId} />
    </section>
  )
}
