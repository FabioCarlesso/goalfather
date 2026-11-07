import { useState } from 'react'
import { ApiError } from '../api/client'
import { errorMessage } from '../api/errorMessages'
import { COST_PER_SEAT_CENTS, MIN_STADIUM_EXPANSION_SEATS } from '../api/queries/useExpandStadium'
import { formatMoney, formatSeats } from '../domain/formatters'
import { Button } from './ui/Button'
import { Card } from './ui/Card'

interface Props {
  /** Caixa atual, só para explicar o 402 (quem decide é o backend). */
  cash: number
  onExpand: (additionalSeats: number) => void
  pending: boolean
  error: Error | null
  succeeded: boolean
}

/** Painel de ampliação de estádio (issue #5). */
export function StadiumExpandPanel({ cash, onExpand, pending, error, succeeded }: Props) {
  const [seats, setSeats] = useState(MIN_STADIUM_EXPANSION_SEATS)

  const valid = Number.isInteger(seats) && seats >= MIN_STADIUM_EXPANSION_SEATS
  const cost = seats * COST_PER_SEAT_CENTS

  return (
    <Card
      title="Ampliar estádio"
      actions={<span className="text-xs text-slate-400">{formatMoney(COST_PER_SEAT_CENTS)} / assento</span>}
      className="space-y-3"
    >
      <div className="flex flex-wrap items-end gap-3">
        <label className="text-sm text-slate-400">
          Novos assentos
          <input
            type="number"
            min={MIN_STADIUM_EXPANSION_SEATS}
            step={MIN_STADIUM_EXPANSION_SEATS}
            value={seats}
            onChange={(e) => setSeats(Math.floor(Number(e.target.value)))}
            className="mt-1 block w-32 rounded-md border border-slate-700 bg-slate-900 px-2 py-1 text-sm text-slate-100"
          />
        </label>
        <div className="text-sm text-slate-400">
          Custo total: <span className="font-mono text-slate-200">{formatMoney(cost)}</span>
        </div>
        <Button onClick={() => valid && onExpand(seats)} disabled={!valid || pending}>
          {pending ? 'Ampliando…' : 'Ampliar'}
        </Button>
      </div>
      {!valid && (
        <p className="text-xs text-amber-300">
          Informe um número inteiro de pelo menos {formatSeats(MIN_STADIUM_EXPANSION_SEATS)} assentos.
        </p>
      )}
      {error && (
        <p className="text-sm text-red-400">
          {error instanceof ApiError && error.status === 402
            ? `Caixa insuficiente — custo ${formatMoney(cost)}, disponível ${formatMoney(cash)}.`
            : errorMessage(error)}
        </p>
      )}
      {succeeded && <p className="text-sm text-emerald-400">Capacidade ampliada ✓</p>}
    </Card>
  )
}
