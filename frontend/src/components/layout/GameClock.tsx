import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { formatGameDate, formatMatchday, matchCompetitionLabel } from '../../domain/calendar'
import type { Calendar } from '../../domain/types'

/**
 * Faixa do relógio no cabeçalho (issue #133): a data do jogo (semana e
 * encaixe) e o próximo compromisso do clube, com atalho para o calendário.
 * Só desenha o `GET /api/league/calendar?clubId=` que o `AppLayout` lê.
 *
 * [notices] é o espaço de avisos curtos ao lado do relógio — o aviso de
 * convocação da issue #134 entra por aqui.
 */
export function GameClock({ calendar, clubId, notices }: { calendar: Calendar | undefined; clubId: number; notices?: ReactNode }) {
  if (!calendar?.current) return null
  const next = calendar.nextMatch
  const home = next?.match.homeClubId === clubId
  const opponent = next ? (home ? next.match.awayClubName : next.match.homeClubName) : null

  return (
    <div className="border-t border-slate-800/70 bg-slate-950/40">
      <div className="mx-auto flex max-w-5xl flex-wrap items-center gap-x-4 gap-y-1 px-4 py-1.5 text-xs text-slate-300 sm:px-6">
        <p data-testid="game-clock">
          <span className="text-text-muted">Hoje:</span> {formatMatchday(calendar.current)}
        </p>
        <p data-testid="game-clock-next">
          <span className="text-text-muted">Próximo jogo:</span>{' '}
          {next ? (
            <>
              {matchCompetitionLabel(next.match)} — contra {opponent} ({home ? 'em casa' : 'fora'}),{' '}
              {formatGameDate(next.matchday.date)}
            </>
          ) : (
            'sem jogo marcado'
          )}
        </p>
        {notices}
        <Link to="/calendar" className="ml-auto text-emerald-300 hover:underline">
          Ver calendário
        </Link>
      </div>
    </div>
  )
}
