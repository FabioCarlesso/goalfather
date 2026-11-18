import { Link } from 'react-router-dom'
import type { CompetitionScorer } from '../../domain/types'
import { EmptyState } from '../ui/EmptyState'

/**
 * Artilharia dos clubes da competição (issue #133), na ordem do backend. A
 * nota de rodapé diz o corte do MVP: os gols são da temporada, em todas as
 * competições.
 */
export function ScorersTable({ scorers, myClubId }: { scorers: CompetitionScorer[]; myClubId: number }) {
  if (scorers.length === 0) {
    return <EmptyState message="Ninguém marcou ainda nesta temporada." />
  }
  return (
    <div className="space-y-2">
      <table className="w-full text-left text-sm">
        <caption className="sr-only">Artilharia</caption>
        <thead className="border-b border-slate-800 text-xs uppercase tracking-wide text-text-muted">
          <tr>
            <th scope="col" className="py-2 pr-2"><abbr title="Posição" className="no-underline">#</abbr></th>
            <th scope="col" className="py-2 pr-2">Jogador</th>
            <th scope="col" className="py-2 pr-2">Clube</th>
            <th scope="col" className="py-2 text-center">Gols</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-800">
          {scorers.map((s, i) => {
            const mine = s.clubId === myClubId
            return (
              <tr key={s.playerId} className={mine ? 'bg-sky-900/40 font-semibold' : undefined}>
                <td className="py-2 pr-2 text-text-muted">{i + 1}</td>
                <td className="py-2 pr-2 text-slate-100">{s.playerName}</td>
                <td className="py-2 pr-2">
                  <Link to={`/clubs/${s.clubId}`} className="text-slate-300 hover:text-emerald-300 hover:underline">
                    {s.clubName}
                  </Link>
                  {mine && <span className="sr-only"> (seu clube)</span>}
                </td>
                <td className="py-2 text-center font-semibold text-slate-100">{s.goals}</td>
              </tr>
            )
          })}
        </tbody>
      </table>
      <p className="text-xs text-text-muted">Gols na temporada, somando todas as competições.</p>
    </div>
  )
}
