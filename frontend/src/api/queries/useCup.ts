import { useQuery } from '@tanstack/react-query'
import { api, ApiError } from '../client'
import type { Cup } from '../../domain/types'

export const cupKey = ['cup'] as const

/**
 * Chave da copa da temporada corrente (issue #61). O 404 (`CUP_NOT_FOUND`) é
 * resposta NORMAL — liga sem copa ou save anterior a ela —, então vira `null`
 * em vez de erro, como a carreira em `useClubCareer`.
 */
export function useCup() {
  return useQuery({
    queryKey: cupKey,
    queryFn: async () => {
      try {
        return await api.get<Cup>('/api/league/cup')
      } catch (err) {
        if (err instanceof ApiError && err.status === 404) return null
        throw err
      }
    },
  })
}
