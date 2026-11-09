import type { RoundStreamStatus } from '../../api/queries/useRoundStream'

/** Relógio agregado da rodada: barra de 0 a 90′ e rótulo do momento. */
export function RoundProgress({
  minute,
  status,
  allFinished,
}: { minute: number; status: RoundStreamStatus; allFinished: boolean }) {
  const pct = Math.min(100, (minute / 90) * 100)
  const remaining = Math.max(0, 90 - minute)
  const label =
    allFinished                ? 'Rodada encerrada — avaliando pontuações' :
    status === 'live' && remaining <= 10 ? `⏱ Faltam ${remaining}′` :
    status === 'live'          ? `Minuto ${minute}′` :
                                 'Aguardando início'

  return (
    <div className="rounded-lg border border-slate-800 bg-slate-900/40 p-3">
      <div className="flex items-center justify-between mb-2 text-xs font-mono">
        <span className={status === 'live' ? 'text-emerald-400' : 'text-slate-400'}>
          {status === 'live' && <span className="inline-block w-1.5 h-1.5 rounded-full bg-emerald-400 mr-1.5 animate-pulse" />}
          {label}
        </span>
        <span className="text-text-muted tabular-nums">{minute}/90′</span>
      </div>
      <div className="h-1.5 rounded-full bg-slate-800 overflow-hidden">
        <div
          className={`h-full transition-all duration-300 ${
            allFinished ? 'bg-slate-500' :
            remaining <= 10 && status === 'live' ? 'bg-amber-400' :
            'bg-emerald-500'
          }`}
          style={{ width: `${pct}%` }}
        />
      </div>
    </div>
  )
}
