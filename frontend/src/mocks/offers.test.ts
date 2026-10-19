import { describe, expect, it } from 'vitest'
import {
  afterRound,
  closedOfferResult,
  offerBlocker,
  squadViolationAfterSale,
  MIN_OFFER_CENTS,
  type OfferClub,
} from './offers'
import type { Player, Position, TransferOffer } from '../domain/types'

// Espelho das regras de TransferOfferRules.kt (issue #63) — os mesmos casos do
// TransferOfferRulesTest, para o mock não divergir do backend.

const player = (id: number, position: Position = 'MF'): Player => ({
  id, name: `P${id}`, position, overall: 75, pace: 75, shooting: 75, passing: 75, defending: 75,
  stamina: 100, salary: 10_000_00, age: 25, goals: 0, yellowCards: 0, redCards: 0,
  availability: { type: 'Available' }, star: false,
})

/** Goleiro (id 1) + linha. */
const squad = (size: number, goalkeepers = 1): Player[] =>
  Array.from({ length: size }, (_, i) => player(i + 1, i < goalkeepers ? 'GK' : 'MF'))

describe('regras de proposta do mock (issue #63)', () => {
  it('vendedor não fica com menos de 11 nem sem o último goleiro', () => {
    expect(squadViolationAfterSale(squad(12), 5)).toBeNull()
    expect(squadViolationAfterSale(squad(11), 5)).toBe('TOO_FEW_PLAYERS')
    expect(squadViolationAfterSale(squad(14), 1)).toBe('NO_GOALKEEPER')
    expect(squadViolationAfterSale(squad(14, 0), 3)).toBeNull()
  })

  const buyer: OfferClub = { id: 1, cash: 1_000_000_00, squad: squad(11), ownerId: 10 }
  const seller: OfferClub = { id: 2, cash: 0, squad: squad(13), ownerId: 20 }

  it('segue a ordem de checagem do backend', () => {
    expect(offerBlocker(buyer, seller, 5, 500_000_00)).toBeNull()
    expect(offerBlocker(buyer, seller, 5, MIN_OFFER_CENTS - 1)).toEqual({ type: 'InvalidAmount', minimum: MIN_OFFER_CENTS })
    expect(offerBlocker(buyer, seller, 999, 500_000_00)).toEqual({ type: 'PlayerNotAvailable', playerId: 999 })
    expect(offerBlocker(buyer, { ...seller, ownerId: null }, 5, 500_000_00)).toEqual({ type: 'SellerNotHuman', clubId: 2 })
    expect(offerBlocker({ ...buyer, cash: 1 }, seller, 5, 500_000_00))
      .toEqual({ type: 'InsufficientFunds', available: 1, required: 500_000_00 })
    expect(offerBlocker(buyer, { ...seller, squad: squad(11) }, 5, 500_000_00))
      .toEqual({ type: 'SellerSquadInvalid', violation: 'TOO_FEW_PLAYERS', minSquadSize: 11 })
  })

  const offer: TransferOffer = {
    id: 7, buyerClubId: 1, buyerClubName: 'A', sellerClubId: 2, sellerClubName: 'B',
    playerId: 5, playerName: 'P5', amount: 500_000_00, roundsLeft: 2, status: { type: 'Pending' },
  }

  it('o prazo anda uma rodada e a última expira por DEADLINE', () => {
    expect(afterRound(offer)).toMatchObject({ roundsLeft: 1, status: { type: 'Pending' } })
    expect(afterRound(afterRound(offer))).toMatchObject({ roundsLeft: 0, status: { type: 'Expired', reason: 'DEADLINE' } })
    const accepted: TransferOffer = { ...offer, status: { type: 'Accepted' } }
    expect(afterRound(accepted)).toBe(accepted)
  })

  it('proposta resolvida explica por que não aceita mais comando', () => {
    expect(closedOfferResult({ ...offer, status: { type: 'Expired', reason: 'DEADLINE' } }))
      .toEqual({ type: 'OfferExpired', offerId: 7, reason: 'DEADLINE' })
    expect(closedOfferResult({ ...offer, status: { type: 'Rejected' } }))
      .toEqual({ type: 'OfferNotPending', offerId: 7, status: { type: 'Rejected' } })
  })
})
