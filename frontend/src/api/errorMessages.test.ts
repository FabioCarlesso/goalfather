import { describe, expect, it } from 'vitest'
import { errorMessage, isClubWriteConflict, offerErrorMessage, transferErrorMessage } from './errorMessages'
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

  it('reconhece só o 409 de lock otimista do clube como conflito de escrita', () => {
    expect(isClubWriteConflict(new ApiError(409, { code: 'CLUB_WRITE_CONFLICT', message: 'x' }))).toBe(true)
    expect(isClubWriteConflict(new ApiError(409, { code: 'CLUB_ALREADY_CLAIMED', message: 'x' }))).toBe(false)
    expect(isClubWriteConflict(new Error('rede'))).toBe(false)
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

  it('explica os erros de negócio das propostas entre técnicos (issue #63)', () => {
    expect(offerErrorMessage({ type: 'SellerSquadInvalid', violation: 'NO_GOALKEEPER', minSquadSize: 11 }, 'buyer'))
      .toBe('O vendedor não pode ficar sem goleiro.')
    expect(offerErrorMessage({ type: 'SellerSquadInvalid', violation: 'TOO_FEW_PLAYERS', minSquadSize: 11 }, 'buyer'))
      .toBe('O vendedor não pode ficar com menos de 11 jogadores.')
    expect(offerErrorMessage({ type: 'OfferExpired', offerId: 1, reason: 'PLAYER_TRANSFERRED' }, 'seller'))
      .toBe('Esta proposta expirou: o jogador deixou o clube vendedor.')
    expect(offerErrorMessage({ type: 'OfferNotPending', offerId: 1, status: { type: 'Rejected' } }, 'buyer'))
      .toBe('Esta proposta já foi recusada.')
    expect(offerErrorMessage({ type: 'OfferAlreadyPending', offerId: 3 }, 'buyer'))
      .toBe('Você já tem uma proposta pendente por este jogador — retire-a para mudar o valor.')
    expect(offerErrorMessage({ type: 'RoundInProgress', roundNumber: 4 }, 'seller'))
      .toBe('A rodada 4 está em andamento — aceite depois que ela terminar.')
    expect(errorMessage(new ApiError(404, { code: 'OFFER_NOT_FOUND', message: 'x' })))
      .toBe('Proposta não encontrada.')
  })

  it('explica o comprador que perdeu o técnico (revisão do PR #94)', () => {
    expect(offerErrorMessage({ type: 'BuyerNotHuman', clubId: 2 }, 'seller'))
      .toBe('O técnico que fez esta proposta deixou o clube — ela não vale mais.')
    expect(offerErrorMessage({ type: 'OfferExpired', offerId: 1, reason: 'MANAGER_LEFT' }, 'buyer'))
      .toBe('Esta proposta expirou: o técnico de um dos clubes deixou o cargo.')
  })

  it('fala com o vendedor na segunda pessoa quando é ele quem aceita (revisão do PR #94)', () => {
    expect(offerErrorMessage({ type: 'SellerSquadInvalid', violation: 'TOO_FEW_PLAYERS', minSquadSize: 11 }, 'seller'))
      .toBe('Seu elenco não pode ficar com menos de 11 jogadores.')
    expect(offerErrorMessage({ type: 'SellerSquadInvalid', violation: 'NO_GOALKEEPER', minSquadSize: 11 }, 'seller'))
      .toBe('Você não pode vender seu último goleiro.')
    expect(offerErrorMessage({ type: 'InsufficientFunds', available: 100, required: 500 }, 'seller'))
      .toMatch(/^O comprador não tem mais caixa/)
  })
})
