// Testes do mock de bilheteria (issues #4 e #59) — garantem que a engine de
// mock espelha FinanceRules.kt do backend até o backend assumir de vez. São os
// mesmos invariantes de FinanceRulesTest.kt, na forma TS.

import { describe, expect, it } from 'vitest'
import {
  attendanceOf,
  attendanceRate,
  fairTicketPriceCents,
  isTicketPriceAllowed,
  ticketPriceDemandFactor,
  ticketRevenueOf,
  DEFAULT_TICKET_PRICE_CENTS,
  MAX_TICKET_PRICE_CENTS,
  MIN_TICKET_PRICE_CENTS,
} from './engine'

const strengths = [50, 60, 70, 80, 90, 100, 110]

const prices: number[] = []
for (let p = MIN_TICKET_PRICE_CENTS; p <= MAX_TICKET_PRICE_CENTS; p += 100) prices.push(p)

const bestPriceFor = (strength: number): number =>
  prices.reduce((best, p) =>
    ticketRevenueOf(20_000, strength, p) > ticketRevenueOf(20_000, strength, best) ? p : best,
  )

describe('curva de demanda da bilheteria (issue #59)', () => {
  it('preço maior nunca aumenta a ocupação', () => {
    for (const strength of strengths) {
      let previous = attendanceRate(strength, prices[0]!)
      for (const price of prices.slice(1)) {
        const rate = attendanceRate(strength, price)
        expect(rate, `força ${strength}, preço ${price}`).toBeLessThanOrEqual(previous)
        previous = rate
      }
    }
  })

  it('ocupação fica sempre entre zero e o estádio cheio', () => {
    for (const strength of strengths) {
      for (const price of prices) {
        const rate = attendanceRate(strength, price)
        expect(rate).toBeGreaterThan(0)
        expect(rate).toBeLessThanOrEqual(1)
      }
    }
  })

  it('receita tem máximo em preço intermediário', () => {
    for (const strength of strengths) {
      const best = bestPriceFor(strength)
      expect(best, `força ${strength}`).toBeGreaterThan(MIN_TICKET_PRICE_CENTS)
      expect(best, `força ${strength}`).toBeLessThan(MAX_TICKET_PRICE_CENTS)
      expect(ticketRevenueOf(20_000, strength, best)).toBeGreaterThan(
        ticketRevenueOf(20_000, strength, MAX_TICKET_PRICE_CENTS),
      )
    }
  })

  it('time mais forte suporta ingresso mais caro', () => {
    expect(bestPriceFor(100)).toBeGreaterThan(bestPriceFor(60))
  })

  it('multiplicador vale 1 no preço justo e é contínuo', () => {
    for (const strength of strengths) {
      const fair = fairTicketPriceCents(strength)
      expect(ticketPriceDemandFactor(fair, strength)).toBeCloseTo(1, 9)
      expect(ticketPriceDemandFactor(fair - 1, strength)).toBeGreaterThan(1)
      expect(ticketPriceDemandFactor(fair + 1, strength)).toBeLessThan(1)
    }
  })

  it('receita é público × preço', () => {
    for (const strength of strengths) {
      for (const price of [MIN_TICKET_PRICE_CENTS, DEFAULT_TICKET_PRICE_CENTS, MAX_TICKET_PRICE_CENTS]) {
        expect(ticketRevenueOf(20_000, strength, price)).toBe(
          attendanceOf(20_000, strength, price) * price,
        )
      }
    }
  })

  it('faixa aceita as bordas e recusa o que passa delas', () => {
    expect(isTicketPriceAllowed(MIN_TICKET_PRICE_CENTS)).toBe(true)
    expect(isTicketPriceAllowed(MAX_TICKET_PRICE_CENTS)).toBe(true)
    expect(isTicketPriceAllowed(DEFAULT_TICKET_PRICE_CENTS)).toBe(true)
    expect(isTicketPriceAllowed(MIN_TICKET_PRICE_CENTS - 1)).toBe(false)
    expect(isTicketPriceAllowed(MAX_TICKET_PRICE_CENTS + 1)).toBe(false)
    expect(isTicketPriceAllowed(0)).toBe(false)
    // Centavo fracionário não é preço válido — o contrato fala em inteiros.
    expect(isTicketPriceAllowed(50_00.5)).toBe(false)
  })
})
