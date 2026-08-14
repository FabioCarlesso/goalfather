import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../client'
import { clubKey } from './useClub'
import type { Club } from '../../domain/types'

/**
 * Faixa de preço praticável, em centavos — espelha `TICKET_PRICE_RANGE` de
 * `domain/rules/FinanceRules.kt` (mesmo padrão de `COST_PER_SEAT_CENTS` e
 * `MEDICAL_COST_CENTS`). Serve para o formulário validar antes de mandar; a
 * palavra final continua sendo do backend, que responde 400
 * `TICKET_PRICE_OUT_OF_RANGE`.
 */
export const MIN_TICKET_PRICE_CENTS = 10_00
export const MAX_TICKET_PRICE_CENTS = 200_00

/**
 * Preço do ingresso do estádio (issue #59). Só registra a decisão — o efeito
 * (público e receita) aparece na bilheteria da próxima rodada em que o clube
 * for mandante e chega à UI pelo `RoundFinished`.
 */
export function useSetTicketPrice(clubId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (ticketPriceCents: number) =>
      api.put<Club>(`/api/clubs/${clubId}/ticket-price`, { ticketPriceCents }),
    // O endpoint devolve o clube já atualizado — sem refetch.
    onSuccess: (club) => qc.setQueryData(clubKey(clubId), club),
    meta: { successMessage: 'Preço do ingresso atualizado!' },
  })
}
