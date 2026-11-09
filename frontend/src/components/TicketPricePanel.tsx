import { useState } from 'react'
import { errorMessage } from '../api/errorMessages'
import { MIN_TICKET_PRICE_CENTS, MAX_TICKET_PRICE_CENTS } from '../api/queries/useSetTicketPrice'
import { formatMoney } from '../domain/formatters'
import { Button } from './ui/Button'
import { Card } from './ui/Card'

interface Props {
  /** Preço em vigor no clube, em centavos. */
  currentCents: number
  onSubmit: (cents: number) => void
  pending: boolean
  error: Error | null
  succeeded: boolean
}

/**
 * Preço do ingresso (issue #59). O painel só ESCOLHE o preço: quanta gente
 * aparece e quanto isso rende é curva de demanda do backend, reportada no
 * extrato da rodada. Fica ao lado da ampliação de propósito — as duas decisões
 * mexem na mesma conta (mais assentos × quanto cada assento rende).
 */
export function TicketPricePanel({ currentCents, onSubmit, pending, error, succeeded }: Props) {
  // Rascunho do input: estado local de UI, iniciado com o preço em vigor.
  const [reais, setReais] = useState(currentCents / 100)

  // Preço em reais inteiros: é o que o `step={1}` do input permite digitar e
  // percorrer no spinner. Validador e input precisam concordar — com step={5}
  // o browser marcava R$ 12 como inválido enquanto o botão seguia habilitado.
  const cents = reais * 100
  const valid = Number.isInteger(reais) &&
    cents >= MIN_TICKET_PRICE_CENTS &&
    cents <= MAX_TICKET_PRICE_CENTS
  const changed = cents !== currentCents

  return (
    <Card
      title="Preço do ingresso"
      actions={<span className="text-xs text-slate-400">em vigor: {formatMoney(currentCents)}</span>}
      className="space-y-3"
    >
      <p className="text-sm text-slate-400">
        Ingresso caro rende mais por torcedor e esvazia o estádio; barato lota
        mas rende pouco. Time melhor suporta preço maior — vale entre{' '}
        {formatMoney(MIN_TICKET_PRICE_CENTS)} e {formatMoney(MAX_TICKET_PRICE_CENTS)}.
      </p>
      <div className="flex flex-wrap items-end gap-3">
        <label className="text-sm text-slate-400">
          Preço (R$)
          <input
            type="number"
            min={MIN_TICKET_PRICE_CENTS / 100}
            max={MAX_TICKET_PRICE_CENTS / 100}
            step={1}
            value={reais}
            onChange={(e) => setReais(Number(e.target.value))}
            className="mt-1 block w-32 rounded-md border border-slate-700 bg-slate-900 px-2 py-1 text-sm text-slate-100"
          />
        </label>
        <Button onClick={() => valid && onSubmit(cents)} disabled={!valid || !changed || pending}>
          {pending ? 'Salvando…' : 'Definir preço'}
        </Button>
      </div>
      {!valid && (
        <p className="text-xs text-amber-300">
          Informe um valor entre {formatMoney(MIN_TICKET_PRICE_CENTS)} e{' '}
          {formatMoney(MAX_TICKET_PRICE_CENTS)}.
        </p>
      )}
      {error && <p className="text-sm text-red-400">{errorMessage(error)}</p>}
      {succeeded && !changed && <p className="text-sm text-emerald-400">Preço atualizado ✓</p>}
    </Card>
  )
}
