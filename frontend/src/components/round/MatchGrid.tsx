import { Link } from 'react-router-dom'
import { KNOCKOUT_PHASE_LABEL } from '../../domain/cup'
import type { RoundMatch } from '../../domain/types'
import type { LiveMatchState } from '../../api/queries/useRoundStream'

/**
 * Rótulo do grupo: a liga da divisão pelo nome (a rodada já está no título da
 * página) e a copa com a fase. `switch` exaustivo sobre o `stage` do contrato.
 */
function groupLabel(match: RoundMatch): string {
  switch (match.stage.type) {
    case 'League':
      return match.competitionName
    case 'Cup':
      return `${match.competitionName} · ${KNOCKOUT_PHASE_LABEL[match.stage.phase]}`
  }
}

/**
 * Pares [rótulo, partidas], um grupo por competição do encaixe (issue #125):
 * cada liga de divisão e a copa. A ordem é a de chegada — o backend lista as
 * ligas da elite para baixo e a copa depois.
 */
function matchGroupsOf(matches: RoundMatch[]): Array<[string, RoundMatch[]]> {
  const grouped = new Map<string, RoundMatch[]>()
  for (const m of matches) {
    const label = groupLabel(m)
    grouped.set(label, [...(grouped.get(label) ?? []), m])
  }
  return [...grouped.entries()]
}

/** Partidas do encaixe agrupadas por competição (issues #47, #61 e #125). */
export function MatchGrid({
  matches,
  states,
  myClubId,
}: {
  matches: RoundMatch[]
  states: Map<number, LiveMatchState>
  myClubId: number
}) {
  return (
    <>
      {matchGroupsOf(matches).map(([label, group]) => (
        <div key={label} className="space-y-2">
          <h2 className="text-sm uppercase tracking-wide text-text-muted">{label}</h2>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
            {group.map((m) => (
              <MatchCard
                key={m.matchId}
                match={m}
                state={states.get(m.matchId)}
                highlight={m.homeClubId === myClubId || m.awayClubId === myClubId}
              />
            ))}
          </div>
        </div>
      ))}
    </>
  )
}

function MatchCard({
  match,
  state,
  highlight,
}: { match: RoundMatch; state: LiveMatchState | undefined; highlight: boolean }) {
  const s = state ?? {
    homeGoals: match.homeGoals,
    awayGoals: match.awayGoals,
    minute: match.minute,
    status: match.status,
  }

  const minuteLabel = s.status === 'Finished' ? 'FIM' : s.status === 'InProgress' ? `${s.minute}'` : '—'
  const minuteColor = s.status === 'InProgress' ? 'text-emerald-400' : 'text-text-muted'

  return (
    <Link
      to={`/round/match/${match.matchId}`}
      className={`block rounded-lg border p-4 transition-colors hover:border-emerald-500/60 ${
        highlight
          ? 'border-emerald-700/50 bg-emerald-900/10'
          : 'border-slate-800 bg-slate-900/40'
      }`}
    >
      <div className="flex items-center justify-between mb-2">
        <span className={`text-xs font-mono tracking-wider ${minuteColor}`}>
          {s.status === 'InProgress' && <span className="inline-block w-1.5 h-1.5 rounded-full bg-emerald-400 mr-1.5 animate-pulse" />}
          {minuteLabel}
        </span>
        {highlight && <span className="text-xs text-emerald-400 font-semibold">SEU JOGO</span>}
      </div>
      {/* Grade fixa [nome] [gols] × [gols] [nome] (issue #85). `minmax(0,1fr)`
          em vez de `1fr`: o mínimo implícito de `1fr` é o conteúdo, então um
          nome longo empurrava o placar e o "×" quebrava linha. Com mínimo 0 o
          nome encolhe e o `truncate` aplica reticências; o `title` mostra o
          nome completo no hover. */}
      <div className="grid grid-cols-[minmax(0,1fr)_auto_auto_auto_minmax(0,1fr)] items-center gap-x-2">
        <div className="text-sm font-medium text-slate-100 truncate text-right" title={match.homeClubName}>
          {match.homeClubName}
        </div>
        <span className="text-2xl font-bold font-mono tabular-nums text-slate-100">{s.homeGoals}</span>
        <span className="text-2xl font-bold font-mono text-text-muted">×</span>
        <span className="text-2xl font-bold font-mono tabular-nums text-slate-100">{s.awayGoals}</span>
        <div className="text-sm font-medium text-slate-100 truncate text-left" title={match.awayClubName}>
          {match.awayClubName}
        </div>
        {s.penalties && (
          <div className="col-start-2 col-span-3 text-center text-xs text-amber-300 whitespace-nowrap">
            pên. {s.penalties.home} × {s.penalties.away}
          </div>
        )}
      </div>
    </Link>
  )
}
