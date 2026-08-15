import { useQuery } from '@tanstack/react-query'
import { api, ApiError } from '../client'
import type { ClubCareer, SeasonRecord } from '../../domain/types'

export const seasonHistoryKey = ['seasonHistory'] as const
export const seasonRecordKey = (season: number) => ['seasonRecord', season] as const
export const clubCareerKey = (clubId: number) => ['clubCareer', clubId] as const

/** Temporadas encerradas, da mais recente para a mais antiga (issue #60). */
export function useSeasonHistory() {
  return useQuery({
    queryKey: seasonHistoryKey,
    queryFn: () => api.get<SeasonRecord[]>('/api/league/history'),
  })
}

/**
 * Drill-down de uma temporada. `enabled` só dispara quando o técnico escolhe
 * uma — a lista sozinha não paga o custo da classificação inteira.
 */
export function useSeasonRecord(season: number | null) {
  return useQuery({
    queryKey: seasonRecordKey(season ?? 0),
    queryFn: () => api.get<SeasonRecord>(`/api/league/history/${season}`),
    enabled: season != null,
  })
}

/**
 * Perfil do técnico. O 404 ("clube ainda não fechou temporada") é resposta
 * NORMAL até a primeira virada, então vira `null` em vez de erro — a tela
 * mostra o estado vazio e o React Query não fica tentando de novo.
 */
export function useClubCareer(clubId: number) {
  return useQuery({
    queryKey: clubCareerKey(clubId),
    queryFn: async () => {
      try {
        return await api.get<ClubCareer>(`/api/league/history/club/${clubId}`)
      } catch (err) {
        if (err instanceof ApiError && err.status === 404) return null
        throw err
      }
    },
  })
}
