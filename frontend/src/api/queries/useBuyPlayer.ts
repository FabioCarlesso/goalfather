import { useMutation, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { api } from '../client'
import { clubKey } from './useClub'
import { marketKey } from './useMarket'
import { transferErrorMessage } from '../errorMessages'
import type { TransferResult } from '../../domain/types'
import type { components } from '../generated'

type BuyPlayerRequest = components['schemas']['BuyPlayerRequest']

export function useBuyPlayer() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: BuyPlayerRequest) =>
      api.post<TransferResult>('/api/market/buy', body),
    onSuccess: (result, vars) => {
      // O endpoint retorna 200 sempre — sucesso/erro vivem no `type` do payload.
      if (result.type === 'Success') {
        toast.success(`${result.player.name} contratado!`)
        qc.invalidateQueries({ queryKey: clubKey(vars.clubId) })
        qc.invalidateQueries({ queryKey: marketKey })
      } else {
        toast.error(transferErrorMessage(result))
      }
    },
  })
}
