import { useCup } from '../api/queries/useCup'
import { PageTitle } from '../components/PageTitle'
import { EmptyState } from '../components/ui/EmptyState'
import { ErrorState } from '../components/ui/ErrorState'
import { LoadingState } from '../components/ui/LoadingState'
import { useMyClubId } from '../auth/useMyClubId'
import { CUP_LEGS_LABEL } from '../domain/cup'
import { formatMoney } from '../domain/formatters'
import type { Cup, CupClub, CupPhaseInfo, CupStage, CupTie } from '../domain/types'

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

      {cup.champion && (
        <div className="rounded-lg border border-amber-500/60 bg-gradient-to-r from-amber-900/30 to-amber-700/20 p-4 text-center">
          <div className="text-3xl">🏆</div>
          <div className="text-lg font-bold text-amber-100">{cup.champion.name} é campeão da copa!</div>
        </div>
      )}

      {/* Rola na horizontal no celular: a chave inteira não cabe em 360px. */}
      <div className="overflow-x-auto pb-2">
        <div className="flex min-w-max gap-4">
          {cup.phases.map((phase, i) => (
            <PhaseColumn
              key={phase.name}
              phase={phase}
              // `stages` vem alinhado por índice com `phases` (contrato).
              stage={cup.stages[i]}
              current={cup.currentStage === phase.name}
              myClubId={myClubId}
            />
          ))}
        </div>
      </div>
    </section>
  )
}

/** "Quartas de final" → "quartas-de-final": id estável para testes. */
const slug = (name: string): string =>
  name.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().replace(/\s+/g, '-')

const clubsOf = (tie: CupTie): CupClub[] => [tie.home, tie.away]

/**
 * Onde o técnico está na copa — lido de `winnerId` (decidido no backend) e dos
 * entrantes de cada fase. Procura o último confronto do clube: se já foi
 * decidido contra ele, caiu; se ainda não jogou, diz em que fase ele entra.
 */
function MyCupStatus({ cup, myClubId }: { cup: Cup; myClubId: number }) {
  const mine = [...cup.stages].reverse()
    .map((stage) => ({ stage, tie: stage.ties.find((t) => clubsOf(t).some((c) => c.id === myClubId)) }))
    .find((x) => x.tie != null)

  let label: string | null = null
  if (cup.champion?.id === myClubId) label = '🏆 Seu clube é o campeão!'
  else if (mine?.tie) {
    label = mine.tie.winnerId != null && mine.tie.winnerId !== myClubId
      ? `Seu clube foi eliminado — ${mine.stage.name}.`
      : `Seu clube segue vivo — ${mine.stage.name}.`
  } else {
    const entry = cup.phases.find((p) => p.entrants.some((c) => c.id === myClubId))
    if (entry) label = `Seu clube entra na copa em: ${entry.name}.`
  }
  return label ? <p className="mt-1 text-sm text-sky-300">{label}</p> : null
}

function PhaseColumn({
  phase,
  stage,
  current,
  myClubId,
}: {
  phase: CupPhaseInfo
  stage: CupStage | undefined
  current: boolean
  myClubId: number
}) {
  return (
    <div className="w-60 space-y-2" data-testid={`cup-phase-${slug(phase.name)}`}>
      <div>
        <h2 className={`text-sm font-semibold ${current ? 'text-emerald-300' : 'text-slate-200'}`}>
          {phase.name}
          {current && <span className="ml-2 text-xs font-normal text-emerald-400">em disputa</span>}
        </h2>
        <p className="text-xs text-text-muted">
          {phase.teams} clubes · {CUP_LEGS_LABEL[phase.legs]}
        </p>
        <p className="text-xs text-text-muted">Prêmio por vitória: {formatMoney(phase.prizeCents)}</p>
        {phase.entrants.length > 0 && (
          <p className="text-xs text-text-muted" title={phase.entrants.map((c) => c.name).join(', ')}>
            Entram nesta fase: {phase.entrants.length} clubes
          </p>
        )}
      </div>
      <div className="flex flex-col justify-around gap-2">
        {stage
          ? stage.ties.map((tie) => <TieCard key={tie.index} tie={tie} myClubId={myClubId} />)
          : Array.from({ length: phase.teams / 2 }, (_, i) => (
              <div key={i} className="rounded-md border border-dashed border-slate-800 px-3 py-2 text-xs text-text-muted">
                A definir
              </div>
            ))}
      </div>
    </div>
  )
}

function TieCard({ tie, myClubId }: { tie: CupTie; myClubId: number }) {
  const mine = clubsOf(tie).some((c) => c.id === myClubId)
  const border = mine ? 'border-emerald-700/60 bg-emerald-900/10' : 'border-slate-800 bg-slate-900/40'
  const decided = tie.winnerId != null

  // `switch` exaustivo sobre o `oneOf` do contrato (espelho do `sealed CupTie`).
  switch (tie.type) {
    case 'Single': {
      const { result } = tie
      return (
        <div className={`rounded-md border px-3 py-2 text-sm ${border}`}>
          <TeamLine club={tie.home} scores={[result?.homeGoals]} penalties={result?.penalties?.home}
            winner={tie.winnerId === tie.home.id} decided={decided} />
          <TeamLine club={tie.away} scores={[result?.awayGoals]} penalties={result?.penalties?.away}
            winner={tie.winnerId === tie.away.id} decided={decided} />
          {!result && <div className="mt-1 text-xs text-text-muted">a disputar</div>}
        </div>
      )
    }
    case 'TwoLegged': {
      // Na volta o mando inverte: os gols do `home` do confronto estão no
      // `awayGoals` da volta — só leitura de orientação; a soma vem pronta.
      const { firstLeg, secondLeg, aggregate, penalties } = tie
      return (
        <div className={`rounded-md border px-3 py-2 text-sm ${border}`}>
          <TeamLine club={tie.home} scores={[firstLeg?.homeGoals, secondLeg?.awayGoals]} aggregate={aggregate?.home}
            penalties={penalties?.home} winner={tie.winnerId === tie.home.id} decided={decided} />
          <TeamLine club={tie.away} scores={[firstLeg?.awayGoals, secondLeg?.homeGoals]} aggregate={aggregate?.away}
            penalties={penalties?.away} winner={tie.winnerId === tie.away.id} decided={decided} />
          <div className="mt-1 text-xs text-text-muted">
            {!firstLeg ? 'ida e volta a disputar' : !secondLeg ? 'volta a disputar' : 'ida · volta · agregado'}
          </div>
        </div>
      )
    }
  }
}

function TeamLine({
  club,
  scores,
  aggregate,
  penalties,
  winner,
  decided,
}: {
  club: CupClub
  scores: Array<number | undefined>
  aggregate?: number | undefined
  penalties: number | undefined
  winner: boolean
  decided: boolean
}) {
  const tone = !decided ? 'text-slate-200' : winner ? 'font-semibold text-slate-100' : 'text-text-muted line-through'
  return (
    <div className="flex items-center justify-between gap-2">
      <span className={`truncate ${tone}`}>{club.name}</span>
      <span className="flex gap-2 font-mono tabular-nums text-slate-300">
        {scores.map((goals, i) => <span key={i}>{goals ?? ''}</span>)}
        {aggregate != null && <span className="font-semibold text-slate-100">{aggregate}</span>}
        {penalties != null && <span className="text-xs text-amber-300">({penalties})</span>}
      </span>
    </div>
  )
}
