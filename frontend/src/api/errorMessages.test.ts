import { describe, expect, it } from 'vitest'
import { errorMessage } from './errorMessages'
import { ApiError } from './client'

describe('errorMessage', () => {
  it('traduz códigos conhecidos para pt-BR', () => {
    expect(errorMessage(new ApiError(402, { code: 'INSUFFICIENT_FUNDS', message: 'x' })))
      .toBe('Caixa insuficiente para a operação.')
    expect(errorMessage(new ApiError(404, { code: 'CLUB_NOT_FOUND', message: 'x' })))
      .toBe('Clube não encontrado.')
  })

  it('usa a mensagem do corpo para códigos desconhecidos', () => {
    expect(errorMessage(new ApiError(400, { code: 'WEIRD', message: 'Detalhe específico' })))
      .toBe('Detalhe específico')
  })

  it('mensagem genérica para 5xx sem corpo', () => {
    expect(errorMessage(new ApiError(500, null))).toMatch(/servidor/i)
  })

  it('falha de rede vira mensagem de conexão', () => {
    expect(errorMessage(new Error('Failed to fetch'))).toMatch(/conectar/i)
  })

  it('fallback para valores não-Error', () => {
    expect(errorMessage('algo')).toBe('Ocorreu um erro inesperado.')
  })
})
