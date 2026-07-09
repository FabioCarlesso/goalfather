import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  auth,
  markRoundReady,
  readinessStatus,
  resetRoundReadiness,
  READINESS_TIMEOUT_MS,
  type MockUser,
} from './seed'

// Cobre a paridade do escape hatch por timeout (issue #45) no mock: o
// cronômetro do `readinessStatus` deve espelhar `buildStatus` do backend —
// só arma com pendentes E ao menos um pronto, e nunca herda o timeout de uma
// rodada anterior de mesmo número (rollover de temporada reusa a rodada 1).

const ana: MockUser = { id: 1, username: 'ana', password: 'x', clubId: 1 }
const bruno: MockUser = { id: 2, username: 'bruno', password: 'x', clubId: 2 }

describe('readinessStatus — escape hatch por timeout (issue #45)', () => {
  beforeEach(() => {
    auth.users.push(ana, bruno)
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-07-09T12:00:00Z'))
  })

  afterEach(() => {
    vi.useRealTimers()
    resetRoundReadiness(1)
    auth.users.length = 0
  })

  it('sem ninguém pronto, o cronômetro não corre', () => {
    const status = readinessStatus(1)
    expect(status.secondsRemaining).toBeNull()
    expect(status.timedOut).toBe(false)
  })

  it('com um pronto e outro pendente, expõe secondsRemaining e não está timedOut', () => {
    markRoundReady(1, ana.id)
    vi.advanceTimersByTime(READINESS_TIMEOUT_MS - 5_000)

    const status = readinessStatus(1)
    expect(status.readyCount).toBe(1)
    expect(status.secondsRemaining).toBe(5)
    expect(status.timedOut).toBe(false)
  })

  it('ao expirar o timeout com pendente, marca timedOut', () => {
    markRoundReady(1, ana.id)
    vi.advanceTimersByTime(READINESS_TIMEOUT_MS + 1_000)

    const status = readinessStatus(1)
    expect(status.timedOut).toBe(true)
  })

  it('após reset, uma rodada de mesmo número não herda o timeout (rollover de temporada)', () => {
    // Rodada 1 da temporada 1: alguém pronto, timeout estoura, rodada joga.
    markRoundReady(1, ana.id)
    vi.advanceTimersByTime(READINESS_TIMEOUT_MS + 1_000)
    expect(readinessStatus(1).timedOut).toBe(true)

    // Fim da rodada → reset. Temporada 2 volta à rodada 1, ninguém pronto.
    resetRoundReadiness(1)

    const fresh = readinessStatus(1)
    expect(fresh.secondsRemaining).toBeNull()
    expect(fresh.timedOut).toBe(false)
  })
})
