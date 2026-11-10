import { describe, expect, it } from 'vitest'
import { COST_PER_SEAT_CENTS } from './useExpandStadium'
import { MIN_OFFER_CENTS } from './useOffers'
import { MEDICAL_COST_CENTS } from './useTreatSquad'
import { MIN_OFFER_CENTS as MOCK_MIN_OFFER_CENTS } from '../../mocks/offers'
import { MEDICAL_DEPARTMENT_COST_CENTS, STADIUM_SEAT_COST_CENTS } from '../../mocks/engine'

/**
 * Os valores que a UI exibe nos formulários espelham constantes do backend,
 * e a engine de mock espelha as mesmas. Na conversão para USD (issue #122) os
 * mocks foram reescalados e estas três ficaram em reais — a tela anunciava
 * US$ 100 por assento enquanto o backend cobrava US$ 20 (revisão da PR #149).
 * Este teste trava a divergência entre os dois espelhos.
 */
describe('constantes de dinheiro da UI', () => {
  it('batem com as da engine de mock (e, por ela, com o backend)', () => {
    expect(COST_PER_SEAT_CENTS).toBe(STADIUM_SEAT_COST_CENTS)
    expect(MIN_OFFER_CENTS).toBe(MOCK_MIN_OFFER_CENTS)
    expect(MEDICAL_COST_CENTS).toBe(MEDICAL_DEPARTMENT_COST_CENTS)
  })
})
