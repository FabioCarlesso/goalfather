import { afterEach, describe, expect, it } from 'vitest'
import { clearToken, setToken } from './token'
import { wsUrl } from './wsUrl'

describe('wsUrl', () => {
  afterEach(() => clearToken())

  const base = `ws://${window.location.host}/ws/round/3`

  it('sem token nem velocidade: URL limpa', () => {
    expect(wsUrl('/ws/round/3')).toBe(base)
  })

  it('token e velocidade viajam na query (issue #115)', () => {
    setToken('a.b+c')
    expect(wsUrl('/ws/round/3', { speed: 'INSTANT' })).toBe(`${base}?token=a.b%2Bc&speed=INSTANT`)
  })

  it('velocidade sem token', () => {
    expect(wsUrl('/ws/round/3', { speed: 'SLOW' })).toBe(`${base}?speed=SLOW`)
  })
})
