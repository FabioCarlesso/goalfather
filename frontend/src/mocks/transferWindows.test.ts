import { describe, expect, it } from 'vitest'
import { registrationBlocker, windowBlocker, windowStatus } from './transferWindows'
import { transferWindowLabel } from '../domain/transferWindow'

// Espelho do TransferWindowRulesTest.kt (issue #137), sobre as janelas do mundo do mock.
const BRA = { code: 'BRA', confederation: 'CONMEBOL' as const }

describe('janelas de transferência do mock (issue #137)', () => {
  it('na data inicial o Brasil está aberto e a Inglaterra fechada', () => {
    expect(windowStatus('BRA', '2026-01-04')).toEqual({ type: 'Open', closes: '2026-03-03', scope: 'INTERNATIONAL' })
    expect(windowStatus('ENG', '2026-01-04')).toEqual({ type: 'Closed', nextOpens: '2026-06-15' })
  })

  it('bordas: a doméstica começa no dia seguinte e depois fecha', () => {
    expect(windowStatus('BRA', '2026-03-04')).toEqual({ type: 'Open', closes: '2026-03-27', scope: 'DOMESTIC_ONLY' })
    expect(windowStatus('BRA', '2026-03-28')).toEqual({ type: 'Closed', nextOpens: '2026-07-20' })
  })

  it('ano sem dado repete o último ano conhecido', () => {
    expect(windowStatus('ENG', '2029-07-01')).toEqual({ type: 'Open', closes: '2029-09-01', scope: 'INTERNATIONAL' })
  })

  it('federação sem janela fica fechada sem data', () => {
    expect(windowStatus('ARG', '2026-01-10')).toEqual({ type: 'Closed' })
  })

  it('só doméstica recusa origem estrangeira e aceita a do país e a do mercado', () => {
    expect(windowBlocker('BRA', 'ENG', '2026-03-10')).toEqual({ type: 'TransferWindowClosed', nextOpens: '2026-07-20' })
    expect(windowBlocker('BRA', 'BRA', '2026-03-10')).toBeNull()
    expect(windowBlocker('BRA', null, '2026-03-10')).toBeNull()
  })

  it('limite: um registro por janela e dois por temporada', () => {
    expect(registrationBlocker(BRA, null, '2026-02-01', 7, ['2026-01-10']))
      .toEqual({ type: 'TransferLimitReached', playerId: 7, limit: 'ONE_PER_WINDOW' })
    expect(registrationBlocker(BRA, null, '2026-07-25', 7, ['2026-01-10', '2026-03-10']))
      .toEqual({ type: 'TransferLimitReached', playerId: 7, limit: 'TWO_PER_SEASON' })
    expect(registrationBlocker(BRA, null, '2027-01-10', 7, ['2026-01-10', '2026-03-10'])).toBeNull()
  })

  it('rótulos do selo', () => {
    expect(transferWindowLabel({ type: 'Open', closes: '2026-03-03', scope: 'INTERNATIONAL' })).toBe('Janela aberta até 03/03/2026')
    expect(transferWindowLabel({ type: 'Open', closes: '2026-03-27', scope: 'DOMESTIC_ONLY' })).toBe('Janela só doméstica até 27/03/2026')
    expect(transferWindowLabel({ type: 'Closed', nextOpens: '2026-06-15' })).toBe('Mercado fechado · abre em 15/06/2026')
    expect(transferWindowLabel({ type: 'Closed' })).toBe('Mercado fechado · sem janela prevista')
  })
})
