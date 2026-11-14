import { useQuery } from '@tanstack/react-query'
import { api } from '../client'
import type { AvailableClub } from '../../domain/types'

/** Prefixo comum: invalidar este invalida a lista de todas as ligas. */
export const availableClubsKey = ['clubs', 'available'] as const

/**
 * Clubes sem dono para a seleção (issue #19). Com `competitionId`, só os da
 * liga — o último passo do onboarding país → liga → clube (issue #131). A
 * ordem (força média decrescente, depois nome) vem pronta do servidor.
 */
export function useAvailableClubs(competitionId?: string) {
  const query = competitionId ? `?competitionId=${encodeURIComponent(competitionId)}` : ''
  return useQuery({
    queryKey: [...availableClubsKey, competitionId ?? 'all'],
    queryFn: () => api.get<AvailableClub[]>(`/api/clubs/available${query}`),
  })
}
