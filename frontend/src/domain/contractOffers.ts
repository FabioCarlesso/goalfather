// --- #138 Termos pessoais e renovação ---
// SÓ apresentação: o que o jogador aceita é regra do backend
// (`ContractNegotiationRules.kt`) e chega pronto no `PlayerRefused`. Aqui não
// se calcula exigência nenhuma — só se escreve a resposta dele.

import { formatGameDate } from './calendar'
import { formatMoney } from './formatters'
import type { ContractOffer, PlayerRefused, RefusalReason } from './types'

/** Durações que o contrato permite oferecer (1 a 5 temporadas); o jogador decide se topa. */
export const CONTRACT_YEARS_OPTIONS = [1, 2, 3, 4, 5] as const

/**
 * Salário inicial do formulário, em centavos: o atual de quem tem contrato; para
 * o livre, o piso de US$ 500 (espelha `MIN_CONTRACT_SALARY`). Só orienta o input —
 * se não bastar, a recusa diz quanto ele pede.
 */
export const MIN_CONTRACT_SALARY_CENTS = 500_00

/** Motivo da recusa em linguagem de técnico. `switch` exaustivo sobre o enum do contrato. */
export function refusalReasonLabel(reason: RefusalReason): string {
  switch (reason) {
    case 'LOW_SALARY':   return 'salário abaixo do que ele pede'
    case 'TOO_SHORT':    return 'contrato curto demais'
    case 'TOO_LONG':     return 'contrato longo demais'
    case 'WEAKER_CLUB':  return 'o seu clube é muito mais fraco que o dele'
    case 'LOWER_LEAGUE': return 'a sua liga é muito mais fraca que a dele'
  }
}

/** "1 temporada" / "2 a 4 temporadas". */
export function yearsRangeLabel(min: number, max: number): string {
  if (min === max) return `${min} temporada${min === 1 ? '' : 's'}`
  return `${min} a ${max} temporadas`
}

/** A recusa inteira numa frase, para toast e para o aviso do formulário. */
export function refusalMessage(refusal: Pick<PlayerRefused, 'reason' | 'demandedSalary' | 'minYears' | 'maxYears'>): string {
  return (
    `O jogador recusou: ${refusalReasonLabel(refusal.reason)}. ` +
    `Ele aceita ${formatMoney(refusal.demandedSalary)}/mês por ${yearsRangeLabel(refusal.minYears, refusal.maxYears)}.`
  )
}

/** Esfriamento da renovação: até quando ele não ouve nova proposta. */
export const cooldownMessage = (until: string): string =>
  `O jogador se sentiu desrespeitado e só volta a conversar em ${formatGameDate(until)}.`

/**
 * A oferta que atende à recusa: o salário que ele pede e a duração atual,
 * trazida para dentro da faixa dele. Não é regra de negócio — é copiar os
 * números que o backend mandou de volta para o formulário.
 */
export function offerMeetingRefusal(offer: ContractOffer, refusal: PlayerRefused): ContractOffer {
  return {
    ...offer,
    salary: Math.max(offer.salary, refusal.demandedSalary),
    years: Math.min(Math.max(offer.years, refusal.minYears), refusal.maxYears),
  }
}

/** "US$ 3.000/mês · 3 temporadas" — os termos de uma proposta. */
export function contractOfferSummary(offer: ContractOffer): string {
  const clause = offer.releaseClause ? ` · multa ${formatMoney(offer.releaseClause)}` : ''
  return `${formatMoney(offer.salary)}/mês · ${yearsRangeLabel(offer.years, offer.years)}${clause}`
}
