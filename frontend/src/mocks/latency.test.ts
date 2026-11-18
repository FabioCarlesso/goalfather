import { describe, expect, it } from 'vitest'
import { parseMockLatency } from './latency'

describe('parseMockLatency', () => {
  it('usa o padrão quando a variável está ausente ou vazia', () => {
    expect(parseMockLatency(undefined)).toBe(120)
    expect(parseMockLatency('')).toBe(120)
    expect(parseMockLatency('  ')).toBe(120)
  })

  it('aceita zero e valores positivos', () => {
    expect(parseMockLatency('0')).toBe(0)
    expect(parseMockLatency('300')).toBe(300)
  })

  it('cai no padrão com valor inválido ou negativo', () => {
    expect(parseMockLatency('abc')).toBe(120)
    expect(parseMockLatency('-5')).toBe(120)
  })
})
