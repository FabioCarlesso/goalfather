import { useQuery } from '@tanstack/react-query'
import { api } from '../client'
import type { Standings } from '../../domain/types'

export const standingsKey = ['standings'] as const

/** Tabelas da temporada ativa — uma por divisão, da elite para baixo (issue #47). */
export function useStandings() {
  return useQuery({
    queryKey: standingsKey,
    queryFn: () => api.get<Standings[]>(`/api/league/standings`),
  })
}
