// Apresentação das propostas entre técnicos (issue #63). Só rótulos e
// filtros de exibição — as regras (prazo, elenco mínimo, caixa) são do backend.

import type { ExpiryReason, OfferStatus, TransferOffer } from './types'

/** Rótulo do estado da proposta — `switch` exaustivo sobre o `oneOf` do contrato. */
export function offerStatusLabel(status: OfferStatus): string {
  switch (status.type) {
    case 'Pending':   return 'Pendente'
    case 'Accepted':  return 'Aceita'
    case 'Rejected':  return 'Recusada'
    case 'Withdrawn': return 'Retirada'
    case 'Expired':   return 'Expirada'
  }
}

export function expiryReasonLabel(reason: ExpiryReason): string {
  switch (reason) {
    case 'DEADLINE':           return 'o prazo acabou sem resposta'
    case 'PLAYER_TRANSFERRED': return 'o jogador foi vendido a outro clube'
  }
}

export const isPendingOffer = (offer: TransferOffer): boolean => offer.status.type === 'Pending'

/** Propostas que o clube RECEBEU (ele é o vendedor). */
export const receivedOffers = (offers: TransferOffer[], clubId: number): TransferOffer[] =>
  offers.filter((o) => o.sellerClubId === clubId)

/** Propostas que o clube ENVIOU (ele é o comprador). */
export const sentOffers = (offers: TransferOffer[], clubId: number): TransferOffer[] =>
  offers.filter((o) => o.buyerClubId === clubId)

/** Quantas propostas recebidas aguardam resposta — o número do badge. */
export const pendingReceivedCount = (offers: TransferOffer[], clubId: number): number =>
  receivedOffers(offers, clubId).filter(isPendingOffer).length
