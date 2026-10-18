import { describe, expect, it } from 'vitest'
import { errorMessage } from './errorMessages'
import { ApiError } from './client'

describe('errorMessage', () => {
  it('traduz códigos conhecidos para pt-BR', () => {
    expect(errorMessage(new ApiError(402, { code: 'INSUFFICIENT_FUNDS', message: 'x' })))
      .toBe('Caixa insuficiente para a operação.')
    expect(errorMessage(new ApiError(404, { code: 'CLUB_NOT_FOUND', message: 'x' })))
      .toBe('Clube não encontrado.')
    expect(errorMessage(new ApiError(400, { code: 'INVALID_STADIUM_EXPANSION', message: 'x' })))
      .toBe('A ampliação mínima é de 1.000 assentos.')
    expect(errorMessage(new ApiError(400, { code: 'DUPLICATE_PLAYERS', message: 'x' })))
      .toBe('Escalação inválida — o mesmo jogador está em mais de uma vaga.')
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
