import { useCup } from '../api/queries/useCup'
import { useMyClubId } from '../auth/useMyClubId'
import { KNOCKOUT_PHASE_LABEL } from '../domain/cup'
import { formatMoney } from '../domain/formatters'
import type { Cup, CupClub, CupStage, CupTie, KnockoutPhase } from '../domain/types'

/**
 * Chave da copa mata-mata (issue #61). Uma coluna por fase, da abertura à
 * final; fases ainda não sorteadas aparecem como vagas "a definir", porque o
 * contrato traz a sequência inteira em `phases`.
 *
 * Nada aqui decide resultado: quem passou vem pronto em `winnerId` (pênaltis
 * incluídos) e o prêmio de cada fase em `prizeCents`. A página só desenha.
 */
export function CupPage() {
  const myClubId = useMyClubId()
  const { data: cup, isLoading, error } = useCup()

  if (isLoading) return <p className="text-slate-400">Carregando copa…</p>
  if (error)     return <p className="text-red-400">Erro: {String(error)}</p>

  if (!cup) {
    return (
      <section className="space-y-4">
        <h1 className="text-3xl font-bold text-slate-100">Copa</h1>
        <p className="rounded-lg border border-slate-800 bg-slate-900/40 p-6 text-slate-400">
          Nenhuma copa nesta temporada. A copa é sorteada na virada de temporada.
        </p>
      </section>
    )
  }

  const stageOf = new Map(cup.stages.map((s) => [s.phase, s]))

  return (
    <section className="space-y-6">
      <header>
        <h1 className="text-3xl font-bold text-slate-100">Copa {cup.season}</h1>
        <p className="text-sm text-slate-400">
          Mata-mata em jogo único — empate decide nos pênaltis.
          {cup.currentPhase && <> Em disputa: <strong className="text-slate-200">{KNOCKOUT_PHASE_LABEL[cup.currentPhase]}</strong>.</>}
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
          {cup.phases.map(({ phase, prizeCents }) => (
            <PhaseColumn
              key={phase}
              phase={phase}
              prizeCents={prizeCents}
              stage={stageOf.get(phase)}
              current={cup.currentPhase === phase}
              myClubId={myClubId}
              slots={slotsOf(cup, phase)}
            />
          ))}
        </div>
      </div>
    </section>
  )
}

/** Nº de confrontos de uma fase: metade da fase anterior, a partir da abertura. */
function slotsOf(cup: Cup, phase: KnockoutPhase): number {
  const opening = cup.stages[0]!.ties.length
  const depth = cup.phases.findIndex((p) => p.phase === phase)
  return Math.max(1, opening / 2 ** depth)
}

/**
 * Onde o técnico está na copa — lido de `winnerId`, que o backend decide.
 * Procura o último confronto do clube: se já foi decidido contra ele, caiu.
 */
function MyCupStatus({ cup, myClubId }: { cup: Cup; myClubId: number }) {
  const mine = [...cup.stages].reverse()
    .map((stage) => ({ stage, tie: stage.ties.find((t) => clubsOf(t).some((c) => c.id === myClubId)) }))
    .find((x) => x.tie != null)
  if (!mine?.tie) return null

  let label: string
  if (cup.champion?.id === myClubId) label = '🏆 Seu clube é o campeão!'
  else if (mine.tie.type === 'Fixture' && mine.tie.winnerId != null && mine.tie.winnerId !== myClubId) {
    label = `Seu clube foi eliminado — ${KNOCKOUT_PHASE_LABEL[mine.stage.phase]}.`
  } else label = `Seu clube segue vivo — ${KNOCKOUT_PHASE_LABEL[mine.stage.phase]}.`

  return <p className="mt-1 text-sm text-sky-300">{label}</p>
}

const clubsOf = (tie: CupTie): CupClub[] => {
  switch (tie.type) {
    case 'Bye': return [tie.club]
    case 'Fixture': return [tie.home, tie.away]
  }
}

function PhaseColumn({
  phase,
  prizeCents,
  stage,
  current,
  myClubId,
  slots,
}: {
  phase: KnockoutPhase
  prizeCents: number
  stage: CupStage | undefined
  current: boolean
  myClubId: number
  slots: number
}) {
  return (
    <div className="w-56 space-y-2" data-testid={`cup-phase-${phase}`}>
      <div>
        <h2 className={`text-sm font-semibold ${current ? 'text-emerald-300' : 'text-slate-200'}`}>
          {KNOCKOUT_PHASE_LABEL[phase]}
          {current && <span className="ml-2 text-xs font-normal text-emerald-400">em disputa</span>}
        </h2>
        <p className="text-xs text-slate-500">Prêmio por vitória: {formatMoney(prizeCents)}</p>
      </div>
      <div className="flex flex-col justify-around gap-2">
        {stage
          ? stage.ties.map((tie) => <TieCard key={tie.index} tie={tie} myClubId={myClubId} />)
          : Array.from({ length: slots }, (_, i) => (
              <div key={i} className="rounded-md border border-dashed border-slate-800 px-3 py-2 text-xs text-slate-600">
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

  switch (tie.type) {
    case 'Bye':
      return (
        <div className={`rounded-md border px-3 py-2 text-sm ${border}`}>
          <div className="font-medium text-slate-100">{tie.club.name}</div>
          <div className="text-xs text-slate-500">folga — avança direto</div>
        </div>
      )
    case 'Fixture': {
      const { result, winnerId } = tie
      return (
        <div className={`rounded-md border px-3 py-2 text-sm ${border}`}>
          <TeamLine
            club={tie.home}
            goals={result?.homeGoals}
            penalties={result?.penalties?.home}
            winner={winnerId === tie.home.id}
            decided={winnerId != null}
          />
          <TeamLine
            club={tie.away}
            goals={result?.awayGoals}
            penalties={result?.penalties?.away}
            winner={winnerId === tie.away.id}
            decided={winnerId != null}
          />
          {!result && <div className="mt-1 text-xs text-slate-500">a disputar</div>}
        </div>
      )
    }
  }
}

function TeamLine({
  club,
  goals,
  penalties,
  winner,
  decided,
}: {
  club: CupClub
  goals: number | undefined
  penalties: number | undefined
  winner: boolean
  decided: boolean
}) {
  const tone = !decided ? 'text-slate-200' : winner ? 'font-semibold text-slate-100' : 'text-slate-500 line-through'
  return (
    <div className="flex items-center justify-between gap-2">
      <span className={`truncate ${tone}`}>{club.name}</span>
      <span className="font-mono tabular-nums text-slate-300">
        {goals ?? ''}
        {penalties != null && <span className="ml-1 text-xs text-amber-300">({penalties})</span>}
      </span>
    </div>
  )
}
