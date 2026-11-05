import { Card } from './Card'

export interface StatProps {
  label: string
  value: string
}

/** Métrica de destaque (rótulo pequeno + valor), extraída da Dashboard. */
export function Stat({ label, value }: StatProps) {
  return (
    <Card>
      <div className="text-xs uppercase tracking-wide text-slate-500">{label}</div>
      <div className="text-lg font-semibold text-text mt-1">{value}</div>
    </Card>
  )
}
