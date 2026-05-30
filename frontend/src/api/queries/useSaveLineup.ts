import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../client'
import { clubKey } from './useClub'
import type { components } from '../generated'

type LineupRequest = components['schemas']['LineupRequest']

export function useSaveLineup(clubId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: LineupRequest) =>
      api.post<void>(`/api/clubs/${clubId}/lineup`, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: clubKey(clubId) })
    },
    meta: { successMessage: 'Escalação salva!' },
  })
}
