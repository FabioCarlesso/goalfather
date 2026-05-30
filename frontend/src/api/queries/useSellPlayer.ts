import { useMutation, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { api } from '../client'
import { clubKey } from './useClub'
import { marketKey } from './useMarket'
import { transferErrorMessage } from '../errorMessages'
import type { TransferResult } from '../../domain/types'
import type { components } from '../generated'

type SellPlayerRequest = components['schemas']['SellPlayerRequest']

export function useSellPlayer() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: SellPlayerRequest) =>
      api.post<TransferResult>('/api/market/sell', body),
    onSuccess: (result, vars) => {
      if (result.type === 'Success') {
        toast.success(`${result.player.name} vendido!`)
        qc.invalidateQueries({ queryKey: clubKey(vars.clubId) })
        qc.invalidateQueries({ queryKey: marketKey })
      } else {
        toast.error(transferErrorMessage(result))
      }
    },
  })
}
