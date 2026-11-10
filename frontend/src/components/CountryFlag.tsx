import type { Country } from '../domain/types'

/**
 * Bandeira de um país (issue #123). O emoji vem pronto do backend (`flag`); o
 * componente só garante que leitores de tela e o tooltip digam o NOME do país,
 * já que o emoji sozinho é lido de forma inconsistente entre plataformas.
 *
 * `withName` mostra o nome ao lado (cabeçalho do clube). Aí o emoji vira
 * decorativo (`aria-hidden`), para o nome não ser lido duas vezes.
 */
export function CountryFlag({ country, withName = false }: { country: Country; withName?: boolean }) {
  if (withName) {
    return (
      <span className="inline-flex items-center gap-1">
        <span aria-hidden="true">{country.flag}</span>
        <span>{country.name}</span>
      </span>
    )
  }
  return (
    <span role="img" aria-label={country.name} title={country.name}>
      {country.flag}
    </span>
  )
}
