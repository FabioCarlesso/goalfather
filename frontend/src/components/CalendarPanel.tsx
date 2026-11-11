import { formatMatchday, matchCompetitionLabel } from '../domain/calendar'
import type { Calendar } from '../domain/types'
import { Card } from './ui/Card'

/**
 * Data corrente do jogo e próximo jogo do clube (issue #125). Só desenha o que
 * chega do `GET /api/league/calendar`: qual é o próximo encaixe do clube e a
 * que competição ele pertence é regra do calendário no backend.
 */
export function CalendarPanel({ calendar, clubId }: { calendar: Calendar; clubId: number }) {
  const next = calendar.nextMatch
  const opponent = next
    ? next.match.homeClubId === clubId ? next.match.awayClubName : next.match.homeClubName
    : null
  const venue = next && next.match.homeClubId === clubId ? 'em casa' : 'fora'

  return (
    <Card title="Calendário" className="space-y-2">
      <div className="text-sm">
        <div className="text-xs uppercase tracking-wide text-text-muted">Hoje</div>
        <div className="text-slate-100" data-testid="calendar-current">
          {calendar.current ? formatMatchday(calendar.current) : 'Sem jogos marcados'}
          {calendar.season != null && <span className="text-slate-400"> · Temporada {calendar.season}</span>}
        </div>
      </div>
      <div className="text-sm">
        <div className="text-xs uppercase tracking-wide text-text-muted">Próximo jogo</div>
        {next ? (
          <div data-testid="calendar-next-match">
            <div className="text-slate-100">
              {matchCompetitionLabel(next.match)}: contra {opponent} ({venue})
            </div>
            <div className="text-xs text-slate-400">{formatMatchday(next.matchday)}</div>
          </div>
        ) : (
          <div className="text-slate-400">Sem jogo marcado nesta temporada</div>
        )}
      </div>
    </Card>
  )
}
