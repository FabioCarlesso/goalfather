import { useStandings } from '../api/queries/useStandings'
import type { Standings } from '../domain/types'

export function StandingsPage() {
  const { data, isLoading, error } = useStandings()

  if (isLoading) return <p className="text-slate-400">Carregando tabela…</p>
  if (error)     return <p className="text-red-400">Erro: {String(error)}</p>
  if (!data || data.length === 0) return null

  const first = data[0]!

  return (
    <section className="space-y-8">
      <header>
        <h1 className="text-3xl font-bold text-slate-100">Tabela</h1>
        <p className="text-sm text-slate-400">
          Temporada {first.season} · Rodada {first.round}
        </p>
      </header>

      {data.map((table) => (
        <DivisionTable key={table.division} table={table} />
      ))}
    </section>
  )
}

/**
 * Tabela de uma divisão com as zonas de promoção (topo) e rebaixamento
 * (fundo) pintadas. Quantas posições compõem cada zona vem do CONTRATO
 * (`promotionSpots`/`relegationSpots`) — o frontend não recalcula a regra.
 */
function DivisionTable({ table }: { table: Standings }) {
  const zoneOf = (position: number): 'promotion' | 'relegation' | null => {
    if (position <= table.promotionSpots) return 'promotion'
    if (position > table.rows.length - table.relegationSpots) return 'relegation'
    return null
  }

  const zoneClasses = {
    promotion:  'border-l-2 border-emerald-500 bg-emerald-900/15',
    relegation: 'border-l-2 border-red-500 bg-red-900/15',
  } as const

  return (
    <div>
      <div className="mb-2 flex items-baseline justify-between">
        <h2 className="text-xl font-semibold text-slate-100">Divisão {table.division}</h2>
        <div className="flex gap-4 text-xs text-slate-400">
          {table.promotionSpots > 0 && (
            <span className="flex items-center gap-1.5">
              <span className="inline-block h-2.5 w-2.5 rounded-sm bg-emerald-500" />
              Promoção (top {table.promotionSpots})
            </span>
          )}
          {table.relegationSpots > 0 && (
            <span className="flex items-center gap-1.5">
              <span className="inline-block h-2.5 w-2.5 rounded-sm bg-red-500" />
              Rebaixamento (últimos {table.relegationSpots})
            </span>
          )}
        </div>
      </div>

      <table className="w-full text-left text-sm">
        <thead className="text-xs uppercase tracking-wide text-slate-500 border-b border-slate-800">
          <tr>
            <th className="py-2 pr-2">#</th>
            <th className="py-2 pr-2">Clube</th>
            <th className="py-2 text-center">J</th>
            <th className="py-2 text-center">V</th>
            <th className="py-2 text-center">E</th>
            <th className="py-2 text-center">D</th>
            <th className="py-2 text-center">SG</th>
            <th className="py-2 text-center font-semibold">Pts</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-800">
          {table.rows.map((r) => {
            const zone = zoneOf(r.position)
            return (
              <tr
                key={r.clubId}
                className={`hover:bg-slate-900/40 ${zone ? zoneClasses[zone] : ''}`}
                title={
                  zone === 'promotion'  ? 'Zona de promoção' :
                  zone === 'relegation' ? 'Zona de rebaixamento' : undefined
                }
              >
                <td className="py-2 pr-2 pl-2 text-slate-500">{r.position}</td>
                <td className="py-2 pr-2 text-slate-100">{r.clubName}</td>
                <td className="py-2 text-center text-slate-300">{r.played}</td>
                <td className="py-2 text-center text-slate-300">{r.wins}</td>
                <td className="py-2 text-center text-slate-300">{r.draws}</td>
                <td className="py-2 text-center text-slate-300">{r.losses}</td>
                <td className="py-2 text-center text-slate-300">{r.goalDifference}</td>
                <td className="py-2 text-center font-semibold text-slate-100">{r.points}</td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
