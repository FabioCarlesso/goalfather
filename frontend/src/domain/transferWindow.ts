// --- #137 Janelas de transferência ---
// SÓ apresentação: abrir, fechar e o limite de registros são regras do backend
// (`TransferWindowRules.kt`) e chegam prontas pelo contrato.

import { formatGameDate } from './calendar'
import type { RegistrationLimit, TransferWindowStatus } from './types'

/** "Janela aberta até 03/03/2026" / "Mercado fechado · abre em 20/07/2026". `switch` exaustivo sobre o `oneOf`. */
export function transferWindowLabel(status: TransferWindowStatus): string {
  switch (status.type) {
    case 'Open':
      return status.scope === 'DOMESTIC_ONLY'
        ? `Janela só doméstica até ${formatGameDate(status.closes)}`
        : `Janela aberta até ${formatGameDate(status.closes)}`
    case 'Closed':
      return status.nextOpens
        ? `Mercado fechado · abre em ${formatGameDate(status.nextOpens)}`
        : 'Mercado fechado · sem janela prevista'
  }
}

/** Texto do erro `TransferWindowClosed`, com a próxima abertura quando há. */
export function windowClosedMessage(nextOpens: string | undefined): string {
  return nextOpens
    ? `Janela de transferências fechada — abre em ${formatGameDate(nextOpens)}.`
    : 'Janela de transferências fechada, sem data prevista.'
}

export function registrationLimitMessage(limit: RegistrationLimit): string {
  switch (limit) {
    case 'ONE_PER_WINDOW': return 'Este jogador já foi transferido nesta janela.'
    case 'TWO_PER_SEASON': return 'Este jogador já teve duas transferências nesta temporada.'
  }
}
