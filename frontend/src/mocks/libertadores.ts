// Libertadores do mock (issue #132) — uma edição ESTÁTICA para a tela de
// grupos e chave evoluir contra o MSW. O mundo do mock tem a forma do pack do
// E2E, que não traz Libertadores; esta edição é um retrato montado à parte:
// os 2 brasileiros mais fortes do mock + os demais clubes reais da fase de
// grupos do pack, com ids próprios (101+). O sorteio, as tabelas e o mata-mata de verdade são do
// backend (`ContinentalRules.kt`); aqui é só dado de exemplo, já no formato do
// contrato (tabela ordenada, faixa de classificação), descartado com mocks/.

import type { Libertadores, StandingRow } from '../domain/types'
import { WORLD_CLUBS } from './world'

interface MockEntrant {
  id: number
  name: string
}

const brazilian = (id: number): MockEntrant => {
  const club = WORLD_CLUBS.find((c) => c.id === id)
  if (!club) throw new Error(`Clube ${id} fora do mundo do mock`)
  return { id: club.id, name: club.name }
}

/** 8 grupos de 4, sem dois clubes do mesmo país (como o sorteio do backend garante). */
const GROUPS: ReadonlyArray<readonly [string, readonly MockEntrant[]]> = [
  ['Grupo A', [brazilian(1), { id: 101, name: 'Boca Juniors' }, { id: 102, name: 'Universidad Católica' }, { id: 103, name: 'Always Ready' }]],
  ['Grupo B', [{ id: 104, name: 'Peñarol' }, brazilian(2), { id: 105, name: 'Junior' }, { id: 106, name: 'Cusco' }]],
  ['Grupo C', [{ id: 107, name: 'Nacional' }, { id: 108, name: 'Estudiantes' }, { id: 109, name: 'Libertad' }, { id: 127, name: 'Corinthians' }]],
  ['Grupo D', [{ id: 111, name: 'LDU Quito' }, { id: 112, name: 'Rosario Central' }, { id: 113, name: 'Santa Fe' }, { id: 128, name: 'Fluminense' }]],
  ['Grupo E', [{ id: 115, name: 'Cerro Porteño' }, { id: 116, name: 'Universitario' }, { id: 129, name: 'Cruzeiro' }, { id: 118, name: 'Universidad Central' }]],
  ['Grupo F', [{ id: 119, name: 'Bolívar' }, { id: 120, name: 'Independiente del Valle' }, { id: 121, name: 'Platense' }, { id: 130, name: 'Mirassol' }]],
  ['Grupo G', [{ id: 123, name: 'Sporting Cristal' }, { id: 124, name: 'Barcelona de Guayaquil' }, { id: 125, name: 'Independiente Medellín' }, { id: 126, name: 'Independiente Rivadavia' }]],
  ['Grupo H', [{ id: 117, name: 'Lanús' }, { id: 114, name: 'Coquimbo Unido' }, { id: 122, name: 'Deportes Tolima' }, { id: 110, name: 'Deportivo La Guaira' }]],
]

/** Turno e returno de 4 clubes (método do círculo), rodada a rodada: [mandante, visitante]. */
const ROUND_PAIRS: ReadonlyArray<ReadonlyArray<readonly [number, number]>> = [
  [[0, 3], [1, 2]],
  [[0, 2], [3, 1]],
  [[0, 1], [2, 3]],
]

/** Rodadas já jogadas no retrato: as duas primeiras, com placares fixos. */
const PLAYED_ROUNDS = 2
const SCORES: ReadonlyArray<readonly [number, number]> = [[2, 0], [1, 1], [0, 1], [3, 2]]

function groupOf(name: string, clubs: readonly MockEntrant[], groupIndex: number): Libertadores['groups'][number] {
  const turn = ROUND_PAIRS.map((round) => round.map(([h, a]) => [clubs[h]!, clubs[a]!] as const))
  const returno = turn.map((round) => round.map(([h, a]) => [a, h] as const))
  const fixtures = [...turn, ...returno].flatMap((round, r) =>
    round.map(([home, away], i) => {
      const played = r < PLAYED_ROUNDS
      const [homeGoals, awayGoals] = SCORES[(groupIndex + r * 2 + i) % SCORES.length]!
      return {
        round: r + 1,
        homeClubId: home.id,
        homeClubName: home.name,
        awayClubId: away.id,
        awayClubName: away.name,
        ...(played ? { homeGoals, awayGoals } : {}),
      }
    }),
  )
  return { name, rows: tableOf(clubs, fixtures), fixtures }
}

/** Tabela do retrato: pontos, saldo, gols — o desempate completo é do backend. */
function tableOf(clubs: readonly MockEntrant[], fixtures: Libertadores['groups'][number]['fixtures']): StandingRow[] {
  const rows = clubs.map((c) => ({
    position: 0, clubId: c.id, clubName: c.name, played: 0, wins: 0, draws: 0, losses: 0,
    goalsFor: 0, goalsAgainst: 0, goalDifference: 0, points: 0, zone: 'NONE' as StandingRow['zone'],
  }))
  const byId = new Map(rows.map((r) => [r.clubId, r]))
  for (const f of fixtures) {
    if (f.homeGoals == null || f.awayGoals == null) continue
    for (const [id, own, against] of [[f.homeClubId, f.homeGoals, f.awayGoals], [f.awayClubId, f.awayGoals, f.homeGoals]] as const) {
      const row = byId.get(id)!
      row.played++
      row.goalsFor += own
      row.goalsAgainst += against
      row.goalDifference += own - against
      if (own > against) { row.wins++; row.points += 3 } else if (own === against) { row.draws++; row.points++ } else row.losses++
    }
  }
  return rows
    .sort((a, b) => b.points - a.points || b.goalDifference - a.goalDifference || b.goalsFor - a.goalsFor || a.clubName.localeCompare(b.clubName))
    .map((r, i) => ({ ...r, position: i + 1, zone: i < 2 ? 'QUALIFICATION' : 'NONE' }))
}

/** A edição 2026 do mock: grupos na 3ª rodada, mata-mata ainda por sortear. */
export function mockLibertadores(): Libertadores {
  return {
    season: 2026,
    competitionId: 'conmebol-libertadores',
    competitionName: 'Copa Libertadores',
    qualifiersPerGroup: 2,
    groups: GROUPS.map(([name, clubs], i) => groupOf(name, clubs, i)),
    finalVenue: { name: 'Estadio Monumental U', capacity: 80_093 },
  }
}
