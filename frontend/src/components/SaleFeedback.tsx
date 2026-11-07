import { formatMoney } from '../domain/formatters'
import type { TransferResult } from '../domain/types'

interface Feedback {
  color: string
  title: string
  body: string
}

/** `switch` exaustivo sobre a união discriminada — o análogo do `when` sobre `sealed`. */
function feedbackOf(result: TransferResult): Feedback {
  switch (result.type) {
    case 'Success':
      return {
        color: 'border-emerald-700/50 bg-emerald-900/20 text-emerald-200',
        title: 'Venda concluída',
        body: `${result.player.name} saiu do clube. Caixa: ${formatMoney(result.club.cash)}.`,
      }
    case 'InsufficientFunds':
      return {
        color: 'border-red-700/50 bg-red-900/20 text-red-200',
        title: 'Caixa insuficiente',
        body: `Necessário ${formatMoney(result.required)}; disponível ${formatMoney(result.available)}.`,
      }
    case 'SquadFull':
      return {
        color: 'border-amber-700/50 bg-amber-900/20 text-amber-200',
        title: 'Elenco cheio',
        body: `Limite de ${result.maxSize} jogadores atingido.`,
      }
    case 'PlayerNotAvailable':
      return {
        color: 'border-slate-700/50 bg-slate-800/40 text-slate-200',
        title: 'Jogador indisponível',
        body: `Jogador #${result.playerId} não está no elenco.`,
      }
    case 'Conflict':
      return {
        color: 'border-amber-700/50 bg-amber-900/20 text-amber-200',
        title: 'Clube alterado ao mesmo tempo',
        body: 'Outra operação mexeu no clube agora há pouco; nada foi aplicado. Tente de novo.',
      }
  }
}

/** Resultado da última venda, acima do elenco, até o técnico dispensar. */
export function SaleFeedback({ result, onDismiss }: { result: TransferResult; onDismiss: () => void }) {
  const { color, title, body } = feedbackOf(result)

  return (
    <div role="status" className={`rounded-lg border ${color} px-4 py-3 flex items-start justify-between`}>
      <div>
        <div className="font-semibold">{title}</div>
        <div className="text-sm opacity-90">{body}</div>
      </div>
      <button
        type="button"
        onClick={onDismiss}
        aria-label="Dispensar aviso"
        className="text-slate-400 hover:text-slate-200 text-sm"
      >
        ×
      </button>
    </div>
  )
}
