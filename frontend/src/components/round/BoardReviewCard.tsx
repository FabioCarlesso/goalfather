import { useCallback, useEffect, useState } from 'react'
import { useAuth } from '../../auth/AuthContext'
import { formatMoney } from '../../domain/formatters'
import { BOARD_OBJECTIVE_LABEL, boardVerdictHeadline, ordinal } from '../../domain/board'
import type { BoardReview } from '../../domain/types'

const TONE = {
  TargetMet: 'border-emerald-700/50 bg-emerald-900/20 text-emerald-200',
  TargetMissed: 'border-amber-700/50 bg-amber-900/20 text-amber-200',
  // Demissão é o desfecho mais grave da temporada (issue #112): borda dupla e
  // fundo vermelho cheio para não se perder no meio do resumo.
  Fired: 'border-2 border-red-500 bg-red-950/70 text-red-100',
} as const

/**
 * Balanço da diretoria no fim da temporada (issue #62). Na demissão, o clube
 * já voltou para a IA no servidor: o card rebusca o usuário (`/me`) sozinho,
 * assim que o veredito chega — com o `AuthUser` antigo, qualquer outro caminho
 * mostraria um clube que já é da IA (e as escritas dariam 403). É a `dismissal`
 * nova que leva os guardas de rota à tela de game over; a página não navega
 * por conta própria. Se o `/me` falhar, o card avisa e oferece tentar de novo.
 */
export function BoardReviewCard({ review }: { review: BoardReview | undefined }) {
  const { refreshUser } = useAuth()
  const [refreshError, setRefreshError] = useState<string | null>(null)
  const fired = review?.verdict.type === 'Fired'

  // O erro só é gravado na volta da promessa (assíncrono), então o efeito
  // abaixo não chama setState de forma síncrona.
  const leaveClub = useCallback(
    () =>
      refreshUser().catch((e: unknown) =>
        setRefreshError(e instanceof Error ? e.message : 'Não foi possível atualizar sua conta.'),
      ),
    [refreshUser],
  )

  useEffect(() => {
    if (fired) void leaveClub()
  }, [fired, leaveClub])

  if (!review) return null

  return (
    <div className={`rounded-lg border p-3 ${TONE[review.verdict.type]}`} data-testid="board-review">
      <h3 className={fired ? 'text-base font-bold' : 'text-sm font-semibold'}>
        {fired && <span aria-hidden="true">⚠ </span>}
        Diretoria: {boardVerdictHeadline(review.verdict)}
      </h3>
      <p className="mt-1 text-sm opacity-90">
        Meta: {BOARD_OBJECTIVE_LABEL[review.objective]} (até o {ordinal(review.targetPosition)}) ·
        terminou em {ordinal(review.finalPosition)} · confiança {review.confidence}%
        {review.verdict.type === 'TargetMet' && <> · bônus de {formatMoney(review.verdict.bonusCents)}</>}
      </p>
      {fired && refreshError && (
        <div className="mt-3 space-y-2">
          <p role="alert" className="text-sm">{refreshError}</p>
          <button
            onClick={() => {
              setRefreshError(null)
              void leaveClub()
            }}
            className="rounded-md bg-red-600 hover:bg-red-700 text-white px-4 py-2 text-sm font-medium transition-colors"
          >
            Tentar de novo
          </button>
        </div>
      )}
    </div>
  )
}
