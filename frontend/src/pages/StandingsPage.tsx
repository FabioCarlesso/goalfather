import { useStandings } from '../api/queries/useStandings'

export function StandingsPage() {
  const { data, isLoading, error } = useStandings()

  if (isLoading) return <p className="text-slate-400">Carregando tabela…</p>
  if (error)     return <p className="text-red-400">Erro: {String(error)}</p>
  if (!data)     return null

  return (
    <section>
      <header className="mb-4">
        <h1 className="text-3xl font-bold text-slate-100">Tabela</h1>
        <p className="text-sm text-slate-400">
          Temporada {data.season} · Rodada {data.round}
        </p>
      </header>

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
          {data.rows.map((r) => (
            <tr key={r.clubId} className="hover:bg-slate-900/40">
              <td className="py-2 pr-2 text-slate-500">{r.position}</td>
              <td className="py-2 pr-2 text-slate-100">{r.clubName}</td>
              <td className="py-2 text-center text-slate-300">{r.played}</td>
              <td className="py-2 text-center text-slate-300">{r.wins}</td>
              <td className="py-2 text-center text-slate-300">{r.draws}</td>
              <td className="py-2 text-center text-slate-300">{r.losses}</td>
              <td className="py-2 text-center text-slate-300">{r.goalDifference}</td>
              <td className="py-2 text-center font-semibold text-slate-100">{r.points}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  )
}
