import { CUP_LEGS_LABEL } from '../../domain/cup'
import { formatMoney } from '../../domain/formatters'
import type { Cup, CupClub, CupPhaseInfo, CupStage, CupTie } from '../../domain/types'

/**
 * Chave de um mata-mata no formato `Cup` do contrato (issue #132) — a da
 * Libertadores, e serve a qualquer competição com a mesma forma (a aba
 * "Chave" da página de competição, #133). Uma coluna por fase; fase ainda não
 * sorteada aparece como vagas "a definir". Quem passou vem pronto em
 * `winnerId` (agregado e pênaltis incluídos): o componente só desenha.
 *
 * [finalVenue]: sede neutra da final única, mostrada na coluna da final.
 */
export function KnockoutBracket({
  cup,
  myClubId,
  finalVenue,
  label = 'Chave do mata-mata',
}: {
  cup: Cup
  myClubId: number
  finalVenue?: string | undefined
  label?: string
}) {
  return (
    <div
      role="region"
      aria-label={label}
      // Região rolável precisa de foco para quem usa só o teclado (axe).
      // eslint-disable-next-line jsx-a11y/no-noninteractive-tabindex
      tabIndex={0}
      className="overflow-x-auto pb-2"
    >
      <div className="flex min-w-max gap-4">
        {cup.phases.map((phase, i) => (
          <PhaseColumn
            key={phase.name}
            phase={phase}
            // `stages` vem alinhado por índice com `phases` (contrato).
            stage={cup.stages[i]}
            current={cup.currentStage === phase.name}
            venue={i === cup.phases.length - 1 ? finalVenue : undefined}
            myClubId={myClubId}
          />
        ))}
      </div>
    </div>
  )
}

function PhaseColumn({
  phase,
  stage,
  current,
  venue,
  myClubId,
}: {
  phase: CupPhaseInfo
  stage: CupStage | undefined
  current: boolean
  venue: string | undefined
  myClubId: number
}) {
  return (
    <div className="w-56 space-y-2">
      <div>
        <h3 className={`text-sm font-semibold ${current ? 'text-emerald-300' : 'text-slate-200'}`}>
          {phase.name}
          {current && <span className="ml-2 text-xs font-normal text-emerald-400">em disputa</span>}
        </h3>
        <p className="text-xs text-text-muted">{phase.teams} clubes · {CUP_LEGS_LABEL[phase.legs]}</p>
        <p className="text-xs text-text-muted">Prêmio por vitória: {formatMoney(phase.prizeCents)}</p>
        {venue && <p className="text-xs text-amber-300">Sede: {venue}</p>}
      </div>
      <div className="flex flex-col gap-2">
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
  const mine = tie.home.id === myClubId || tie.away.id === myClubId
  const border = mine ? 'border-emerald-700/60 bg-emerald-900/10' : 'border-slate-800 bg-slate-900/40'
  const decided = tie.winnerId != null
  // `switch` exaustivo sobre o `oneOf` do contrato (espelho do `sealed CupTie`).
  switch (tie.type) {
    case 'Single':
      return (
        <div className={`rounded-md border px-3 py-2 text-sm ${border}`}>
          <Line club={tie.home} goals={tie.result?.homeGoals} penalties={tie.result?.penalties?.home} winnerId={tie.winnerId} decided={decided} />
          <Line club={tie.away} goals={tie.result?.awayGoals} penalties={tie.result?.penalties?.away} winnerId={tie.winnerId} decided={decided} />
        </div>
      )
    case 'TwoLegged':
      return (
        <div className={`rounded-md border px-3 py-2 text-sm ${border}`}>
          <Line club={tie.home} goals={tie.aggregate?.home} penalties={tie.penalties?.home} winnerId={tie.winnerId} decided={decided} />
          <Line club={tie.away} goals={tie.aggregate?.away} penalties={tie.penalties?.away} winnerId={tie.winnerId} decided={decided} />
          <div className="mt-1 text-xs text-text-muted">
            {!tie.firstLeg ? 'ida e volta a disputar' : !tie.secondLeg ? 'volta a disputar' : 'agregado'}
          </div>
        </div>
      )
  }
}

function Line({
  club,
  goals,
  penalties,
  winnerId,
  decided,
}: {
  club: CupClub
  goals: number | undefined
  penalties: number | undefined
  winnerId: number | undefined
  decided: boolean
}) {
  const tone = !decided ? 'text-slate-200' : winnerId === club.id ? 'font-semibold text-slate-100' : 'text-text-muted line-through'
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
