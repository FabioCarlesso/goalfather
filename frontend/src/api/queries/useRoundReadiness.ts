import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../client'
import type { ReadinessStatus } from '../../domain/types'

export const roundReadinessKey = ['round', 'readiness'] as const

/**
 * Prontidão da rodada compartilhada (issue #20). Em liga com vários técnicos,
 * o estado muda quando OUTROS sinalizam — por isso refazemos a busca em
 * intervalo enquanto a página está aberta (refetch é suficiente; o WS de
 * lobby é opcional no escopo da issue).
 *
 * `paused` desliga o polling quando o card não está visível (ex.: durante a
 * partida ao vivo), evitando requisições ociosas a cada 3s (ponto #7).
 */
export function useRoundReadiness(options?: { paused?: boolean }) {
  return useQuery({
    queryKey: roundReadinessKey,
    queryFn: () => api.get<ReadinessStatus>('/api/league/round/readiness'),
    refetchInterval: options?.paused ? false : 3000,
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
