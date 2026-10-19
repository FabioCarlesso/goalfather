import { useQuery } from '@tanstack/react-query'
import { api } from '../client'
import type { BoardStatus } from '../../domain/types'

export const boardKey = (clubId: number) => ['board', clubId] as const

/**
 * Painel da diretoria do clube (issue #62): meta da temporada, posição atual e
 * confiança no técnico. Muda a cada rodada de liga — a `RoundPage` invalida
 * esta chave no `RoundFinished`.
 */
export function useBoard(clubId: number) {
  return useQuery({
    queryKey: boardKey(clubId),
    queryFn: () => api.get<BoardStatus>(`/api/clubs/${clubId}/board`),
  })
}
