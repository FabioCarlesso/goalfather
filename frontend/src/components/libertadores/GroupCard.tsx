import type { ContinentalGroup, GroupFixture } from '../../domain/types'

/**
 * Um grupo da Libertadores (issue #132): a tabela, já ordenada pelo backend
 * com os classificados em `zone: QUALIFICATION`, e os jogos rodada a rodada.
 * Só desenha — nada de pontos, desempate ou classificação é calculado aqui.
 */
export function GroupCard({ group, myClubId }: { group: ContinentalGroup; myClubId: number }) {
  const rounds = [...new Set(group.fixtures.map((f) => f.round))]
  return (
    <section
      aria-label={group.name}
      data-testid={`libertadores-group-${group.name.toLowerCase().replace(/\s+/g, '-')}`}
      className="space-y-3 rounded-lg border border-slate-800 bg-slate-900/40 p-3"
    >
      <h3 className="text-sm font-semibold text-slate-100">{group.name}</h3>
      <table className="w-full text-sm">
        <thead>
          <tr className="text-xs text-text-muted">
            <th scope="col" className="w-6 text-left">#</th>
            <th scope="col" className="text-left">Clube</th>
            <th scope="col" title="Jogos">J</th>
            <th scope="col" title="Saldo de gols">SG</th>
            <th scope="col" title="Pontos">P</th>
          </tr>
        </thead>
        <tbody>
          {group.rows.map((row) => {
            const qualified = row.zone === 'QUALIFICATION'
            const mine = row.clubId === myClubId
            return (
              <tr
                key={row.clubId}
                className={`${qualified ? 'text-emerald-200' : 'text-slate-300'} ${mine ? 'font-semibold' : ''}`}
                data-zone={row.zone}
              >
                <td>{row.position}</td>
                <td className="truncate">
                  {qualified && <span aria-hidden="true" className="mr-1 inline-block h-2 w-2 rounded-full bg-emerald-500" />}
                  {row.clubName}
                </td>
                <td className="text-center tabular-nums">{row.played}</td>
                <td className="text-center tabular-nums">{row.goalDifference}</td>
                <td className="text-center font-semibold tabular-nums">{row.points}</td>
              </tr>
            )
          })}
        </tbody>
      </table>
      <details className="text-xs text-slate-300">
        <summary className="cursor-pointer text-text-muted">Jogos do grupo</summary>
        <ol className="mt-2 space-y-2">
          {rounds.map((round) => (
            <li key={round}>
              <div className="text-text-muted">Rodada {round}</div>
              {group.fixtures.filter((f) => f.round === round).map((f) => (
                <FixtureLine key={`${f.homeClubId}-${f.awayClubId}`} fixture={f} />
              ))}
            </li>
          ))}
        </ol>
      </details>
    </section>
  )
}

function FixtureLine({ fixture }: { fixture: GroupFixture }) {
  const played = fixture.homeGoals != null && fixture.awayGoals != null
  return (
    <div className="flex justify-between gap-2">
      <span className="truncate">{fixture.homeClubName}</span>
      <span className="font-mono tabular-nums">{played ? `${fixture.homeGoals} × ${fixture.awayGoals}` : '×'}</span>
      <span className="truncate text-right">{fixture.awayClubName}</span>
    </div>
  )
}
