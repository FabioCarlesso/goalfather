import { useEffect, useId, useRef } from 'react'
import { Badge, type BadgeTone } from '../ui/Badge'
import { Button } from '../ui/Button'
import { stageTitle } from '../../domain/cup'
import type { RoundMatch, Standings } from '../../domain/types'
import type { LiveMatchState, RoundOutcome } from '../../api/queries/useRoundStream'
import { BoardReviewCard } from './BoardReviewCard'
import { ChampionBanner } from './ChampionBanner'
import { RetirementsCard } from './RetirementsCard'
import { StandingsFinanceCard } from './StandingsFinanceCard'
import { TrainingCard } from './TrainingCard'

export interface RoundSummaryProps {
  outcome: RoundOutcome
  myClubId: number
  /** Partida do técnico na data jogada (ausente se o clube folgou). */
  myMatch?: RoundMatch
  myMatchState?: LiveMatchState
  /** Prontidão da próxima rodada (issues #20/#45) — decidida pela página. */
  canStart: boolean
  onNextRound: () => void
  onShowDetails: () => void
}

/**
 * Painel "Resumo da rodada" (issue #112): o que aconteceu com o clube do
 * técnico, em ordem de importância — resultado, posição e caixa, treino e, na
 * virada, campeão, diretoria e aposentadorias. Aparece no topo da página e
 * recebe o foco ao surgir, para que quem estava rolando o feed o veja.
 */
export function RoundSummary({
  outcome,
  myClubId,
  myMatch,
  myMatchState,
  canStart,
  onNextRound,
  onShowDetails,
}: RoundSummaryProps) {
  const headingId = useId()
  const headingRef = useRef<HTMLHeadingElement>(null)
  // Foco no título ao surgir: rola o resumo para a vista e o leitor de tela
  // anuncia "Resumo da rodada" (o painel aparece sem ação do usuário).
  useEffect(() => headingRef.current?.focus(), [])

  const { played, season } = outcome
  const review = season?.boardReviews?.find((r) => r.clubId === myClubId)
  const fired = review?.verdict.type === 'Fired'

  return (
    <section
      aria-labelledby={headingId}
      data-testid="round-summary"
      className={`rounded-lg border p-4 space-y-3 bg-surface-raised/80 ${
        fired ? 'border-red-500' : 'border-emerald-700/60'
      }`}
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 id={headingId} ref={headingRef} tabIndex={-1} className="text-xl font-bold text-text focus:outline-none">
            Resumo da rodada
          </h2>
          <p className="text-sm text-text-muted">
            {stageTitle(played.stage)} · Temporada {played.season}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          {myMatch && (
            <Button variant="secondary" onClick={onShowDetails}>
              Ver detalhes da partida
            </Button>
          )}
          <Button
            onClick={onNextRound}
            disabled={!canStart}
            title={!canStart ? 'Aguardando técnicos sinalizarem prontos (ou o timeout expirar)' : undefined}
          >
            Próxima rodada
          </Button>
        </div>
      </div>
      {!canStart && (
        <p className="text-xs text-amber-300">
          Sinalize que está pronto no quadro de prontidão abaixo para liberar a próxima rodada.
        </p>
      )}

      {/* Demissão não espera a vez (issue #112): sobe para o topo do resumo. */}
      {fired && <BoardReviewCard review={review} />}

      <div className="grid grid-cols-1 gap-3 lg:grid-cols-3">
        {myMatch ? (
          <MyMatchResult match={myMatch} state={myMatchState} myClubId={myClubId} />
        ) : (
          <div className="rounded-lg border border-slate-800 bg-slate-900/40 p-3 text-sm text-text-muted">
            Seu clube não jogou nesta data.
          </div>
        )}
        <StandingsFinanceCard
          table={myDivisionTable(outcome.standings, myClubId)}
          myClubId={myClubId}
          stage={played.stage}
          finance={outcome.finance}
        />
        {outcome.training && <TrainingCard report={outcome.training} />}
      </div>

      {season && (
        <div className="grid grid-cols-1 gap-3 lg:grid-cols-3">
          <ChampionBanner season={season.season} champion={season.champion} cupChampion={season.cupChampion} />
          {!fired && <BoardReviewCard review={review} />}
          <RetirementsCard retirements={season.retirements.filter((r) => r.clubId === myClubId)} />
        </div>
      )}
    </section>
  )
}

/** Tabela da divisão do usuário (fallback: elite). */
function myDivisionTable(tables: Standings[], myClubId: number): Standings {
  return tables.find((t) => t.rows.some((r) => r.clubId === myClubId)) ?? tables[0]!
}

type Verdict = { label: string; tone: BadgeTone }

/**
 * Rótulo do placar visto pelo técnico. Só lê o resultado que chegou no
 * `FullTime`/`PenaltyShootout` — quem passou na copa é decidido no servidor.
 */
function verdictOf(mine: number, theirs: number, penalties?: { mine: number; theirs: number }): Verdict {
  if (mine > theirs) return { label: 'Vitória', tone: 'success' }
  if (mine < theirs) return { label: 'Derrota', tone: 'danger' }
  if (penalties) {
    return penalties.mine > penalties.theirs
      ? { label: 'Vitória nos pênaltis', tone: 'success' }
      : { label: 'Derrota nos pênaltis', tone: 'danger' }
  }
  return { label: 'Empate', tone: 'neutral' }
}

function MyMatchResult({
  match,
  state,
  myClubId,
}: { match: RoundMatch; state: LiveMatchState | undefined; myClubId: number }) {
  const home = state?.homeGoals ?? match.homeGoals
  const away = state?.awayGoals ?? match.awayGoals
  const pens = state?.penalties ?? match.penalties
  const atHome = match.homeClubId === myClubId
  const verdict = atHome
    ? verdictOf(home, away, pens && { mine: pens.home, theirs: pens.away })
    : verdictOf(away, home, pens && { mine: pens.away, theirs: pens.home })
  const opponent = atHome ? match.awayClubName : match.homeClubName

  return (
    <div className="rounded-lg border border-slate-700 bg-slate-900/60 p-3">
      <div className="flex items-center justify-between gap-2">
        <h3 className="text-sm font-semibold text-slate-200">Sua partida</h3>
        <Badge tone={verdict.tone} className="text-xs">{verdict.label}</Badge>
      </div>
      <p className="mt-2 text-lg font-bold text-slate-100" data-testid="my-match-score">
        {match.homeClubName} <span className="font-mono tabular-nums">{home} × {away}</span> {match.awayClubName}
      </p>
      {pens && <p className="text-xs text-amber-300">pênaltis {pens.home} × {pens.away}</p>}
      <p className="mt-1 text-xs text-text-muted">
        {atHome ? 'Em casa' : 'Fora'} contra {opponent}
      </p>
    </div>
  )
}
