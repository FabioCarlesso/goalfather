import { describe, expect, it } from 'vitest'
import { contractOfferSummary, offerMeetingRefusal, refusalMessage, yearsRangeLabel } from './contractOffers'
import type { PlayerRefused } from './types'

const refusal: PlayerRefused = {
  type: 'PlayerRefused',
  playerId: 7,
  reason: 'WEAKER_CLUB',
  demandedSalary: 4_500_00,
  minYears: 2,
  maxYears: 4,
}

describe('contractOffers', () => {
  it('descreve a recusa com motivo, pedido e faixa de duração', () => {
    expect(refusalMessage(refusal)).toMatch(/muito mais fraco/)
    expect(refusalMessage(refusal)).toMatch(/2 a 4 temporadas/)
    expect(yearsRangeLabel(1, 1)).toBe('1 temporada')
  })

  it('a oferta que atende à recusa copia o pedido e traz a duração para a faixa', () => {
    expect(offerMeetingRefusal({ salary: 1_000_00, years: 5 }, refusal)).toEqual({ salary: 4_500_00, years: 4 })
    expect(offerMeetingRefusal({ salary: 9_000_00, years: 1, releaseClause: 1 }, refusal))
      .toEqual({ salary: 9_000_00, years: 2, releaseClause: 1 })
  })

  it('resume os termos', () => {
    expect(contractOfferSummary({ salary: 3_000_00, years: 3 })).toMatch(/3 temporadas/)
    expect(contractOfferSummary({ salary: 3_000_00, years: 1, releaseClause: 50_000_00 })).toMatch(/multa/)
  })
})
