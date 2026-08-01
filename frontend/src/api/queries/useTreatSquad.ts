import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../client'
import { clubKey } from './useClub'
import type { Club } from '../../domain/types'

/** Custo de uma sessão do departamento médico, em centavos (espelha o backend). */
export const MEDICAL_COST_CENTS = 30_000_00

/** Departamento médico (issue #54): recupera stamina e encurta lesões. */
export function useTreatSquad(clubId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => api.post<Club>(`/api/clubs/${clubId}/medical`, {}),
    // O endpoint devolve o clube já atualizado — caixa e elenco mudam sem refetch.
    onSuccess: (club) => qc.setQueryData(clubKey(clubId), club),
    meta: { successMessage: 'Elenco tratado no departamento médico!' },
  })
}
