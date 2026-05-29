import { useQuery } from '@tanstack/react-query'
import { api } from '../client'
import type { Standings } from '../../domain/types'

export const standingsKey = ['standings'] as const

export function useStandings() {
  return useQuery({
    queryKey: standingsKey,
    queryFn: () => api.get<Standings>(`/api/league/standings`),
  })
}
