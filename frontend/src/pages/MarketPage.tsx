import { useMarket } from '../api/queries/useMarket'
import { formatMoney } from '../domain/formatters'

export function MarketPage() {
  const { data, isLoading, error } = useMarket()

  if (isLoading) return <p className="text-slate-400">Carregando mercado…</p>
  if (error)     return <p className="text-red-400">Erro: {String(error)}</p>
  if (!data)     return null

  return (
    <section>
      <h1 className="text-3xl font-bold text-slate-100 mb-4">Mercado</h1>

      <ul className="space-y-2">
        {data.map((entry) => (
          <li
            key={entry.player.id}
            className="flex items-center justify-between rounded-lg border border-slate-800 bg-slate-900/60 px-4 py-3"
          >
            <div>
              <div className="text-slate-100 font-medium">{entry.player.name}</div>
              <div className="text-xs text-slate-500">
                {entry.player.position} · {entry.player.age}a · OVR {entry.player.overall}
              </div>
            </div>
            <div className="text-right">
              <div className="text-slate-100 font-mono">{formatMoney(entry.price)}</div>
              <div className="text-xs text-slate-500">
                salário {formatMoney(entry.player.salary)}
              </div>
            </div>
          </li>
        ))}
      </ul>
    </section>
  )
}
