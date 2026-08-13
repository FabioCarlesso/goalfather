import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../client'
import { clubKey } from './useClub'
import type { Club, TrainingFocus } from '../../domain/types'

/**
 * Foco de treino da semana (issue #58). Só registra a decisão — o efeito
 * (evolução, lesão, recuperação) entra na virada da rodada e chega à UI pelo
 * `RoundFinished`.
 */
export function useSetTrainingFocus(clubId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (focus: TrainingFocus) =>
      api.post<Club>(`/api/clubs/${clubId}/training`, { focus }),
    // O endpoint devolve o clube já atualizado — sem refetch.
    onSuccess: (club) => qc.setQueryData(clubKey(clubId), club),
    meta: { successMessage: 'Foco de treino definido!' },
  })
}
