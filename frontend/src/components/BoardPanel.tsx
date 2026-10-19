import { formatMoney } from '../domain/formatters'
import { BOARD_MOOD_LABEL, BOARD_MOOD_TONE, BOARD_OBJECTIVE_LABEL, ordinal } from '../domain/board'
import type { BoardStatus } from '../domain/types'

/**
 * Painel da diretoria (issue #62). Só desenha o que chega no `BoardStatus`: a
 * meta, a posição, a confiança e o humor já vêm calculados pelo backend — o
 * marcador do limiar de demissão usa `firingConfidence`, não um número fixo.
 */
export function BoardPanel({ status }: { status: BoardStatus }) {
  const tone = BOARD_MOOD_TONE[status.mood]
  const onTarget = status.currentPosition <= status.targetPosition

  return (
    <div className="rounded-lg border border-slate-800 bg-slate-900/60 p-4 space-y-3">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold text-slate-100">Diretoria</h2>
        <span className={`text-xs font-semibold ${tone.text}`}>{BOARD_MOOD_LABEL[status.mood]}</span>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 text-sm">
        <div>
          <div className="text-xs uppercase tracking-wide text-slate-500">Meta {status.season}</div>
          <div className="text-slate-100">{BOARD_OBJECTIVE_LABEL[status.objective]}</div>
          <div className="text-xs text-slate-400">
            até o {ordinal(status.targetPosition)} de {status.divisionSize}
          </div>
        </div>
        <div>
          <div className="text-xs uppercase tracking-wide text-slate-500">Posição atual</div>
          <div className={onTarget ? 'text-emerald-300' : 'text-amber-300'}>
            {ordinal(status.currentPosition)}
          </div>
          <div className="text-xs text-slate-400">
            {status.matchday === 0 ? 'temporada ainda não começou' : `após ${status.matchday} rodada(s)`}
          </div>
        </div>
        <div>
          <div className="text-xs uppercase tracking-wide text-slate-500">Bônus pela meta</div>
          <div className="text-slate-100">{formatMoney(status.bonusCents)}</div>
        </div>
      </div>

      <div>
        <div className="flex justify-between text-xs text-slate-400 mb-1">
          <span>Confiança</span>
          <span className="font-mono">{status.confidence}%</span>
        </div>
        <div
          className="relative h-2 overflow-hidden rounded-full bg-slate-800"
          role="meter"
          aria-label="Confiança da diretoria"
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={status.confidence}
        >
          <span className={`block h-full ${tone.bar}`} style={{ width: `${status.confidence}%` }} />
          <span
            className="absolute top-0 h-full w-0.5 bg-red-400/80"
            style={{ left: `${status.firingConfidence}%` }}
            title={`Limiar de demissão: ${status.firingConfidence}%`}
          />
        </div>
        <p className="mt-2 text-xs text-slate-500">
          Fechar a temporada fora da meta com a confiança em {status.firingConfidence}% ou menos
          custa o cargo.
        </p>
      </div>
    </div>
  )
}
