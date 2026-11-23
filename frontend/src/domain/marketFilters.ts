// Filtros, ordenação e página do Mercado (issues #111 e #139). Desde a #139 o
// pool de livres tem centenas de jogadores e a busca é do SERVIDOR
// (`GET /api/market`, paginado): este arquivo só traduz o estado da tela entre
// a URL e os parâmetros do contrato. Nenhuma regra de mercado mora aqui.

import { POSITION_ABBR } from './formations'
import type { MarketSort, Position } from './types'

export const MARKET_POSITIONS = Object.keys(POSITION_ABBR) as Position[]

export const MARKET_SORT_OPTIONS = [
  { key: 'price', label: 'Taxa' },
  { key: 'value', label: 'Valor' },
  { key: 'overall', label: 'OVR' },
  { key: 'age', label: 'Idade' },
] as const satisfies readonly { key: MarketSort; label: string }[]

export type MarketSortKey = MarketSort
export type MarketSortDir = 'asc' | 'desc'

/**
 * Faixas de idade da tela. A URL guarda a chave da faixa (`age=sub21`), e a
 * API recebe os limites (`minAge`/`maxAge`).
 */
export const MARKET_AGE_BANDS = [
  { key: 'sub21', label: 'Até 21', minAge: undefined, maxAge: 21 },
  { key: 'auge', label: '22 a 29', minAge: 22, maxAge: 29 },
  { key: 'veterano', label: '30 ou mais', minAge: 30, maxAge: undefined },
] as const

/** Faixas de valor de mercado, em centavos de USD (escala da economia do jogo). */
export const MARKET_VALUE_BANDS = [
  { key: 'ate10k', label: 'Até US$ 10 mil', minValue: undefined, maxValue: 10_000_00 },
  { key: '10a50k', label: 'US$ 10 mil a 50 mil', minValue: 10_000_00, maxValue: 50_000_00 },
  { key: '50a250k', label: 'US$ 50 mil a 250 mil', minValue: 50_000_00, maxValue: 250_000_00 },
  { key: 'acima250k', label: 'Acima de US$ 250 mil', minValue: 250_000_00, maxValue: undefined },
] as const

export type MarketAgeBand = (typeof MARKET_AGE_BANDS)[number]['key']
export type MarketValueBand = (typeof MARKET_VALUE_BANDS)[number]['key']

/** Tamanho da página pedida ao backend. */
export const MARKET_PAGE_SIZE = 20

export interface MarketFilterState {
  /** `null` = todas as posições. */
  position: Position | null
  /** Código FIFA (`BRA`); `null` = todas. */
  nationality: string | null
  age: MarketAgeBand | null
  value: MarketValueBand | null
  sort: MarketSortKey
  dir: MarketSortDir
  /** Texto cru como foi digitado; o servidor normaliza acento e caixa. */
  query: string
  /** Página, começando em 0 (na URL, `p` começa em 1). */
  page: number
}

export const DEFAULT_MARKET_FILTERS: MarketFilterState = {
  position: null,
  nationality: null,
  age: null,
  value: null,
  sort: 'price',
  dir: 'asc',
  query: '',
  page: 0,
}

export function isMarketSortKey(value: string | null): value is MarketSortKey {
  return MARKET_SORT_OPTIONS.some((option) => option.key === value)
}

function isPosition(value: string | null): value is Position {
  return MARKET_POSITIONS.some((position) => position === value)
}

export const isMarketAgeBand = (value: string | null): value is MarketAgeBand =>
  MARKET_AGE_BANDS.some((b) => b.key === value)
export const isMarketValueBand = (value: string | null): value is MarketValueBand =>
  MARKET_VALUE_BANDS.some((b) => b.key === value)
const isCountryCode = (value: string | null): value is string => value !== null && /^[A-Z]{3}$/.test(value)

/** Lê a query string. Valor inválido (URL editada à mão) cai no padrão, sem quebrar. */
export function parseMarketFilters(params: URLSearchParams): MarketFilterState {
  const position = params.get('pos')
  const nationality = params.get('nat')
  const age = params.get('age')
  const value = params.get('val')
  const sort = params.get('sort')
  const page = Number(params.get('p'))
  return {
    position: isPosition(position) ? position : null,
    nationality: isCountryCode(nationality) ? nationality : null,
    age: isMarketAgeBand(age) ? age : null,
    value: isMarketValueBand(value) ? value : null,
    sort: isMarketSortKey(sort) ? sort : DEFAULT_MARKET_FILTERS.sort,
    dir: params.get('dir') === 'desc' ? 'desc' : 'asc',
    query: params.get('q') ?? '',
    page: Number.isInteger(page) && page >= 1 ? page - 1 : 0,
  }
}

/** Caminho inverso de `parseMarketFilters`. Valores padrão ficam fora da URL. */
export function toMarketSearchParams(filters: MarketFilterState): URLSearchParams {
  const params = new URLSearchParams()
  if (filters.position) params.set('pos', filters.position)
  if (filters.nationality) params.set('nat', filters.nationality)
  if (filters.age) params.set('age', filters.age)
  if (filters.value) params.set('val', filters.value)
  if (filters.sort !== DEFAULT_MARKET_FILTERS.sort) params.set('sort', filters.sort)
  if (filters.dir !== DEFAULT_MARKET_FILTERS.dir) params.set('dir', filters.dir)
  if (filters.query) params.set('q', filters.query)
  if (filters.page > 0) params.set('p', String(filters.page + 1))
  return params
}

/**
 * Os parâmetros de `GET /api/market` (contrato `listMarket`). A busca vai
 * aparada: espaço sobrando não deve virar outra chave de cache.
 */
export function toMarketQuery(filters: MarketFilterState): URLSearchParams {
  const params = new URLSearchParams()
  if (filters.position) params.set('position', filters.position)
  if (filters.nationality) params.set('nationality', filters.nationality)
  const age = MARKET_AGE_BANDS.find((b) => b.key === filters.age)
  if (age?.minAge !== undefined) params.set('minAge', String(age.minAge))
  if (age?.maxAge !== undefined) params.set('maxAge', String(age.maxAge))
  const value = MARKET_VALUE_BANDS.find((b) => b.key === filters.value)
  if (value?.minValue !== undefined) params.set('minValue', String(value.minValue))
  if (value?.maxValue !== undefined) params.set('maxValue', String(value.maxValue))
  const query = filters.query.trim()
  if (query) params.set('q', query)
  params.set('sort', filters.sort)
  params.set('dir', filters.dir)
  params.set('page', String(filters.page))
  params.set('size', String(MARKET_PAGE_SIZE))
  return params
}

/** Algum filtro além da ordenação está ativo? Decide entre "mercado vazio" e "nada encontrado". */
export function hasActiveMarketFilters(filters: MarketFilterState): boolean {
  return (
    filters.position !== null ||
    filters.nationality !== null ||
    filters.age !== null ||
    filters.value !== null ||
    filters.query.trim() !== ''
  )
}
