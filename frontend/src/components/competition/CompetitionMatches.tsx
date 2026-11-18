import { useState } from 'react'
import { Link } from 'react-router-dom'
import { formatMatchday, roundTitle } from '../../domain/calendar'
import { openMatchdayIndex } from '../../domain/competitions'
import type { CompetitionMatchday, RoundMatch } from '../../domain/types'
import { Button } from '../ui/Button'
import { EmptyState } from '../ui/EmptyState'

/**
 * Jogos de uma competição, um encaixe por vez (issue #133). Abre no primeiro
 * encaixe ainda em aberto; setas e seletor andam pelos demais. O índice
 * escolhido é estado local de UI — os jogos chegam por props.
 */
export function CompetitionMatches({ days, myClubId }: { days: CompetitionMatchday[]; myClubId: number }) {
  const [picked, setPicked] = useState<number | null>(null)
  if (days.length === 0) {
    return <EmptyState message="Nenhum jogo marcado nesta temporada." />
  }
  const index = Math.min(picked ?? openMatchdayIndex(days), days.length - 1)
  const day = days[index]!

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end gap-2">
        <Button
          variant="secondary"
          size="sm"
          aria-label="Encaixe anterior"
          disabled={index === 0}
          onClick={() => setPicked(index - 1)}
        >
          ‹
        </Button>
        <label className="flex min-w-0 flex-1 flex-col gap-1 text-sm text-slate-400 sm:flex-none">
          Encaixe
          <select
            value={index}
            onChange={(e) => setPicked(Number(e.target.value))}
            className="w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-slate-100 sm:w-auto"
          >
            {days.map((d, i) => (
              <option key={d.matchday.key} value={i}>
                {labelOf(d)} · {formatMatchday(d.matchday)}
              </option>
            ))}
          </select>
        </label>
        <Button
          variant="secondary"
          size="sm"
          aria-label="Próximo encaixe"
          disabled={index === days.length - 1}
          onClick={() => setPicked(index + 1)}
        >
          ›
        </Button>
      </div>

      <section aria-labelledby="matchday-title" className="space-y-2">
        <h2 id="matchday-title" className="text-xl font-semibold text-slate-100">
          {labelOf(day)}
        </h2>
        <p className="text-sm text-slate-400">{formatMatchday(day.matchday)}</p>
        {day.matches.length === 0 ? (
          <EmptyState message="Confrontos a definir: a fase é sorteada quando a anterior termina." />
        ) : (
          <ul className="divide-y divide-slate-800 rounded-lg border border-slate-800" data-testid="competition-matches">
            {day.matches.map((m) => <MatchRow key={m.matchId} match={m} myClubId={myClubId} />)}
          </ul>
        )}
      </section>
    </div>
  )
}

/** "Rodada 3" / "Copa — Final"; encaixe de fase ainda não sorteada só tem a data. */
const labelOf = (day: CompetitionMatchday): string =>
  day.matches.length > 0 ? roundTitle(day) : 'Fase a sortear'

function MatchRow({ match, myClubId }: { match: RoundMatch; myClubId: number }) {
  const mine = match.homeClubId === myClubId || match.awayClubId === myClubId
  const played = match.status !== 'Scheduled'
  return (
    <li
      className={`grid grid-cols-[1fr_auto_1fr] items-center gap-2 px-3 py-2 text-sm ${mine ? 'bg-sky-900/30 font-semibold' : ''}`}
      aria-current={mine ? 'true' : undefined}
    >
      <Link to={`/clubs/${match.homeClubId}`} className="truncate text-right text-slate-100 hover:text-emerald-300 hover:underline">
        {match.homeClubName}
      </Link>
      <span className="min-w-16 text-center font-mono tabular-nums text-slate-200">
        {played ? `${match.homeGoals} × ${match.awayGoals}` : '×'}
        {match.penalties && (
          <span className="block text-xs text-amber-300">
            pên. {match.penalties.home}–{match.penalties.away}
          </span>
        )}
        {match.status === 'InProgress' && <span className="block text-xs text-emerald-300">ao vivo</span>}
      </span>
      <Link to={`/clubs/${match.awayClubId}`} className="truncate text-slate-100 hover:text-emerald-300 hover:underline">
        {match.awayClubName}
      </Link>
      {mine && <span className="sr-only">(jogo do seu clube)</span>}
    </li>
  )
}
