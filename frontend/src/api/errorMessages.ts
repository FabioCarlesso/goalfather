// Tradução de erros da API para mensagens amigáveis em pt-BR.
// Usado pelo tratamento global de erros do TanStack Query (toast).

import { ApiError } from './client'
import { formatMoney, formatSeats } from '../domain/formatters'
import { MIN_STADIUM_EXPANSION_SEATS } from './queries/useExpandStadium'
import type { TransferOfferResult, TransferResult } from '../domain/types'
import { offerStatusLabel, expiryReasonLabel } from '../domain/offers'

/** Mensagens por `code` estável do ErrorResponse (não pela mensagem livre). */
const MESSAGES: Record<string, string> = {
  INSUFFICIENT_FUNDS: 'Caixa insuficiente para a operação.',
  CLUB_NOT_FOUND: 'Clube não encontrado.',
  ROUND_NOT_FOUND: 'Nenhuma rodada disponível.',
  ROUND_ALREADY_FINISHED: 'Esta rodada já foi encerrada.',
  INCOMPLETE_LINEUP: 'Escalação incompleta — preencha os 11 titulares.',
  INVALID_LINEUP: 'Escalação inválida — jogadores fora do elenco.',
  DUPLICATE_PLAYERS: 'Escalação inválida — o mesmo jogador está em mais de uma vaga.',
  INVALID_STADIUM_EXPANSION: `A ampliação mínima é de ${formatSeats(MIN_STADIUM_EXPANSION_SEATS)} assentos.`,
  // Lock otimista do clube (issue #89): nada foi aplicado.
  CLUB_WRITE_CONFLICT: 'O clube foi alterado por outra operação ao mesmo tempo. Confira os dados e tente de novo.',
  OFFER_NOT_FOUND: 'Proposta não encontrada.',
  FORBIDDEN: 'Você não é dono do clube desta operação.',
  NOT_FOUND: 'Recurso não encontrado.',
  MALFORMED_REQUEST: 'Requisição inválida — os dados enviados não puderam ser lidos.',
  INVALID_PARAMETER: 'Parâmetro inválido na requisição.',
  INTERNAL_ERROR: 'Erro no servidor. Tente novamente em instantes.',
}

/**
 * 409 do lock otimista do clube (issue #89): nada foi aplicado e o clube em
 * cache ficou velho — quem trata o erro deve recarregá-lo.
 */
export function isClubWriteConflict(error: unknown): boolean {
  return error instanceof ApiError && error.status === 409 && error.body?.code === 'CLUB_WRITE_CONFLICT'
}

/** Converte qualquer erro em uma mensagem legível para exibir ao usuário. */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    const code = error.body?.code
    if (code && MESSAGES[code]) return MESSAGES[code]
    if (error.body?.message) return error.body.message
    if (error.status >= 500) return 'Erro no servidor. Tente novamente em instantes.'
    return `Falha na requisição (HTTP ${error.status}).`
  }
  if (error instanceof Error) {
    // Falha de rede (fetch rejeitado) — backend fora do ar, por exemplo.
    return 'Não foi possível conectar ao servidor.'
  }
  return 'Ocorreu um erro inesperado.'
}

/**
 * Mensagem para as variantes de erro de `TransferResult` (compra/venda),
 * que chegam com HTTP 200 — o resultado de negócio vive no payload.
 */
export function transferErrorMessage(result: Exclude<TransferResult, { type: 'Success' }>): string {
  switch (result.type) {
    case 'InsufficientFunds':
      return `Caixa insuficiente: necessário ${formatMoney(result.required)}, disponível ${formatMoney(result.available)}.`
    case 'SquadFull':
      return `Elenco cheio — limite de ${result.maxSize} jogadores.`
    case 'PlayerNotAvailable':
      return `Jogador #${result.playerId} indisponível.`
    case 'Conflict':
      return 'O clube foi alterado por outra operação ao mesmo tempo — nada foi aplicado. Tente de novo.'
  }
}

/**
 * Mensagem para os erros de negócio das propostas entre técnicos (issue #63),
 * que chegam no 200 como variantes de `TransferOfferResult`. `switch`
 * exaustivo: uma variante nova no contrato quebra a compilação aqui.
 */
export function offerErrorMessage(result: Exclude<TransferOfferResult, { type: 'Success' }>): string {
  switch (result.type) {
    case 'InvalidAmount':
      return `A proposta mínima é de ${formatMoney(result.minimum)}.`
    case 'InsufficientFunds':
      return `Caixa insuficiente: necessário ${formatMoney(result.required)}, disponível ${formatMoney(result.available)}.`
    case 'SquadFull':
      return `Elenco do comprador cheio — limite de ${result.maxSize} jogadores.`
    case 'PlayerNotAvailable':
      return 'O jogador não está mais no elenco do vendedor.'
    case 'SellerNotHuman':
      return 'Clubes da IA não negociam — propostas são só entre técnicos.'
    case 'SellerSquadInvalid':
      return result.violation === 'NO_GOALKEEPER'
        ? 'O vendedor não pode ficar sem goleiro.'
        : `O vendedor não pode ficar com menos de ${result.minSquadSize} jogadores.`
    case 'OfferNotPending':
      return `Esta proposta já foi ${offerStatusLabel(result.status).toLowerCase()}.`
    case 'OfferExpired':
      return `Esta proposta expirou: ${expiryReasonLabel(result.reason)}.`
    case 'RoundInProgress':
      return `A rodada ${result.roundNumber} está em andamento — aceite depois que ela terminar.`
    case 'Conflict':
      return 'Um dos clubes foi alterado por outra operação ao mesmo tempo — nada foi aplicado. Tente de novo.'
  }
}
