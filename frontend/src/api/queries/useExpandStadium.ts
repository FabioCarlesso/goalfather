import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../client'
import { clubKey } from './useClub'
import type { Club } from '../../domain/types'
import type { components } from '../generated'

type ExpandStadiumRequest = components['schemas']['ExpandStadiumRequest']

/**
 * Custo por assento (centavos) e menor obra aceita — espelham
 * `STADIUM_SEAT_COST_CENTS` e `MIN_STADIUM_EXPANSION_SEATS` de
 * `domain/rules/StadiumRules.kt` (issue #79), no mesmo padrão de
 * `MIN_TICKET_PRICE_CENTS`. Um lugar só no frontend: o formulário e a
 * mensagem de erro leem daqui. A palavra final é do backend, que responde 400
 * `INVALID_STADIUM_EXPANSION`.
 */
export const COST_PER_SEAT_CENTS = 100_00
export const MIN_STADIUM_EXPANSION_SEATS = 1_000

export function useExpandStadium(clubId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: ExpandStadiumRequest) =>
      api.post<Club>(`/api/clubs/${clubId}/stadium/expand`, body),
    // Atualiza o clube no cache na hora — capacidade/caixa mudam sem refetch.
    onSuccess: (club) => qc.setQueryData(clubKey(clubId), club),
    meta: { successMessage: 'Estádio ampliado!' },
  })
}
