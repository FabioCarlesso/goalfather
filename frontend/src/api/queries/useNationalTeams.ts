import { useQuery } from '@tanstack/react-query'
import { api } from '../client'
import type { ClubCallUps, NationalTeamsOverview } from '../../domain/types'

/** Prefixo comum: a `RoundPage` invalida tudo de seleções no `RoundFinished`. */
export const nationalTeamsRootKey = ['national-teams'] as const
export const nationalTeamsKey = [...nationalTeamsRootKey, 'overview'] as const
export const clubCallUpsKey = (clubId: number) => [...nationalTeamsRootKey, 'call-ups', clubId] as const

/**
 * Painel das seleções (issue #134): datas FIFA, últimos resultados,
 * Eliminatórias e ranking — tudo calculado pelo backend.
 */
export function useNationalTeams() {
  return useQuery({
    queryKey: nationalTeamsKey,
    queryFn: () => api.get<NationalTeamsOverview>('/api/national-teams'),
  })
}

/**
 * Convocados do clube na data FIFA corrente ou na próxima (issue #134). Antes
 * da janela é a prévia (`window.status === 'UPCOMING'`).
 */
export function useClubCallUps(clubId: number) {
  return useQuery({
    queryKey: clubCallUpsKey(clubId),
    queryFn: () => api.get<ClubCallUps>(`/api/clubs/${clubId}/call-ups`),
  })
}
