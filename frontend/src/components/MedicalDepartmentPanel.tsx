import { ApiError } from '../api/client'
import { errorMessage } from '../api/errorMessages'
import { MEDICAL_COST_CENTS } from '../api/queries/useTreatSquad'
import { formatMoney } from '../domain/formatters'
import { isInjured, staminaLevel } from '../domain/players'
import type { Player } from '../domain/types'
import { Card } from './ui/Card'

interface Props {
  squad: Player[]
  cash: number
  onTreat: () => void
  pending: boolean
  error: Error | null
  succeeded: boolean
}

/**
 * Departamento médico (issue #54): taxa fixa que devolve stamina e encurta
 * lesões. Custo e efeitos vêm do backend — o painel só dispara e reporta.
 */
export function MedicalDepartmentPanel({ squad, cash, onTreat, pending, error, succeeded }: Props) {
  const injuredCount = squad.filter(isInjured).length
  const tiredCount = squad.filter((p) => staminaLevel(p.stamina) !== 'fresh').length
  const affordable = cash >= MEDICAL_COST_CENTS

  return (
    <Card
      title="Departamento médico"
      actions={<span className="text-xs text-slate-400">{formatMoney(MEDICAL_COST_CENTS)} / sessão</span>}
      className="space-y-3"
    >
      <p className="text-sm text-slate-400">
        Recupera forma física do elenco e encurta as lesões em uma rodada.
        {' '}
        <span className="text-slate-300">
          {injuredCount} lesionado{injuredCount === 1 ? '' : 's'} · {tiredCount} desgastado
          {tiredCount === 1 ? '' : 's'}
        </span>
      </p>
      <button
        type="button"
        onClick={onTreat}
        disabled={!affordable || pending}
        className="rounded-md bg-sky-700 hover:bg-sky-800 disabled:bg-slate-700 disabled:cursor-not-allowed text-white px-4 py-2 text-sm font-medium transition-colors"
      >
        {pending ? 'Tratando…' : 'Tratar elenco'}
      </button>
      {!affordable && (
        <p className="text-xs text-amber-300">
          Caixa insuficiente — necessário {formatMoney(MEDICAL_COST_CENTS)}.
        </p>
      )}
      {error && (
        <p className="text-sm text-red-400">
          {error instanceof ApiError && error.status === 402
            ? `Caixa insuficiente — custo ${formatMoney(MEDICAL_COST_CENTS)}, disponível ${formatMoney(cash)}.`
            : errorMessage(error)}
        </p>
      )}
      {succeeded && <p className="text-sm text-emerald-400">Elenco tratado ✓</p>}
    </Card>
  )
}
