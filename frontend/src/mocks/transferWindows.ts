// --- #137 Janelas de transferência ---
// Espelho, no mock, de domain/rules/TransferWindowRules.kt. Descartável junto
// do resto do mock quando o backend for o único alvo.
//
// As janelas são as do pack do E2E (`data/world/e2e/v1/calendar`), que é o
// mundo que o mock imita: o Brasil abre no 1º dia (o mercado está ABERTO na
// data inicial, 04/01/2026) e a Inglaterra não tem a janela de inverno de 2026
// (o mercado está FECHADO na data inicial). Datas ISO comparam como texto.

import type { Confederation, TransferResult, TransferWindowScope, TransferWindowStatus } from '../domain/types'

interface MockTransferWindow {
  country: string
  start: string
  end: string
  scope: TransferWindowScope
}

const W = (country: string, start: string, end: string, scope: TransferWindowScope = 'INTERNATIONAL'): MockTransferWindow =>
  ({ country, start, end, scope })

export const MOCK_TRANSFER_WINDOWS: MockTransferWindow[] = [
  W('BRA', '2026-01-01', '2026-03-03'),
  W('BRA', '2026-03-04', '2026-03-27', 'DOMESTIC_ONLY'),
  W('BRA', '2026-07-20', '2026-09-11'),
  W('ENG', '2026-06-15', '2026-09-01'),
  W('BRA', '2027-01-04', '2027-03-02'),
  W('BRA', '2027-07-19', '2027-09-10'),
  W('ENG', '2027-01-01', '2027-02-01'),
  W('ENG', '2027-06-15', '2027-09-01'),
]

/** As duas variantes de bloqueio — iguais no `TransferResult` e no `TransferOfferResult`. */
export type TransferBlock = Extract<TransferResult, { type: 'TransferWindowClosed' | 'TransferLimitReached' }>

const yearOf = (iso: string): number => Number(iso.slice(0, 4))
const withYear = (iso: string, year: number): string => `${year}${iso.slice(4)}`

/** Espelha `projectedFor`: ano sem dado repete o último ano conhecido da federação. */
function projectedFor(country: string, year: number): MockTransferWindow[] {
  const own = MOCK_TRANSFER_WINDOWS.filter((w) => w.country === country)
  const known = [...new Set(own.map((w) => yearOf(w.start)))].sort((a, b) => a - b)
  if (known.length === 0) return []
  if (known.includes(year)) return own.filter((w) => yearOf(w.start) === year)
  const source = [...known].reverse().find((y) => y < year) ?? known[0]!
  const shift = year - source
  return own
    .filter((w) => yearOf(w.start) === source)
    .map((w) => ({ ...w, start: withYear(w.start, yearOf(w.start) + shift), end: withYear(w.end, yearOf(w.end) + shift) }))
}

function openOn(country: string, date: string): MockTransferWindow | null {
  const year = yearOf(date)
  const open = [year - 1, year, year + 1]
    .flatMap((y) => projectedFor(country, y))
    .filter((w) => w.start <= date && date <= w.end)
  return open.find((w) => w.scope === 'INTERNATIONAL') ?? open[0] ?? null
}

function nextOpening(country: string, date: string, internationalOnly = false): string | undefined {
  const year = yearOf(date)
  return [year, year + 1, year + 2]
    .flatMap((y) => projectedFor(country, y))
    .filter((w) => w.start > date && (!internationalOnly || w.scope === 'INTERNATIONAL'))
    .map((w) => w.start)
    .sort()[0]
}

/** Espelha `windowStatus`. `nextOpens` ausente = federação sem calendário (como o backend omite o nulo). */
export function windowStatus(country: string, date: string): TransferWindowStatus {
  const open = openOn(country, date)
  if (open) return { type: 'Open', closes: open.end, scope: open.scope }
  const nextOpens = nextOpening(country, date)
  return nextOpens ? { type: 'Closed', nextOpens } : { type: 'Closed' }
}

const closed = (nextOpens: string | undefined): TransferBlock =>
  nextOpens ? { type: 'TransferWindowClosed', nextOpens } : { type: 'TransferWindowClosed' }

/** Espelha `windowBlocker`: fechada, ou só doméstica com origem de outro país (`origin` nulo = mercado). */
export function windowBlocker(buyer: string, origin: string | null, date: string): TransferBlock | null {
  const open = openOn(buyer, date)
  if (!open) return closed(nextOpening(buyer, date))
  if (origin != null && origin !== buyer && open.scope === 'DOMESTIC_ONLY') return closed(nextOpening(buyer, date, true))
  return null
}

/** Espelha `registrationSeason`: ano civil, ou de junho a maio na UEFA. */
function registrationSeason(confederation: Confederation, date: string): [string, string] {
  const year = yearOf(date)
  if (confederation !== 'UEFA') return [`${year}-01-01`, `${year}-12-31`]
  const start = date.slice(5) >= '06-01' ? year : year - 1
  return [`${start}-06-01`, `${start + 1}-05-31`]
}

/** Espelha `registrationBlocker`: janela, depois 1 registro por janela e 2 por temporada. */
export function registrationBlocker(
  buyer: { code: string; confederation: Confederation },
  origin: string | null,
  date: string,
  playerId: number,
  previous: string[],
): TransferBlock | null {
  const block = windowBlocker(buyer.code, origin, date)
  if (block) return block
  const current = openOn(buyer.code, date)!
  const [seasonStart, seasonEnd] = registrationSeason(buyer.confederation, date)
  if (previous.filter((d) => current.start <= d && d <= date).length >= 1) {
    return { type: 'TransferLimitReached', playerId, limit: 'ONE_PER_WINDOW' }
  }
  if (previous.filter((d) => seasonStart <= d && d <= seasonEnd).length >= 2) {
    return { type: 'TransferLimitReached', playerId, limit: 'TWO_PER_SEASON' }
  }
  return null
}
