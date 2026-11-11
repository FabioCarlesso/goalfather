// Construtores compactos de dados do contrato para testes (issue #125).
import type { Matchday, RoundMatch, RoundStage } from '../domain/types'

/** Encaixe de teste: a semana `week` de 2026, no fim de semana por padrão. */
export function testMatchday(week: number, slot: Matchday['slot'] = 'WEEKEND'): Matchday {
  // Datas reais de 2026 (semana 1 começa em 29/12/2025): quarta = +2, domingo = +6.
  const monday = Date.UTC(2025, 11, 29) + (week - 1) * 7 * 86_400_000
  const day = monday + (slot === 'MIDWEEK' ? 2 : 6) * 86_400_000
  return {
    key: `2026-W${String(week).padStart(2, '0')}-${slot}`,
    year: 2026,
    week,
    slot,
    date: new Date(day).toISOString().slice(0, 10),
  }
}

/**
 * Campos que o encaixe acrescentou a cada partida (issue #125): o que ela
 * disputa e de qual competição. Default = rodada 1 da liga da divisão 1.
 */
export function matchCompetition(
  stage: RoundStage = { type: 'League', matchday: 1 },
): Pick<RoundMatch, 'stage' | 'competitionId' | 'competitionName'> {
  return stage.type === 'League'
    ? { stage, competitionId: 'liga-d1', competitionName: 'Liga — Divisão 1' }
    : { stage, competitionId: 'copa', competitionName: 'Copa' }
}
