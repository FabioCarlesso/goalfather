import { useId } from 'react'
import { CONTRACT_YEARS_OPTIONS } from '../domain/contractOffers'
import { CURRENCY_SYMBOL } from '../domain/formatters'
import type { ContractOffer } from '../domain/types'

const INPUT =
  'mt-1 block w-full rounded-md border border-slate-700 bg-slate-900 px-2 py-1 text-sm text-slate-100'

interface Props {
  value: ContractOffer
  onChange: (offer: ContractOffer) => void
}

/**
 * Campos dos termos pessoais (issue #138): salário mensal, duração e cláusula
 * opcional. Controlado — quem guarda a oferta é o modal ou o formulário de
 * proposta. Os inputs falam em dólares inteiros; o contrato, em centavos.
 */
export function ContractTermsFields({ value, onChange }: Props) {
  const id = useId()
  const dollars = (cents: number | undefined) => (cents ? Math.round(cents / 100) : '')
  const toCents = (text: string) => Math.round(Number(text) * 100)

  return (
    <div className="grid grid-cols-2 gap-3">
      <label htmlFor={`${id}-salary`} className="text-sm text-slate-400">
        Salário mensal ({CURRENCY_SYMBOL})
        <input
          id={`${id}-salary`}
          type="number"
          min={0}
          step={10}
          value={dollars(value.salary)}
          onChange={(e) => onChange({ ...value, salary: toCents(e.target.value) })}
          className={INPUT}
        />
      </label>
      <label htmlFor={`${id}-years`} className="text-sm text-slate-400">
        Duração
        <select
          id={`${id}-years`}
          value={value.years}
          onChange={(e) => onChange({ ...value, years: Number(e.target.value) })}
          className={INPUT}
        >
          {CONTRACT_YEARS_OPTIONS.map((n) => (
            <option key={n} value={n}>
              {n} temporada{n === 1 ? '' : 's'}
            </option>
          ))}
        </select>
      </label>
      <label htmlFor={`${id}-clause`} className="col-span-2 text-sm text-slate-400">
        Multa rescisória ({CURRENCY_SYMBOL}, opcional)
        <input
          id={`${id}-clause`}
          type="number"
          min={0}
          step={1000}
          value={dollars(value.releaseClause)}
          onChange={(e) => {
            // Vazio ou zero = sem cláusula: o campo some do objeto (opcional no contrato).
            const cents = toCents(e.target.value)
            const next: ContractOffer = { ...value, releaseClause: cents }
            if (!(cents > 0)) delete next.releaseClause
            onChange(next)
          }}
          className={INPUT}
        />
      </label>
    </div>
  )
}
