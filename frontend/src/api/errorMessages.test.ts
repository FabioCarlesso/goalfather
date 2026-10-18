import { describe, expect, it } from 'vitest'
import { errorMessage, transferErrorMessage } from './errorMessages'
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

  it('traduz os códigos genéricos de erro de entrada e de servidor (issue #81)', () => {
    expect(errorMessage(new ApiError(400, { code: 'MALFORMED_REQUEST', message: 'x' })))
      .toBe('Requisição inválida — os dados enviados não puderam ser lidos.')
    expect(errorMessage(new ApiError(400, { code: 'INVALID_PARAMETER', message: 'x' })))
      .toBe('Parâmetro inválido na requisição.')
    expect(errorMessage(new ApiError(500, { code: 'INTERNAL_ERROR', message: 'x' })))
      .toBe('Erro no servidor. Tente novamente em instantes.')
  })

  it('traduz o conflito de lock otimista do clube (issue #89)', () => {
    expect(errorMessage(new ApiError(409, { code: 'CLUB_WRITE_CONFLICT', message: 'x' })))
      .toBe('O clube foi alterado por outra operação ao mesmo tempo. Confira os dados e tente de novo.')
    expect(transferErrorMessage({ type: 'Conflict', clubId: 1 }))
      .toBe('O clube foi alterado por outra operação ao mesmo tempo — nada foi aplicado. Tente de novo.')
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
