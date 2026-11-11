import { useQuery } from '@tanstack/react-query'
import { api } from '../client'
import type { Calendar } from '../../domain/types'

export const calendarKey = (clubId: number) => ['calendar', clubId] as const

/**
 * Relógio do jogo (issue #125): encaixe corrente e próximo jogo do clube.
 * Muda a cada encaixe jogado — o `useRoundStream` invalida a família
 * `['calendar']` no `RoundFinished`.
 */
export function useCalendar(clubId: number) {
  return useQuery({
    queryKey: calendarKey(clubId),
    queryFn: () => api.get<Calendar>(`/api/league/calendar?clubId=${clubId}`),
  })
}
