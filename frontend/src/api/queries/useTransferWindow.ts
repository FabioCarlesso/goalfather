import { useQuery } from '@tanstack/react-query'
import { api } from '../client'
import type { TransferWindowStatus } from '../../domain/types'

/**
 * Janela de transferência do clube (issue #137). Sob a família `['calendar']`,
 * que o `useRoundStream` invalida a cada encaixe jogado: a data andou, a
 * janela pode ter aberto ou fechado.
 */
export const transferWindowKey = (clubId: number) => ['calendar', 'transfer-window', clubId] as const

export function useTransferWindow(clubId: number) {
  return useQuery({
    queryKey: transferWindowKey(clubId),
    queryFn: () => api.get<TransferWindowStatus>(`/api/clubs/${clubId}/transfer-window`),
  })
}
