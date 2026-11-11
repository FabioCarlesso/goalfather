import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../client'
import type { Round } from '../../domain/types'

export const currentRoundKey = ['round', 'current'] as const

export function useCurrentRound() {
  return useQuery({
    queryKey: currentRoundKey,
    queryFn: () => api.get<Round>('/api/league/round/current'),
  })
}

/** Chave do encaixe liberado (issue #125). */
interface PlayRoundResponse { matchday: string }

export function usePlayRound() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => api.post<PlayRoundResponse>('/api/league/round/play'),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: currentRoundKey })
    },
  })
}
