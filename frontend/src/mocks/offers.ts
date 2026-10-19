// Propostas entre técnicos no mock (issue #63) — espelho de
// domain/rules/TransferOfferRules.kt e das transições de TransferOffer.kt.
// Descartável junto do resto do mock quando o backend for o único alvo.

import type { Player, TransferOffer, TransferOfferResult } from '../domain/types'

export const OFFER_DEADLINE_ROUNDS = 3
export const MIN_OFFER_CENTS = 1_000_00
export const MIN_SQUAD_SIZE = 11
export const MAX_SQUAD_SIZE = 25

type Rejection = Exclude<TransferOfferResult, { type: 'Success' }>

/** O mínimo de clube que as regras enxergam. */
export interface OfferClub {
  id: number
  cash: number
  squad: Player[]
  ownerId?: number | null
}

/** Espelha `squadViolationAfterSale`: o goleiro só conta quando é ELE que sai. */
export function squadViolationAfterSale(squad: Player[], playerId: number): 'TOO_FEW_PLAYERS' | 'NO_GOALKEEPER' | null {
  const sold = squad.find((p) => p.id === playerId)
  const remaining = squad.filter((p) => p.id !== playerId)
  if (remaining.length < MIN_SQUAD_SIZE) return 'TOO_FEW_PLAYERS'
  if (sold?.position === 'GK' && !remaining.some((p) => p.position === 'GK')) return 'NO_GOALKEEPER'
  return null
}

/** Espelha `offerBlocker`, na mesma ordem de checagem. */
export function offerBlocker(buyer: OfferClub, seller: OfferClub, playerId: number, amount: number): Rejection | null {
  if (amount < MIN_OFFER_CENTS) return { type: 'InvalidAmount', minimum: MIN_OFFER_CENTS }
  if (buyer.id === seller.id || !seller.squad.some((p) => p.id === playerId)) {
    return { type: 'PlayerNotAvailable', playerId }
  }
  if (seller.ownerId == null) return { type: 'SellerNotHuman', clubId: seller.id }
  if (buyer.cash < amount) return { type: 'InsufficientFunds', available: buyer.cash, required: amount }
  if (buyer.squad.length >= MAX_SQUAD_SIZE) return { type: 'SquadFull', maxSize: MAX_SQUAD_SIZE }
  const violation = squadViolationAfterSale(seller.squad, playerId)
  if (violation) return { type: 'SellerSquadInvalid', violation, minSquadSize: MIN_SQUAD_SIZE }
  return null
}

/** Espelha `TransferOffer.afterRound`: uma rodada a menos; a última expira. */
export function afterRound(offer: TransferOffer): TransferOffer {
  if (offer.status.type !== 'Pending') return offer
  return offer.roundsLeft <= 1
    ? { ...offer, roundsLeft: 0, status: { type: 'Expired', reason: 'DEADLINE' } }
    : { ...offer, roundsLeft: offer.roundsLeft - 1 }
}

/** Erro de quem age sobre proposta que já não está pendente (espelha `closed`). */
export function closedOfferResult(offer: TransferOffer): Rejection {
  return offer.status.type === 'Expired'
    ? { type: 'OfferExpired', offerId: offer.id, reason: offer.status.reason }
    : { type: 'OfferNotPending', offerId: offer.id, status: offer.status }
}
