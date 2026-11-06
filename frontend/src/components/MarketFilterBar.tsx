import { useEffect, useRef } from 'react'
import { Button } from './ui/Button'
import { POSITION_ABBR, POSITION_LABEL } from '../domain/formations'
import {
  isMarketSortKey,
  MARKET_POSITIONS,
  MARKET_SORT_OPTIONS,
  type MarketFilterState,
} from '../domain/marketFilters'

const FIELD_CLASS =
  'rounded-md border border-slate-700 bg-slate-900 px-2 py-1 text-sm text-slate-100'

interface MarketFilterBarProps {
  filters: MarketFilterState
  onChange: (patch: Partial<MarketFilterState>) => void
  /** Digitação da busca: o chamador não deve criar uma entrada de histórico por tecla. */
  onQueryChange: (query: string) => void
}

/** Chips de posição, busca e ordenação do Mercado. Apresentação pura. */
export function MarketFilterBar({ filters, onChange, onQueryChange }: MarketFilterBarProps) {
  return (
    <div className="mb-4 space-y-3">
      <div role="group" aria-label="Filtrar por posição" className="flex flex-wrap gap-2">
        <Chip selected={filters.position === null} onClick={() => onChange({ position: null })}>
          Todos
        </Chip>
        {MARKET_POSITIONS.map((position) => (
          <Chip
            key={position}
            title={POSITION_LABEL[position]}
            selected={filters.position === position}
            onClick={() => onChange({ position })}
          >
            {POSITION_ABBR[position]}
          </Chip>
        ))}
      </div>

      <div className="flex flex-wrap items-end gap-3">
        <SearchField value={filters.query} onChange={onQueryChange} />
        <label className="text-xs text-slate-400">
          Ordenar por
          <select
            value={filters.sort}
            onChange={(e) => {
              const sort = e.target.value
              if (isMarketSortKey(sort)) onChange({ sort })
            }}
            className={`mt-1 block ${FIELD_CLASS}`}
          >
            {MARKET_SORT_OPTIONS.map(({ key, label }) => (
              <option key={key} value={key}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <Button
          variant="secondary"
          size="sm"
          onClick={() => onChange({ dir: filters.dir === 'asc' ? 'desc' : 'asc' })}
        >
          <span aria-hidden="true">{filters.dir === 'asc' ? '↑' : '↓'}</span>
          Ordem: {filters.dir === 'asc' ? 'crescente' : 'decrescente'}
        </Button>
      </div>
    </div>
  )
}

interface ChipProps {
  selected: boolean
  onClick: () => void
  title?: string
  children: string
}

function Chip({ selected, onClick, title, children }: ChipProps) {
  return (
    <Button
      variant={selected ? 'primary' : 'secondary'}
      size="sm"
      aria-pressed={selected}
      title={title}
      onClick={onClick}
    >
      {children}
    </Button>
  )
}

/**
 * Campo não controlado de propósito. O React Router aplica cada mudança de URL
 * dentro de `startTransition`, e um input controlado por um valor em transição
 * volta ao valor antigo a cada tecla: o cursor pula e letras digitadas rápido
 * se perdem. Aqui o DOM manda enquanto o técnico digita e a lista acompanha a URL.
 *
 * O efeito só escreve no campo em mudanças externas (voltar do navegador,
 * "Limpar filtros", link do menu). `pending` guarda o último valor enviado para
 * a URL, para não confundir um commit atrasado da própria digitação com uma
 * mudança externa.
 */
function SearchField({ value, onChange }: { value: string; onChange: (query: string) => void }) {
  const input = useRef<HTMLInputElement>(null)
  const pending = useRef<string | null>(null)

  useEffect(() => {
    if (pending.current !== null) {
      if (pending.current === value) pending.current = null
      return
    }
    if (input.current && input.current.value !== value) input.current.value = value
  }, [value])

  return (
    <label className="min-w-0 flex-1 basis-48 text-xs text-slate-400">
      Buscar por nome
      <input
        ref={input}
        type="search"
        defaultValue={value}
        onChange={(e) => {
          const next = e.target.value
          // Voltar ao valor já aplicado não muda a URL: nenhum efeito vai limpar `pending`.
          pending.current = next === value ? null : next
          onChange(next)
        }}
        className={`mt-1 block w-full ${FIELD_CLASS}`}
      />
    </label>
  )
}
