import { errorMessage } from '../../api/errorMessages'
import { Button } from './Button'
import { cx } from './cx'

export interface ErrorStateProps {
  /** O erro da query; vira texto amigável via `errorMessage()`. */
  error: unknown
  /** Ação de "Tentar novamente". Nas páginas, é o `refetch` da query. */
  onRetry: () => void
  className?: string
}

/**
 * Falha ao buscar dados: mensagem amigável e ação de recuperação, sem F5.
 * Apresentação pura: quem conhece a query é a página.
 *
 * `() => onRetry()` não repassa o evento de clique: `refetch` aceita um objeto
 * de opções como 1º argumento e receberia o `MouseEvent` no lugar dele.
 */
export function ErrorState({ error, onRetry, className }: ErrorStateProps) {
  return (
    <div
      role="alert"
      className={cx(
        'flex flex-wrap items-center justify-between gap-3 rounded-lg border border-red-700/50 bg-red-900/20 p-4',
        className,
      )}
    >
      <p className="text-sm text-red-300">{errorMessage(error)}</p>
      <Button variant="secondary" size="sm" onClick={() => onRetry()}>
        Tentar novamente
      </Button>
    </div>
  )
}
