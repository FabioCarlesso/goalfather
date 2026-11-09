import { TRAINED_ATTRIBUTE_LABEL, TRAINING_FOCUS_LABEL } from '../../domain/training'
import type { TrainingReport } from '../../domain/types'

/**
 * O que a semana de treino produziu (issue #58): quem evoluiu e quem saiu
 * machucado. O card aparece mesmo sem eventos — "treinei e ninguém evoluiu"
 * é a informação que fecha o ciclo da decisão tomada na Dashboard.
 */
export function TrainingCard({ report }: { report: TrainingReport }) {
  return (
    <div className="rounded-lg border border-sky-800/60 bg-sky-950/30 p-3">
      <h3 className="text-sm font-semibold text-sky-200">
        Treino da semana — {TRAINING_FOCUS_LABEL[report.focus]}
      </h3>
      {report.events.length === 0 ? (
        <p className="mt-2 text-sm text-slate-400">
          Semana sem novidades: ninguém evoluiu nem saiu machucado.
        </p>
      ) : (
        <ul className="mt-2 space-y-1 text-sm text-slate-300">
          {report.events.map((event, i) => (
            <li key={`${event.type}-${event.player.id}-${i}`}>
              {event.type === 'Improved' ? (
                <>
                  <span className="text-emerald-300">▲</span>{' '}
                  <span className="text-slate-100">{event.player.name}</span>
                  <span className="text-slate-400">
                    {' '}evoluiu {TRAINED_ATTRIBUTE_LABEL[event.attribute]} · OVR{' '}
                    {event.player.overall}
                  </span>
                </>
              ) : (
                <>
                  <span className="text-orange-300">🚑</span>{' '}
                  <span className="text-slate-100">{event.player.name}</span>
                  <span className="text-slate-400">
                    {' '}se machucou no treino · fora por {event.roundsOut} rodada
                    {event.roundsOut === 1 ? '' : 's'}
                  </span>
                </>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
