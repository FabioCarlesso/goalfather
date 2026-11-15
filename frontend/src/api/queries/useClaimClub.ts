import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../client'
import { availableClubsKey } from './useAvailableClubs'
import { worldCountriesKey } from './useWorldCountries'
import type { AuthUser } from '../../domain/types'

/**
 * Reivindica um clube (issue #19). Devolve o usuário atualizado (com `clubId`).
 * 409 (`CLUB_ALREADY_CLAIMED`) chega como `ApiError` e é tratado na página.
 */
export function useClaimClub() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (clubId: number) => api.post<AuthUser>(`/api/clubs/${clubId}/claim`),
    // `onSettled`, não só `onSuccess`: no 409 outro técnico levou o clube
    // primeiro, e a lista também precisa refletir isso.
    onSettled: () => {
      qc.invalidateQueries({ queryKey: availableClubsKey })
      // Clubes livres por liga mudaram (issue #131).
      qc.invalidateQueries({ queryKey: worldCountriesKey })
    },
  })
}
