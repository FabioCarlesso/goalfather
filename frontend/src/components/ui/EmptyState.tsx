import type { ReactNode } from 'react'
import { cx } from './cx'

export interface EmptyStateProps {
  /** Por que não há nada para mostrar (e, de preferência, quando passa a haver). */
  message: ReactNode
  /** Ação opcional abaixo da mensagem: um `Button`, um `Link`… */
  action?: ReactNode
  className?: string
}

/** A busca deu certo, mas não há dados: explica em vez de deixar a tela em branco. */
export function EmptyState({ message, action, className }: EmptyStateProps) {
  return (
    <div className={cx('space-y-3 rounded-lg border border-border bg-surface-raised/40 p-6 text-text-muted', className)}>
      <p>{message}</p>
      {action}
    </div>
  )
}
