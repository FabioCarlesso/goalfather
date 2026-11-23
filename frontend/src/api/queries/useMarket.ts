import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { api } from '../client'
import type { MarketPage } from '../../domain/types'
import { toMarketQuery, type MarketFilterState } from '../../domain/marketFilters'

/** Prefixo de todas as buscas do mercado: compra e venda invalidam todas de uma vez. */
export const marketKey = ['market'] as const

/**
 * Uma página da busca no pool de livres (issue #139). A chave inclui os
 * parâmetros já traduzidos para o contrato, então cada combinação de filtros e
 * página tem o seu cache. `keepPreviousData` mantém a página anterior na tela
 * enquanto a próxima carrega — trocar de página não pisca o "Carregando…".
 */
export function useMarket(filters: MarketFilterState) {
  const query = toMarketQuery(filters).toString()
  return useQuery({
    queryKey: [...marketKey, query],
    queryFn: () => api.get<MarketPage>(`/api/market?${query}`),
    placeholderData: keepPreviousData,
  })
}
