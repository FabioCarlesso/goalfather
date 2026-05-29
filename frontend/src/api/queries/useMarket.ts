import { useQuery } from '@tanstack/react-query'
import { api } from '../client'
import type { MarketEntry } from '../../domain/types'

export const marketKey = ['market'] as const

export function useMarket() {
  return useQuery({
    queryKey: marketKey,
    queryFn: () => api.get<MarketEntry[]>(`/api/market`),
  })
}
