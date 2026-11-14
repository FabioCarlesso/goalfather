import { useQuery } from '@tanstack/react-query'
import { api } from '../client'
import type { WorldCountry } from '../../domain/types'

export const worldCountriesKey = ['world', 'countries'] as const

/**
 * Países do mundo com as suas ligas — passos 1 e 2 do onboarding país → liga →
 * clube (issue #131). As contagens de clubes livres mudam a cada reivindicação,
 * então o claim invalida esta query (ver `useClaimClub`).
 */
export function useWorldCountries() {
  return useQuery({
    queryKey: worldCountriesKey,
    queryFn: () => api.get<WorldCountry[]>('/api/world/countries'),
  })
}
