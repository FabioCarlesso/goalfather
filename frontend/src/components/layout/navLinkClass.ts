import { cx } from '../ui/cx'

/** Classes de um `NavLink` do cabeçalho; `layout` traz o espaçamento de cada contexto. */
export const navLinkClass =
  (layout: string) =>
  ({ isActive }: { isActive: boolean }) =>
    cx(
      'rounded-md transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400',
      isActive ? 'bg-slate-800 text-slate-100' : 'text-slate-400 hover:bg-slate-800/50 hover:text-slate-200',
      layout,
    )
