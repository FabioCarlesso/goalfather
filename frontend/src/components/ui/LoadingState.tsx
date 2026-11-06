export type LoadingShape = 'list' | 'table' | 'cards'

export interface LoadingStateProps {
  /** Texto anunciado a leitores de tela; visualmente, só o skeleton aparece. */
  label?: string
  /** Forma do conteúdo que está por vir. */
  shape?: LoadingShape
  /** Quantas linhas (ou cards) desenhar. */
  rows?: number
  /** Reserva o espaço do cabeçalho da página; desligue em blocos dentro da página. */
  withHeader?: boolean
  className?: string
}

const DEFAULT_ROWS: Record<LoadingShape, number> = { list: 8, table: 10, cards: 6 }

/**
 * Skeleton de carregamento inicial, com as alturas do conteúdo real para a
 * página não "pular" quando os dados chegam.
 *
 * Sem `aria-busy` de propósito: ele pede ao leitor de tela que espere, e
 * calaria o anúncio do `role="status"`.
 */
export function LoadingState({
  label = 'Carregando…',
  shape = 'list',
  rows = DEFAULT_ROWS[shape],
  withHeader = true,
  className,
}: LoadingStateProps) {
  return (
    <div role="status" className={className}>
      <span className="sr-only">{label}</span>
      {/* `motion-reduce:` respeita o `prefers-reduced-motion` do sistema. */}
      <div aria-hidden="true" data-testid="skeleton" className="animate-pulse space-y-6 motion-reduce:animate-none">
        {withHeader && (
          <div className="space-y-1">
            <div className="h-9 w-64 max-w-full rounded bg-border" />
            <div className="h-5 w-48 max-w-full rounded bg-border/60" />
          </div>
        )}
        <SkeletonBody shape={shape} rows={rows} />
      </div>
    </div>
  )
}

function SkeletonBody({ shape, rows }: { shape: LoadingShape; rows: number }) {
  const items = Array.from({ length: rows }, (_, i) => i)

  switch (shape) {
    case 'list':
      return (
        <div className="divide-y divide-border rounded-lg border border-border">
          {items.map((i) => (
            <div key={i} className="flex h-10 items-center justify-between gap-4 px-4">
              <div className="h-3 w-1/2 rounded bg-border" />
              <div className="h-3 w-8 rounded bg-border" />
            </div>
          ))}
        </div>
      )
    case 'table':
      return (
        <div className="divide-y divide-border">
          <div className="flex h-8 items-center">
            <div className="h-3 w-full rounded bg-border/60" />
          </div>
          {items.map((i) => (
            <div key={i} className="flex h-9 items-center gap-4">
              <div className="h-3 w-6 rounded bg-border" />
              <div className="h-3 flex-1 rounded bg-border" />
              <div className="h-3 w-1/3 rounded bg-border" />
            </div>
          ))}
        </div>
      )
    case 'cards':
      return (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {items.map((i) => (
            <div key={i} className="h-28 rounded-lg border border-border bg-surface-raised/60" />
          ))}
        </div>
      )
  }
}
