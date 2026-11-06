import { describe, expect, it } from 'vitest'
import {
  applyMarketFilters,
  parseMarketFilters,
  toMarketSearchParams,
  type MarketFilterState,
} from './marketFilters'
import { marketEntries } from '../mocks/seed'
import type { MarketEntry } from './types'

const BASE: MarketFilterState = { position: null, sort: 'price', dir: 'asc', query: '' }

const [template] = marketEntries

/** Entrada do seed com id, nome e preço trocados: o resto do jogador não importa aqui. */
const entry = (id: number, name: string, price: number): MarketEntry => ({
  price,
  player: { ...template!.player, id, name },
})

const names = (entries: MarketEntry[]) => entries.map((e) => e.player.name)

describe('parseMarketFilters', () => {
  it('usa os padrões quando a URL não traz nada', () => {
    expect(parseMarketFilters(new URLSearchParams())).toEqual(BASE)
  })

  it('valores inválidos caem no padrão', () => {
    const params = new URLSearchParams('pos=XX&sort=constructor&dir=lateral')
    expect(parseMarketFilters(params)).toEqual(BASE)
  })

  it('ida e volta pela query string preserva o estado', () => {
    const filters: MarketFilterState = { position: 'FW', sort: 'age', dir: 'desc', query: 'Lú cas' }
    expect(parseMarketFilters(toMarketSearchParams(filters))).toEqual(filters)
  })

  it('valores padrão ficam fora da URL', () => {
    expect(toMarketSearchParams(BASE).toString()).toBe('')
  })
})

describe('applyMarketFilters', () => {
  it('não muta o array de entrada (cache do TanStack Query)', () => {
    const cached: readonly MarketEntry[] = Object.freeze([entry(1, 'B', 20), entry(2, 'A', 10)])
    expect(names(applyMarketFilters(cached, BASE))).toEqual(['A', 'B'])
    expect(names([...cached])).toEqual(['B', 'A'])
  })

  it('empate cai em nome crescente nos dois sentidos, e depois em id', () => {
    const entries = [entry(3, 'Zeca', 10), entry(2, 'Ana', 10), entry(1, 'Ana', 10)]
    const ids = (dir: MarketFilterState['dir']) =>
      applyMarketFilters(entries, { ...BASE, dir }).map((e) => e.player.id)
    expect(ids('asc')).toEqual([1, 2, 3])
    expect(ids('desc')).toEqual([1, 2, 3])
  })

  it('busca ignora acentos e maiúsculas nos dois lados e apara espaços', () => {
    const entries = [entry(1, 'Bruno Aparício', 1), entry(2, 'Lucas', 2)]
    for (const query of ['aparicio', 'APARÍCIO', '  apar  ']) {
      expect(names(applyMarketFilters(entries, { ...BASE, query }))).toEqual(['Bruno Aparício'])
    }
  })
})
