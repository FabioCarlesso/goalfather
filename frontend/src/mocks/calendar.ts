// Calendário semanal do mock (issue #125).
//
// O backend encaixa cada competição na semana com um agendador
// (`MatchdayScheduler.kt`). O mock NÃO replica o agendador: mantém a sequência
// de datas que já tinha (liga intercalada com copa, `seasonCalendar`) e só dá
// a cada data um encaixe plausível — liga no domingo, copa na quarta, as
// datas espalhadas pelo ano. É o suficiente para a UI exercitar semana,
// encaixe e data; o calendário de verdade vem do backend.

import type { Matchday, Slot } from '../domain/types'

const DAY_MS = 24 * 60 * 60 * 1000
/** Segunda-feira 05/01/1970 — mesma âncora do `Matchday.serial` do backend. */
const EPOCH_MONDAY_MS = Date.UTC(1970, 0, 5)

/** Segunda-feira (UTC) da semana ISO `week` de `year`: a semana 1 contém o dia 4 de janeiro. */
function isoMonday(year: number, week: number): number {
  const jan4 = Date.UTC(year, 0, 4)
  const dayOfWeek = (new Date(jan4).getUTCDay() + 6) % 7 // segunda = 0
  return jan4 - dayOfWeek * DAY_MS + (week - 1) * 7 * DAY_MS
}

/** O encaixe `slot` da semana ISO `week` de `year`, no formato do contrato. */
export function matchdayOf(year: number, week: number, slot: Slot): Matchday {
  const day = isoMonday(year, week) + (slot === 'MIDWEEK' ? 2 : 6) * DAY_MS
  return {
    key: `${year}-W${String(week).padStart(2, '0')}-${slot}`,
    year,
    week,
    slot,
    date: new Date(day).toISOString().slice(0, 10),
  }
}

/** Serial do encaixe (2 por semana desde 1970) — mesma conta do backend, usada no `matchId`. */
export function matchdaySerial(matchday: Matchday): number {
  const weeks = Math.round((isoMonday(matchday.year, matchday.week) - EPOCH_MONDAY_MS) / (7 * DAY_MS))
  return weeks * 2 + (matchday.slot === 'MIDWEEK' ? 0 : 1)
}

/**
 * Encaixe da data `index` (0-based) de uma temporada com `total` datas:
 * espalhadas pelas semanas 1..50 do ano, liga no fim de semana e copa no meio.
 */
export function seasonMatchday(season: number, index: number, total: number, slot: Slot): Matchday {
  const week = 1 + Math.floor((index * 50) / Math.max(1, total))
  return matchdayOf(season, week, slot)
}
