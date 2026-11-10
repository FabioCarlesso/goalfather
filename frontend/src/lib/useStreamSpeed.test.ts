import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, renderHook } from '@testing-library/react'
import { STREAM_SPEED_KEY, readStreamSpeed, useStreamSpeed } from './useStreamSpeed'

describe('useStreamSpeed', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    localStorage.removeItem(STREAM_SPEED_KEY)
  })

  it('começa em NORMAL e lembra a escolha', () => {
    const { result } = renderHook(() => useStreamSpeed())
    expect(result.current.speed).toBe('NORMAL')

    act(() => result.current.setSpeed('FAST'))
    expect(result.current.speed).toBe('FAST')
    expect(localStorage.getItem(STREAM_SPEED_KEY)).toBe('FAST')

    // Uma "nova sessão" lê o valor salvo.
    expect(renderHook(() => useStreamSpeed()).result.current.speed).toBe('FAST')
  })

  it('valor salvo inválido cai em NORMAL', () => {
    localStorage.setItem(STREAM_SPEED_KEY, 'TURBO')
    expect(readStreamSpeed()).toBe('NORMAL')
  })

  it('sem localStorage, funciona só em memória', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('bloqueado')
    })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('bloqueado')
    })
    const { result } = renderHook(() => useStreamSpeed())
    expect(result.current.speed).toBe('NORMAL')
    act(() => result.current.setSpeed('SLOW'))
    expect(result.current.speed).toBe('SLOW')
  })
})
