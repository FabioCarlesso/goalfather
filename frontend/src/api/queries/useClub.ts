import { useQuery } from '@tanstack/react-query'
import { api } from '../client'
import type { Club } from '../../domain/types'

export const clubKey = (id: number) => ['club', id] as const

export function useClub(id: number) {
  return useQuery({
    queryKey: clubKey(id),
    queryFn: () => api.get<Club>(`/api/clubs/${id}`),
  })
}
