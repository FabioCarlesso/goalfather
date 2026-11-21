import { useMutation, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { api } from '../client'
import { clubKey } from './useClub'
import type { ContractOffer, RenewalResult } from '../../domain/types'

const RENEW_RESULT_TOAST = 'renew-result'

/**
 * Oferecer renovação a um jogador do próprio elenco (issue #138). O desfecho de
 * negócio vem no 200 (`RenewalResult`): a recusa e o esfriamento aparecem no
 * modal, que os recebe pelo `onSuccess` do chamador; aqui só o que é aviso.
 */
export function useRenewContract(clubId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ playerId, offer }: { playerId: number; offer: ContractOffer }) =>
      api.post<RenewalResult>(`/api/clubs/${clubId}/players/${playerId}/renewal`, offer),
    onSuccess: (result) => {
      // `switch` exaustivo: uma variante nova no contrato quebra a compilação aqui.
      switch (result.type) {
        case 'Renewed':
          toast.success(`${result.player.name} renovou o contrato!`, { id: RENEW_RESULT_TOAST })
          qc.invalidateQueries({ queryKey: clubKey(clubId) })
          return
        case 'PlayerRefused':
        case 'TalksCoolingDown':
          return
        case 'PlayerNotAvailable':
          toast.error('Este jogador não está mais no seu elenco.', { id: RENEW_RESULT_TOAST })
          qc.invalidateQueries({ queryKey: clubKey(clubId) })
          return
        case 'Conflict':
          toast.error('O clube foi alterado por outra operação ao mesmo tempo — nada foi aplicado. Tente de novo.', { id: RENEW_RESULT_TOAST })
          qc.invalidateQueries({ queryKey: clubKey(clubId) })
          return
      }
    },
  })
}
