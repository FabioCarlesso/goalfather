import { useId, type HTMLAttributes, type ReactNode } from 'react'
import { cx } from './cx'

export interface CardProps extends Omit<HTMLAttributes<HTMLElement>, 'title'> {
  /** Título do card (vira um `<h2>`). Aceita nó para levar badges junto. */
  title?: ReactNode
  /** Slot à direita do título: legenda curta, botão, contador… */
  actions?: ReactNode
  /**
   * `section` transforma o card numa região nomeada pelo título
   * (`aria-labelledby`), útil quando o card é um bloco navegável da página.
   */
  as?: 'div' | 'section'
}

/**
 * Superfície elevada padrão (borda + fundo translúcido). Apresentação pura.
 * Espaçamento interno entre filhos (`space-y-*`) fica com o chamador, via `className`.
 */
export function Card({ title, actions, as: Tag = 'div', className, children, ...rest }: CardProps) {
  const headingId = useId()
  const hasHeader = title != null || actions != null
  const labelled = Tag === 'section' && title != null

  return (
    <Tag
      aria-labelledby={labelled ? headingId : undefined}
      className={cx('rounded-lg border border-border bg-surface-raised/60 p-4', className)}
      {...rest}
    >
      {hasHeader && (
        <div className="flex items-center justify-between">
          {title != null && (
            <h2 id={headingId} className="text-lg font-semibold text-text">
              {title}
            </h2>
          )}
          {actions}
        </div>
      )}
      {children}
    </Tag>
  )
}
