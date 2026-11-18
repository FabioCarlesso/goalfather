import { useQuery } from '@tanstack/react-query'
import { api, ApiError } from '../client'
import type { Libertadores } from '../../domain/types'

export const libertadoresKey = (season?: number) => ['libertadores', season ?? 'current'] as const

/**
 * A Libertadores (issue #132): grupos com tabela, jogos e a chave do
 * mata-mata. O 404 (`LIBERTADORES_NOT_FOUND`) é resposta NORMAL — o mundo do
 * E2E não tem Libertadores —, então vira `null` em vez de erro, como `useCup`.
 */
export function useLibertadores(season?: number) {
  return useQuery({
    queryKey: libertadoresKey(season),
    queryFn: async () => {
      try {
        return await api.get<Libertadores>(season == null ? '/api/libertadores' : `/api/libertadores?season=${season}`)
      } catch (err) {
        if (err instanceof ApiError && err.status === 404) return null
        throw err
      }
    },
  })
}
