import { useState } from 'react'
import { useClub } from '../api/queries/useClub'
import { useSellPlayer } from '../api/queries/useSellPlayer'
import { formatMoney, formatSeats } from '../domain/formatters'
import type { TransferResult } from '../domain/types'

const MY_CLUB_ID = 1

export function DashboardPage() {
  const { data: club, isLoading, error } = useClub(MY_CLUB_ID)
  const sell = useSellPlayer()
  const [lastResult, setLastResult] = useState<TransferResult | null>(null)

  if (isLoading) return <p className="text-slate-400">Carregando clube…</p>
  if (error)     return <p className="text-red-400">Erro: {String(error)}</p>
  if (!club)     return null

  const onSell = (playerId: number, name: string) => {
    if (!confirm(`Vender ${name}?`)) return
    sell.mutate(
      { clubId: MY_CLUB_ID, playerId },
      { onSuccess: (result) => setLastResult(result) },
    )
  }

  return (
    <section className="space-y-6">
      <header>
        <h1 className="text-3xl font-bold text-slate-100">{club.name}</h1>
        <p className="text-sm text-slate-400">ID #{club.id}</p>
      </header>

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <Stat label="Caixa"              value={formatMoney(club.cash)} />
        <Stat label="Capacidade estádio" value={formatSeats(club.stadiumCapacity)} />
        <Stat label="Elenco"             value={`${club.squad.length} jogadores`} />
      </div>

      {lastResult && (
        <SaleFeedback result={lastResult} onDismiss={() => setLastResult(null)} />
      )}

      <div>
        <h2 className="text-xl font-semibold text-slate-100 mb-3">Elenco</h2>
        <ul className="divide-y divide-slate-800 rounded-lg border border-slate-800">
          {club.squad.map((p) => (
            <li key={p.id} className="flex items-center justify-between px-4 py-2">
              <div className="min-w-0">
                <span className="font-medium text-slate-100">{p.name}</span>
                <span className="ml-2 text-xs text-slate-500">{p.position} · {p.age}a</span>
              </div>
              <div className="flex items-center gap-3">
                {p.star && <span className="text-yellow-400" title="Estrela">★</span>}
                <span className="text-slate-300 font-mono w-8 text-right">{p.overall}</span>
                <button
                  onClick={() => onSell(p.id, p.name)}
                  disabled={sell.isPending}
                  className="rounded-md border border-slate-700 hover:border-red-600 hover:text-red-400 disabled:opacity-50 px-2 py-1 text-xs text-slate-400 transition-colors"
                >
                  {sell.isPending && sell.variables?.playerId === p.id ? '…' : 'Vender'}
                </button>
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

function SaleFeedback({
  result,
  onDismiss,
}: { result: TransferResult; onDismiss: () => void }) {
  let color: string
  let title: string
  let body: string
  switch (result.type) {
    case 'Success':
      color = 'border-emerald-700/50 bg-emerald-900/20 text-emerald-200'
      title = 'Venda concluída'
      body  = `${result.player.name} saiu do clube. Caixa: ${formatMoney(result.club.cash)}.`
      break
    case 'InsufficientFunds':
      color = 'border-red-700/50 bg-red-900/20 text-red-200'
      title = 'Caixa insuficiente'
      body  = `Necessário ${formatMoney(result.required)}; disponível ${formatMoney(result.available)}.`
      break
    case 'SquadFull':
      color = 'border-amber-700/50 bg-amber-900/20 text-amber-200'
      title = 'Elenco cheio'
      body  = `Limite de ${result.maxSize} jogadores atingido.`
      break
    case 'PlayerNotAvailable':
      color = 'border-slate-700/50 bg-slate-800/40 text-slate-200'
      title = 'Jogador indisponível'
      body  = `Jogador #${result.playerId} não está no elenco.`
      break
  }

  return (
    <div className={`rounded-lg border ${color} px-4 py-3 flex items-start justify-between`}>
      <div>
        <div className="font-semibold">{title}</div>
        <div className="text-sm opacity-90">{body}</div>
      </div>
      <button onClick={onDismiss} className="text-slate-400 hover:text-slate-200 text-sm">×</button>
    </div>
  )
}
