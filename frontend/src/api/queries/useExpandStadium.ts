import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../client'
import { clubKey } from './useClub'
import type { Club } from '../../domain/types'
import type { components } from '../generated'

type ExpandStadiumRequest = components['schemas']['ExpandStadiumRequest']

/** Custo por assento, em centavos (espelha o controller do backend: 100_00). */
export const COST_PER_SEAT_CENTS = 100_00

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
