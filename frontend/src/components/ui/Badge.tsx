import type { HTMLAttributes } from 'react'
import { cx } from './cx'

export type BadgeTone = 'neutral' | 'success' | 'warning' | 'danger' | 'info'

export interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  tone?: BadgeTone
}

const TONES: Record<BadgeTone, string> = {
  neutral: 'bg-slate-800 text-slate-300',
  success: 'bg-emerald-900/40 text-emerald-300',
  warning: 'bg-amber-900/40 text-amber-300',
  danger:  'bg-red-900/40 text-red-300',
  info:    'bg-sky-900/40 text-sky-300',
}

/** Etiqueta curta (status, divisão, lesão…). Apresentação pura. */
export function Badge({ tone = 'neutral', className, children, ...rest }: BadgeProps) {
  return (
    <span
      className={cx('rounded px-1.5 py-0.5 text-[10px] font-semibold', TONES[tone], className)}
      {...rest}
    >
      {children}
    </span>
  )
}
