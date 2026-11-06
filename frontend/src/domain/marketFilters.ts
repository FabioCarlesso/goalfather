// Filtro, ordenação e busca do Mercado (issue #111). Tudo aqui é apresentação
// sobre a lista já carregada: o contrato `listMarket` não tem parâmetros e
// nenhuma regra de transferência mora neste arquivo.

import { POSITION_ABBR } from './formations'
import type { MarketEntry, Position } from './types'

export const MARKET_POSITIONS = Object.keys(POSITION_ABBR) as Position[]

export const MARKET_SORT_OPTIONS = [
  { key: 'price', label: 'Preço' },
  { key: 'overall', label: 'OVR' },
  { key: 'age', label: 'Idade' },
  { key: 'salary', label: 'Salário' },
] as const

export type MarketSortKey = (typeof MARKET_SORT_OPTIONS)[number]['key']
export type MarketSortDir = 'asc' | 'desc'

export interface MarketFilterState {
  /** `null` = todas as posições. */
  position: Position | null
  sort: MarketSortKey
  dir: MarketSortDir
  /** Texto cru como foi digitado; a normalização só acontece na comparação. */
  query: string
}

const DEFAULT_SORT: MarketSortKey = 'price'
const DEFAULT_DIR: MarketSortDir = 'asc'

const SORT_VALUE: Record<MarketSortKey, (entry: MarketEntry) => number> = {
  price: (e) => e.price,
  overall: (e) => e.player.overall,
  age: (e) => e.player.age,
  salary: (e) => e.player.salary,
}

export function isMarketSortKey(value: string | null): value is MarketSortKey {
  return MARKET_SORT_OPTIONS.some((option) => option.key === value)
}

function isPosition(value: string | null): value is Position {
  return MARKET_POSITIONS.some((position) => position === value)
}

/** Lê a query string. Valor inválido (URL editada à mão) cai no padrão, sem quebrar. */
export function parseMarketFilters(params: URLSearchParams): MarketFilterState {
  const position = params.get('pos')
  const sort = params.get('sort')
  return {
    position: isPosition(position) ? position : null,
    sort: isMarketSortKey(sort) ? sort : DEFAULT_SORT,
    dir: params.get('dir') === 'desc' ? 'desc' : DEFAULT_DIR,
    query: params.get('q') ?? '',
  }
}

/** Caminho inverso de `parseMarketFilters`. Valores padrão ficam fora da URL. */
export function toMarketSearchParams(filters: MarketFilterState): URLSearchParams {
  const params = new URLSearchParams()
  if (filters.position) params.set('pos', filters.position)
  if (filters.sort !== DEFAULT_SORT) params.set('sort', filters.sort)
  if (filters.dir !== DEFAULT_DIR) params.set('dir', filters.dir)
  if (filters.query) params.set('q', filters.query)
  return params
}

const normalize = (text: string): string =>
  text.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase()

/**
 * Aplica filtro, busca e ordenação sem tocar em `entries`, que é o array do
 * cache do TanStack Query (`filter` e `toSorted` devolvem cópias).
 *
 * Empates caem sempre em nome e depois id, em ordem crescente mesmo com
 * `dir: 'desc'`, para a lista não mudar de ordem entre um render e outro.
 */
export function applyMarketFilters(
  entries: readonly MarketEntry[],
  { position, sort, dir, query }: MarketFilterState,
): MarketEntry[] {
  const needle = normalize(query.trim())
  const value = SORT_VALUE[sort]
  const sign = dir === 'asc' ? 1 : -1

  return entries
    .filter((e) => position === null || e.player.position === position)
    .filter((e) => normalize(e.player.name).includes(needle))
    .toSorted(
      (a, b) =>
        sign * (value(a) - value(b)) ||
        a.player.name.localeCompare(b.player.name, 'pt-BR') ||
        a.player.id - b.player.id,
    )
}
