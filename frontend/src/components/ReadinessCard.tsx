// Card de lobby da rodada compartilhada (issue #20). Componente puro: recebe
// o estado de prontidão por props e emite "estou pronto" via callback — nenhuma
// chamada de API aqui (segue a regra: só pages/hooks tocam dados).

interface ReadinessCardProps {
  readyCount: number
  totalCount: number
  pendingUsernames: string[]
  /** Se o usuário atual já sinalizou pronto. */
  amIReady: boolean
  /** Segundos até o auto-start do escape hatch (issue #45); null = cronômetro parado. */
  secondsRemaining?: number | null
  /** Timeout expirou: a rodada pode iniciar mesmo com pendentes (issue #45). */
  timedOut?: boolean
  /** Disparado ao clicar "Estou pronto". */
  onReady: () => void
  /** Requisição de "pronto" em andamento (desabilita o botão). */
  marking?: boolean
}

/** "1m 05s" / "42s" a partir de segundos — countdown legível do escape hatch. */
function formatCountdown(totalSeconds: number): string {
  const m = Math.floor(totalSeconds / 60)
  const s = totalSeconds % 60
  return m > 0 ? `${m}m ${String(s).padStart(2, '0')}s` : `${s}s`
}

export function ReadinessCard({
  readyCount,
  totalCount,
  pendingUsernames,
  amIReady,
  secondsRemaining = null,
  timedOut = false,
  onReady,
  marking = false,
}: ReadinessCardProps) {
  const allReady = totalCount > 0 && readyCount >= totalCount

  return (
    <div className="rounded-lg border border-slate-800 bg-slate-900/40 p-3 flex items-center justify-between gap-3">
      <div className="space-y-0.5">
        <div className="flex items-center gap-2 text-sm font-medium text-slate-100">
          <span
            className={`inline-block w-2 h-2 rounded-full ${allReady ? 'bg-emerald-400' : 'bg-amber-400 animate-pulse'}`}
          />
          <span className="tabular-nums">{readyCount}/{totalCount}</span> técnicos prontos
        </div>
        {!allReady && pendingUsernames.length > 0 && (
          <p className="text-xs text-slate-400">Aguardando: {pendingUsernames.join(', ')}</p>
        )}
        {/* Escape hatch (issue #45): countdown enquanto corre; aviso ao estourar. */}
        {!allReady && timedOut && (
          <p className="text-xs text-emerald-400">
            Tempo esgotado — ausentes entram com a escalação atual. Pode jogar!
          </p>
        )}
        {!allReady && !timedOut && secondsRemaining != null && (
          <p className="text-xs text-amber-300 tabular-nums">
            Auto-início em {formatCountdown(secondsRemaining)} se ninguém mais sinalizar
          </p>
        )}
        {allReady && (
          <p className="text-xs text-emerald-400">Todos prontos — pode jogar a rodada!</p>
        )}
      </div>

      {amIReady ? (
        <span className="rounded-md bg-slate-800 text-slate-300 px-4 py-2 text-sm font-medium whitespace-nowrap">
          {allReady ? 'Pronto ✓' : 'Aguardando outros…'}
        </span>
      ) : (
        <button
          onClick={onReady}
          disabled={marking}
          className="rounded-md bg-emerald-600 hover:bg-emerald-500 disabled:bg-slate-700 disabled:cursor-not-allowed text-white px-4 py-2 text-sm font-medium transition-colors whitespace-nowrap"
        >
          {marking ? 'Enviando…' : 'Estou pronto'}
        </button>
      )}
    </div>
  )
}
