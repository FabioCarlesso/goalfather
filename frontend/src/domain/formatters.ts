// Helpers de apresentação compartilhados entre páginas/componentes.

const BRL = new Intl.NumberFormat('pt-BR', {
  style: 'currency',
  currency: 'BRL',
  maximumFractionDigits: 0,
})

/** Converte centavos (do contrato) para string em BRL. */
export const formatMoney = (cents: number): string => BRL.format(cents / 100)

/** Formata número de assentos com separador de milhar PT-BR. */
export const formatSeats = (n: number): string =>
  new Intl.NumberFormat('pt-BR').format(n)
