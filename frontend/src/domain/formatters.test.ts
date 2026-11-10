import { describe, expect, it } from 'vitest'
import { formatMoney, formatSeats } from './formatters'

describe('formatMoney', () => {
  it('converte centavos de USD', () => {
    expect(formatMoney(10_000)).toBe('US$ 100')
    expect(formatMoney(800_000_00)).toBe('US$ 800.000')
  })

  it('trata zero', () => {
    expect(formatMoney(0)).toBe('US$ 0')
  })
})

describe('formatSeats', () => {
  it('aplica separador de milhar PT-BR', () => {
    expect(formatSeats(15_000)).toBe('15.000')
    expect(formatSeats(1_234_567)).toBe('1.234.567')
  })

  it('mantem numeros pequenos como esta', () => {
    expect(formatSeats(42)).toBe('42')
  })
})
