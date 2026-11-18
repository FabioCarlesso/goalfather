import { useQuery } from '@tanstack/react-query'
import { api } from '../client'
import type { ClubSchedule } from '../../domain/types'

/**
 * Calendário do clube semana a semana (issue #133). Sob a família
 * `['calendar']`, que o `useRoundStream` invalida a cada encaixe jogado — o
 * relógio andou, as semanas também.
 */
export const clubScheduleKey = (clubId: number, weeks: number) => ['calendar', 'schedule', clubId, weeks] as const

export function useClubSchedule(clubId: number, weeks: number) {
  return useQuery({
    queryKey: clubScheduleKey(clubId, weeks),
    queryFn: () => api.get<ClubSchedule>(`/api/clubs/${clubId}/schedule?weeks=${weeks}`),
  })
}
