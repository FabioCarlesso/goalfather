import { Link } from 'react-router-dom'
import { formatGameDate, matchCompetitionLabel, SLOT_LABEL } from '../domain/calendar'
import type { ClubSchedule, NextMatch, ScheduleWeek } from '../domain/types'
import { Badge } from './ui/Badge'

/**
 * Calendário do clube semana a semana (issue #133). Só desenha o que chega de
 * `GET /api/clubs/{id}/schedule`: em que semana cai cada jogo, de qual
 * competição ele é e quais semanas são data FIFA são regras do backend.
 */
export function ClubScheduleList({ schedule }: { schedule: ClubSchedule }) {
  return (
    <ol className="space-y-3" aria-label="Semanas do calendário">
      {schedule.weeks.map((week) => (
        <WeekItem
          key={`${week.year}-${week.week}`}
          week={week}
          clubId={schedule.clubId}
          current={schedule.current != null && week.year === schedule.current.year && week.week === schedule.current.week}
        />
      ))}
    </ol>
  )
}

function WeekItem({ week, clubId, current }: { week: ScheduleWeek; clubId: number; current: boolean }) {
  return (
    <li
      className={`rounded-lg border p-3 ${current ? 'border-emerald-700/60 bg-emerald-900/10' : 'border-slate-800 bg-slate-900/30'}`}
      aria-current={current ? 'date' : undefined}
      data-testid={`schedule-week-${week.year}-${week.week}`}
    >
      <div className="flex flex-wrap items-baseline gap-2">
        <h2 className="text-sm font-semibold text-slate-100">Semana {week.week}</h2>
        <span className="text-xs text-slate-400">
          {formatGameDate(week.start)} a {formatGameDate(week.end)}
        </span>
        {current && <Badge tone="success">semana atual</Badge>}
        {week.fifaWindow && <Badge tone="warning" title="Semana de jogos de seleções">Data FIFA</Badge>}
      </div>
      {week.matches.length === 0 ? (
        <p className="mt-1 text-sm text-text-muted">Sem jogo do clube.</p>
      ) : (
        <ul className="mt-2 space-y-1">
          {week.matches.map((m) => <MatchLine key={m.match.matchId} entry={m} clubId={clubId} />)}
        </ul>
      )}
    </li>
  )
}

function MatchLine({ entry, clubId }: { entry: NextMatch; clubId: number }) {
  const { match, matchday } = entry
  const home = match.homeClubId === clubId
  const opponentId = home ? match.awayClubId : match.homeClubId
  const opponent = home ? match.awayClubName : match.homeClubName
  const finished = match.status === 'Finished'
  return (
    <li className="flex flex-wrap items-baseline gap-x-2 text-sm">
      <span className="text-xs text-slate-400">
        {SLOT_LABEL[matchday.slot]} · {formatGameDate(matchday.date)}
      </span>
      <Link
        to={`/competitions/${encodeURIComponent(match.competitionId)}`}
        className="text-sky-300 hover:underline"
      >
        {matchCompetitionLabel(match)}
      </Link>
      <span className="text-slate-100">
        contra <Link to={`/clubs/${opponentId}`} className="hover:text-emerald-300 hover:underline">{opponent}</Link>{' '}
        ({home ? 'em casa' : 'fora'})
      </span>
      {finished && (
        <span className="font-mono tabular-nums text-slate-200">
          {match.homeGoals} × {match.awayGoals}
        </span>
      )}
    </li>
  )
}
