import type { WorldCountry, WorldLeague } from './types'

/**
 * Passo do onboarding país → liga → clube (issue #131). União discriminada:
 * cada passo carrega exatamente o que já foi escolhido, então a página não
 * precisa de `!` para ler o país do passo da liga nem a liga do passo do clube.
 */
export type OnboardingStep =
  | { type: 'Country' }
  | { type: 'League'; country: WorldCountry }
  | { type: 'Club'; country: WorldCountry; league: WorldLeague }

/**
 * Passo a partir da escolha guardada na URL (`?country=BRA&league=bra-serie-a`).
 * Código desconhecido (link velho, liga que deixou de existir) volta um passo
 * em vez de quebrar: país inválido → países; liga inválida → ligas do país.
 */
export function onboardingStepOf(
  countries: WorldCountry[],
  countryCode: string | null,
  leagueId: string | null,
): OnboardingStep {
  const country = countries.find((c) => c.country.code === countryCode)
  if (!country) return { type: 'Country' }
  const league = country.leagues.find((l) => l.id === leagueId)
  if (!league) return { type: 'League', country }
  return { type: 'Club', country, league }
}

/** Clubes livres somados das ligas do país — o resumo do card do passo 1. */
export const availableClubsIn = (country: WorldCountry): number =>
  country.leagues.reduce((sum, league) => sum + league.availableClubCount, 0)

/** "1 liga" / "3 ligas": contagem com o substantivo concordando. */
export const countLabel = (n: number, singular: string, plural: string): string =>
  `${n} ${n === 1 ? singular : plural}`

/** "1ª divisão": o `tier` da liga no país, como o torcedor fala. */
export const tierLabel = (tier: number): string => `${tier}ª divisão`
