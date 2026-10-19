import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { api } from '../client'
import { clubKey } from './useClub'
import { offerErrorMessage, type OfferRole } from '../errorMessages'
import type { CreateOfferRequest, TransferOffer, TransferOfferResult } from '../../domain/types'

/**
 * Menor proposta, em centavos (espelha `MIN_OFFER_CENTS` do backend). Só
 * orienta o input — quem decide é o backend, que devolve `InvalidAmount`.
 */
export const MIN_OFFER_CENTS = 1_000_00

/** Intervalo do polling das propostas — a "notificação" da v1 (sem WebSocket). */
export const OFFERS_POLL_MS = 15_000

export const offersKey = (clubId: number) => ['offers', clubId] as const

/**
 * Propostas recebidas e enviadas pelo clube (issue #63). Polling em vez de
 * WebSocket: proposta nova não é urgente ao segundo, e o badge do cabeçalho
 * consome a mesma query. `staleTime: 0` faz a Dashboard buscar de novo ao
 * montar — quem abre a tela vê o estado atual, não o do último poll.
 */
export function useClubOffers(clubId: number) {
  return useQuery({
    queryKey: offersKey(clubId),
    queryFn: () => api.get<TransferOffer[]>(`/api/clubs/${clubId}/offers`),
    refetchInterval: OFFERS_POLL_MS,
    staleTime: 0,
    enabled: clubId > 0,
  })
}

/**
 * Reação comum a todo comando de proposta: o resultado de negócio vem no
 * payload do 200 (como na compra/venda), então sucesso e erro são tratados
 * aqui, e não pelo `onError` global.
 */
function useOfferResultHandler(clubId: number) {
  const qc = useQueryClient()
  return (
    result: TransferOfferResult,
    role: OfferRole,
    successMessage: (offer: TransferOffer) => string,
  ) => {
    qc.invalidateQueries({ queryKey: offersKey(clubId) })
    if (result.type === 'Success') {
      toast.success(successMessage(result.offer))
      // Aceite mexe em caixa e elenco; os demais comandos só na proposta —
      // recarregar o clube sempre é barato e evita um caso esquecido.
      qc.invalidateQueries({ queryKey: clubKey(clubId) })
    } else {
      toast.error(offerErrorMessage(result, role))
      if (result.type === 'Conflict') qc.invalidateQueries({ queryKey: clubKey(clubId) })
    }
  }
}

/** Fazer proposta por um jogador de outro técnico. */
export function useCreateOffer(myClubId: number) {
  const handle = useOfferResultHandler(myClubId)
  return useMutation({
    mutationFn: (body: CreateOfferRequest) => api.post<TransferOfferResult>('/api/offers', body),
    onSuccess: (result) => handle(result, 'buyer', (offer) => `Proposta enviada por ${offer.playerName}!`),
  })
}

export type OfferAction = 'accept' | 'reject' | 'withdraw'

/** Quem dispara cada comando — o vendedor aceita/recusa, o comprador retira. */
const ACTION_ROLE: Record<OfferAction, OfferRole> = {
  accept:   'seller',
  reject:   'seller',
  withdraw: 'buyer',
}

const ACTION_DONE: Record<OfferAction, (offer: TransferOffer) => string> = {
  accept:   (o) => `${o.playerName} vendido para o ${o.buyerClubName}!`,
  reject:   (o) => `Proposta do ${o.buyerClubName} recusada.`,
  withdraw: (o) => `Proposta por ${o.playerName} retirada.`,
}

/** Aceitar/recusar (vendedor) ou retirar (comprador) uma proposta. */
export function useOfferAction(myClubId: number) {
  const handle = useOfferResultHandler(myClubId)
  return useMutation({
    mutationFn: ({ offerId, action }: { offerId: number; action: OfferAction }) =>
      api.post<TransferOfferResult>(`/api/offers/${offerId}/${action}`),
    onSuccess: (result, { action }) => handle(result, ACTION_ROLE[action], ACTION_DONE[action]),
  })
}
