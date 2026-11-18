import { useCup } from '../api/queries/useCup'
import { PageTitle } from '../components/PageTitle'
import { EmptyState } from '../components/ui/EmptyState'
import { ErrorState } from '../components/ui/ErrorState'
import { LoadingState } from '../components/ui/LoadingState'
import { useMyClubId } from '../auth/useMyClubId'
import { CupBracket, MyCupStatus } from '../components/CupBracket'

/**
 * Chave da copa (issues #61 e #131). Uma coluna por fase, da abertura à final;
 * fases ainda não sorteadas aparecem como vagas "a definir" com quantos clubes
 * ENTRAM nelas, porque o contrato traz o plano inteiro em `phases`.
 *
 * Nada aqui decide resultado: quem passou vem pronto em `winnerId` (agregado e
 * pênaltis incluídos), o agregado da ida e volta em `aggregate` e o prêmio de
 * cada fase em `prizeCents`. A página só desenha.
 */
export function CupPage() {
  const myClubId = useMyClubId()
  const { data: cup, isPending, isLoadingError, error, refetch } = useCup()

  if (isPending) return <><PageTitle>Copa</PageTitle><LoadingState label="Carregando copa…" shape="cards" /></>
  if (isLoadingError) return <><PageTitle>Copa</PageTitle><ErrorState error={error} onRetry={refetch} /></>

  if (!cup) {
    return (
      <section className="space-y-4">
        <PageTitle>Copa</PageTitle>
        <h1 className="text-3xl font-bold text-slate-100">Copa</h1>
        <EmptyState message="Nenhuma copa nesta temporada. A copa é sorteada na virada de temporada." />
      </section>
    )
  }

  return (
    <section className="space-y-6">
      <PageTitle>Copa</PageTitle>
      <header>
        <h1 className="text-3xl font-bold text-slate-100">{cup.competitionName} {cup.season}</h1>
        <p className="text-sm text-slate-400">
          Mata-mata em jogo único ou ida e volta — empate no jogo único, ou no agregado da volta, vai aos pênaltis.
          {cup.currentStage && <> Em disputa: <strong className="text-slate-200">{cup.currentStage}</strong>.</>}
        </p>
        <MyCupStatus cup={cup} myClubId={myClubId} />
      </header>

      <CupBracket cup={cup} myClubId={myClubId} />
    </section>
  )
}
