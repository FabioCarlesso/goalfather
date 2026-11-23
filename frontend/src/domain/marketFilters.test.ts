import { describe, expect, it } from 'vitest'
import {
  DEFAULT_MARKET_FILTERS,
  hasActiveMarketFilters,
  MARKET_PAGE_SIZE,
  parseMarketFilters,
  toMarketQuery,
  toMarketSearchParams,
  type MarketFilterState,
} from './marketFilters'

const BASE = DEFAULT_MARKET_FILTERS

describe('parseMarketFilters', () => {
  it('usa os padrões quando a URL não traz nada', () => {
    expect(parseMarketFilters(new URLSearchParams())).toEqual(BASE)
  })

  it('valores inválidos caem no padrão', () => {
    const params = new URLSearchParams('pos=XX&sort=constructor&dir=lateral&nat=br&age=bebe&val=caro&p=-3')
    expect(parseMarketFilters(params)).toEqual(BASE)
  })

  it('ida e volta pela query string preserva o estado', () => {
    const filters: MarketFilterState = {
      position: 'FW',
      nationality: 'ESP',
      age: 'sub21',
      value: '10a50k',
      sort: 'value',
      dir: 'desc',
      query: 'Lú cas',
      page: 2,
    }
    expect(parseMarketFilters(toMarketSearchParams(filters))).toEqual(filters)
  })

  it('valores padrão ficam fora da URL, e a página da URL começa em 1', () => {
    expect(toMarketSearchParams(BASE).toString()).toBe('')
    expect(toMarketSearchParams({ ...BASE, page: 1 }).get('p')).toBe('2')
  })
})

describe('toMarketQuery (contrato listMarket, issue #139)', () => {
  it('sem filtro manda só ordenação e página', () => {
    expect(Object.fromEntries(toMarketQuery(BASE))).toEqual({
      sort: 'price',
      dir: 'asc',
      page: '0',
      size: String(MARKET_PAGE_SIZE),
    })
  })

  it('traduz as faixas de idade e valor nos limites do contrato', () => {
    const query = toMarketQuery({ ...BASE, age: 'auge', value: '50a250k' })
    expect(query.get('minAge')).toBe('22')
    expect(query.get('maxAge')).toBe('29')
    expect(query.get('minValue')).toBe(String(50_000_00))
    expect(query.get('maxValue')).toBe(String(250_000_00))

    // Faixa aberta numa ponta não manda a outra.
    const open = toMarketQuery({ ...BASE, age: 'veterano', value: 'ate10k' })
    expect(open.get('minAge')).toBe('30')
    expect(open.has('maxAge')).toBe(false)
    expect(open.has('minValue')).toBe(false)
    expect(open.get('maxValue')).toBe(String(10_000_00))
  })

  it('posição, nacionalidade e busca aparada vão como vieram', () => {
    const query = toMarketQuery({ ...BASE, position: 'GK', nationality: 'BRA', query: '  muñoz ', page: 3 })
    expect(query.get('position')).toBe('GK')
    expect(query.get('nationality')).toBe('BRA')
    expect(query.get('q')).toBe('muñoz')
    expect(query.get('page')).toBe('3')
  })

  it('busca só de espaços não vira parâmetro (nem outra chave de cache)', () => {
    expect(toMarketQuery({ ...BASE, query: '   ' }).toString()).toBe(toMarketQuery(BASE).toString())
  })
})

describe('hasActiveMarketFilters', () => {
  it('ordenação e página não contam como filtro', () => {
    expect(hasActiveMarketFilters({ ...BASE, sort: 'age', dir: 'desc', page: 4 })).toBe(false)
    expect(hasActiveMarketFilters({ ...BASE, nationality: 'ITA' })).toBe(true)
    expect(hasActiveMarketFilters({ ...BASE, query: ' ' })).toBe(false)
  })
})
