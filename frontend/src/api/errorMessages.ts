// Tradução de erros da API para mensagens amigáveis em pt-BR.
// Usado pelo tratamento global de erros do TanStack Query (toast).

import { ApiError } from './client'
import { formatMoney } from '../domain/formatters'
import type { TransferResult } from '../domain/types'

/** Mensagens por `code` estável do ErrorResponse (não pela mensagem livre). */
const MESSAGES: Record<string, string> = {
  INSUFFICIENT_FUNDS: 'Caixa insuficiente para a operação.',
  CLUB_NOT_FOUND: 'Clube não encontrado.',
  ROUND_NOT_FOUND: 'Nenhuma rodada disponível.',
  ROUND_ALREADY_FINISHED: 'Esta rodada já foi encerrada.',
  INCOMPLETE_LINEUP: 'Escalação incompleta — preencha os 11 titulares.',
  INVALID_LINEUP: 'Escalação inválida — jogadores fora do elenco.',
  NOT_FOUND: 'Recurso não encontrado.',
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
  }
}
