import type { ButtonHTMLAttributes } from 'react'
import { cx } from './cx'

export type ButtonVariant = 'primary' | 'secondary' | 'danger'
export type ButtonSize = 'sm' | 'md'

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
  size?: ButtonSize
  /** Desabilita o botão, marca `aria-busy` e mostra um spinner antes do rótulo. */
  loading?: boolean
}

// Foco visível só na navegação por teclado (`focus-visible`), não no clique.
const BASE =
  'inline-flex items-center justify-center gap-2 rounded-md font-medium transition-colors ' +
  'disabled:cursor-not-allowed focus-visible:outline-none focus-visible:ring-2 ' +
  'focus-visible:ring-emerald-400 focus-visible:ring-offset-2 focus-visible:ring-offset-surface'

const VARIANTS: Record<ButtonVariant, string> = {
  primary:   'bg-accent hover:bg-emerald-500 disabled:bg-slate-700 text-white',
  secondary: 'border border-slate-700 text-slate-300 hover:border-slate-500 hover:text-slate-100 disabled:opacity-50',
  danger:    'bg-danger hover:bg-red-500 disabled:bg-slate-700 text-white',
}

const SIZES: Record<ButtonSize, string> = {
  sm: 'px-3 py-1.5 text-sm',
  md: 'px-4 py-2 text-sm',
}

/**
 * Botão base do app. Apresentação pura: o chamador decide o que o clique faz.
 *
 * `type` padrão é `button` (não o `submit` do HTML), para um botão dentro de um
 * `<form>` não enviar o formulário sem querer; o submit é sempre explícito.
 */
export function Button({
  variant = 'primary',
  size = 'md',
  loading = false,
  disabled,
  type = 'button',
  className,
  children,
  ...rest
}: ButtonProps) {
  return (
    <button
      type={type}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={cx(BASE, VARIANTS[variant], SIZES[size], className)}
      {...rest}
    >
      {loading && (
        <span
          aria-hidden="true"
          data-testid="button-spinner"
          className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-current border-t-transparent"
        />
      )}
      {children}
    </button>
  )
}
