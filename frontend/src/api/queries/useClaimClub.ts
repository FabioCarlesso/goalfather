import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../client'
import { availableClubsKey } from './useAvailableClubs'
import type { AuthUser } from '../../domain/types'

/**
 * Reivindica um clube (issue #19). Devolve o usuário atualizado (com `clubId`).
 * 409 (`CLUB_ALREADY_CLAIMED`) chega como `ApiError` e é tratado na página.
 */
export function useClaimClub() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (clubId: number) => api.post<AuthUser>(`/api/clubs/${clubId}/claim`),
    onSuccess: () => {
      // A lista de disponíveis mudou — invalida para refletir o clube tomado.
      qc.invalidateQueries({ queryKey: availableClubsKey })
    },
  })
}
