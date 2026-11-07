import { errorMessage } from '../api/errorMessages'
import { TRAINING_FOCUSES, TRAINING_FOCUS_HINT, TRAINING_FOCUS_LABEL } from '../domain/training'
import type { TrainingFocus } from '../domain/types'
import { Card } from './ui/Card'

interface Props {
  /** Foco em destaque: o do clube, ou o que acabou de ser clicado (em voo). */
  selected: TrainingFocus
  onSelect: (focus: TrainingFocus) => void
  pending: boolean
  error: Error | null
}

/**
 * Foco de treino da semana (issue #58). O card só escolhe: o efeito acontece
 * na virada da rodada e é reportado na página da rodada.
 */
export function TrainingPanel({ selected, onSelect, pending, error }: Props) {
  return (
    <Card
      title="Treino da semana"
      actions={<span className="text-xs text-slate-400">aplicado na próxima rodada</span>}
      className="space-y-3"
    >
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
        {TRAINING_FOCUSES.map((focus) => (
          <button
            key={focus}
            type="button"
            onClick={() => onSelect(focus)}
            disabled={pending}
            aria-pressed={selected === focus}
            className={`rounded-md border px-3 py-2 text-sm font-medium transition-colors disabled:opacity-60 ${
              selected === focus
                ? 'border-emerald-500 bg-emerald-900/30 text-emerald-200'
                : 'border-slate-700 text-slate-300 hover:border-emerald-600/60'
            }`}
          >
            {TRAINING_FOCUS_LABEL[focus]}
          </button>
        ))}
      </div>
      <p className="text-sm text-slate-400">{TRAINING_FOCUS_HINT[selected]}</p>
      {error && <p className="text-sm text-red-400">{errorMessage(error)}</p>}
    </Card>
  )
}
