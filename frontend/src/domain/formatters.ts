// Helpers de apresentação compartilhados entre páginas/componentes.

/**
 * Dinheiro do jogo é dólar (issue #122): o contrato manda centavos de USD.
 * O locale continua `pt-BR` (separador de milhar com ponto, como o resto da
 * UI) — só a moeda muda, e o `Intl` escreve `US$` para não confundir com real.
 */
const USD = new Intl.NumberFormat('pt-BR', {
  style: 'currency',
  currency: 'USD',
  maximumFractionDigits: 0,
})

/**
 * Converte centavos de USD (do contrato) para texto. É o ÚNICO ponto da UI que
 * formata dinheiro — telas não chamam `toLocaleString` nem montam `US$` à mão.
 */
export const formatMoney = (cents: number): string => USD.format(cents / 100)

/** Símbolo da moeda para rótulos de campo ("Preço (US$)"). */
export const CURRENCY_SYMBOL = 'US$'

/** Formata número de assentos com separador de milhar PT-BR. */
export const formatSeats = (n: number): string =>
  new Intl.NumberFormat('pt-BR').format(n)
