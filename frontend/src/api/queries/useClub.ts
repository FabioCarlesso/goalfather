import { useQuery } from '@tanstack/react-query'
import { api } from '../client'
import type { Club } from '../../domain/types'

export const clubKey = (id: number) => ['club', id] as const

/**
 * Clube por id. Aceita `null`/`undefined` para quem ainda não sabe qual clube
 * buscar (ex.: o adversário antes de a rodada carregar — issue #84): o hook é
 * chamado sempre (regras de hooks) e `enabled` só dispara com id definido.
 */
export function useClub(id: number | null | undefined) {
  return useQuery({
    queryKey: clubKey(id ?? 0),
    queryFn: () => api.get<Club>(`/api/clubs/${id}`),
    enabled: id != null,
  })
}
