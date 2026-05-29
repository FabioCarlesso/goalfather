import { useState } from 'react'
import { useMarket } from '../api/queries/useMarket'
import { useBuyPlayer } from '../api/queries/useBuyPlayer'
import { formatMoney } from '../domain/formatters'
import type { TransferResult } from '../domain/types'

const MY_CLUB_ID = 1

export function MarketPage() {
  const { data, isLoading, error } = useMarket()
  const buy = useBuyPlayer()
  const [lastResult, setLastResult] = useState<TransferResult | null>(null)

  if (isLoading) return <p className="text-slate-400">Carregando mercado…</p>
  if (error)     return <p className="text-red-400">Erro: {String(error)}</p>
  if (!data)     return null

  const onBuy = (playerId: number) => {
    buy.mutate(
      { clubId: MY_CLUB_ID, playerId },
      { onSuccess: (result) => setLastResult(result) },
    )
  }

  return (
    <section>
      <h1 className="text-3xl font-bold text-slate-100 mb-4">Mercado</h1>

      {lastResult && (
        <div className="mb-4">
          <TransferFeedback result={lastResult} onDismiss={() => setLastResult(null)} />
        </div>
      )}

      {data.length === 0 ? (
        <p className="text-slate-500 italic">Nenhum jogador disponível no momento.</p>
      ) : (
        <ul className="space-y-2">
          {data.map((entry) => (
            <li
              key={entry.player.id}
              className="flex items-center justify-between rounded-lg border border-slate-800 bg-slate-900/60 px-4 py-3"
            >
              <div className="min-w-0">
                <div className="text-slate-100 font-medium truncate">{entry.player.name}</div>
                <div className="text-xs text-slate-500">
                  {entry.player.position} · {entry.player.age}a · OVR {entry.player.overall}
                </div>
              </div>
              <div className="text-right mr-4">
                <div className="text-slate-100 font-mono">{formatMoney(entry.price)}</div>
                <div className="text-xs text-slate-500">salário {formatMoney(entry.player.salary)}</div>
              </div>
              <button
                onClick={() => onBuy(entry.player.id)}
                disabled={buy.isPending}
                className="rounded-md bg-emerald-600 hover:bg-emerald-500 disabled:bg-slate-700 disabled:cursor-not-allowed text-white px-3 py-1.5 text-sm font-medium transition-colors"
              >
                {buy.isPending && buy.variables?.playerId === entry.player.id ? '…' : 'Comprar'}
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

// Switch exaustivo sobre o discriminated union TransferResult.
// Mesma forma do `when` exaustivo sobre `sealed interface TransferResult` no Kotlin.
function TransferFeedback({
  result,
  onDismiss,
}: { result: TransferResult; onDismiss: () => void }) {
  let color: string
  let title: string
  let body: string
  switch (result.type) {
    case 'Success':
      color = 'border-emerald-700/50 bg-emerald-900/20 text-emerald-200'
      title = 'Contratação concluída'
      body  = `${result.player.name} agora joga no ${result.club.name}.`
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
      body  = `Jogador #${result.playerId} não está mais à venda.`
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
