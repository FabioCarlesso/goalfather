import { useQuery } from '@tanstack/react-query'
import { api } from '../client'
import type { AvailableClub } from '../../domain/types'

export const availableClubsKey = ['clubs', 'available'] as const

/** Clubes sem dono para a tela de seleção (issue #19). */
export function useAvailableClubs() {
  return useQuery({
    queryKey: availableClubsKey,
    queryFn: () => api.get<AvailableClub[]>('/api/clubs/available'),
  })
}
