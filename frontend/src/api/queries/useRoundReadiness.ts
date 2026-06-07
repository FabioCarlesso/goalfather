import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../client'
import type { ReadinessStatus } from '../../domain/types'

export const roundReadinessKey = ['round', 'readiness'] as const

/**
 * Prontidão da rodada compartilhada (issue #20). Em liga com vários técnicos,
 * o estado muda quando OUTROS sinalizam — por isso refazemos a busca em
 * intervalo enquanto a página está aberta (refetch é suficiente; o WS de
 * lobby é opcional no escopo da issue).
 */
export function useRoundReadiness() {
  return useQuery({
    queryKey: roundReadinessKey,
    queryFn: () => api.get<ReadinessStatus>('/api/league/round/readiness'),
    refetchInterval: 3000,
  })
}

export function useMarkReady() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => api.post<ReadinessStatus>('/api/league/round/ready'),
    // A resposta já é o status atualizado — escreve direto no cache (sem refetch).
    onSuccess: (status) => qc.setQueryData(roundReadinessKey, status),
  })
}
