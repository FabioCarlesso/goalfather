import { useClub } from '../api/queries/useClub'
import { formatMoney, formatSeats } from '../domain/formatters'

const MY_CLUB_ID = 1

export function DashboardPage() {
  const { data: club, isLoading, error } = useClub(MY_CLUB_ID)

  if (isLoading) return <p className="text-slate-400">Carregando clube…</p>
  if (error)     return <p className="text-red-400">Erro: {String(error)}</p>
  if (!club)     return null

  return (
    <section className="space-y-6">
      <header>
        <h1 className="text-3xl font-bold text-slate-100">{club.name}</h1>
        <p className="text-sm text-slate-400">ID #{club.id}</p>
      </header>

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <Stat label="Caixa"             value={formatMoney(club.cash)} />
        <Stat label="Capacidade estádio" value={formatSeats(club.stadiumCapacity)} />
        <Stat label="Elenco"             value={`${club.squad.length} jogadores`} />
      </div>

      <div>
        <h2 className="text-xl font-semibold text-slate-100 mb-3">Elenco</h2>
        <ul className="divide-y divide-slate-800 rounded-lg border border-slate-800">
          {club.squad.map((p) => (
            <li key={p.id} className="flex items-center justify-between px-4 py-2">
              <div>
                <span className="font-medium text-slate-100">{p.name}</span>
                <span className="ml-2 text-xs text-slate-500">{p.position} · {p.age}a</span>
              </div>
              <div className="flex items-center gap-3">
                {p.star && <span className="text-yellow-400">★</span>}
                <span className="text-slate-300 font-mono">{p.overall}</span>
              </div>
            </li>
          ))}
        </ul>
      </div>
    </section>
  )
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border border-slate-800 bg-slate-900/60 p-4">
      <div className="text-xs uppercase tracking-wide text-slate-500">{label}</div>
      <div className="text-lg font-semibold text-slate-100 mt-1">{value}</div>
    </div>
  )
}
