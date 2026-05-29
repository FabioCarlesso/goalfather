// Dados iniciais para os handlers MSW.
// Espelham o protótipo (../../../prototype/goalfather-web.jsx) de forma simplificada.
// Quando o backend Kotlin estiver pronto, esta pasta inteira (mocks/) é removida.

import type {
  Club,
  Player,
  MarketEntry,
  Standings,
  Round,
  RoundMatch,
} from '../domain/types'

const player = (
  id: number,
  name: string,
  pos: Player['position'],
  ovr: number,
  salary: number,
  age: number,
): Player => ({
  id, name, position: pos, age,
  overall: ovr,
  pace: ovr + Math.round(Math.random() * 6 - 3),
  shooting: ovr + Math.round(Math.random() * 6 - 3),
  passing: ovr + Math.round(Math.random() * 6 - 3),
  defending: ovr + Math.round(Math.random() * 6 - 3),
  stamina: 100,
  salary,
  goals: 0,
  injured: false,
  star: ovr >= 82,
})

const initialSquad: Player[] = [
  player(1,  'Marcos Figueiredo', 'GK', 78, 25_000_00, 32),
  player(2,  'Rodrigo Alves',     'CB', 75, 18_000_00, 27),
  player(3,  'Túlio Mendes',      'CB', 72, 15_000_00, 24),
  player(4,  'Caio Bernardes',    'CB', 70, 13_000_00, 22),
  player(5,  'Pedro Henrique',    'CB', 73, 16_000_00, 26),
  player(6,  'Felipe Costa',      'MF', 80, 28_000_00, 28),
  player(7,  'Diego Lobato',      'MF', 76, 22_000_00, 25),
  player(8,  'Igor Vasques',      'MF', 74, 19_000_00, 23),
  player(9,  'Carlos Almeida',    'MF', 71, 14_000_00, 21),
  player(10, 'Renato Silva',      'FW', 88, 55_000_00, 29),
  player(11, 'João Faria',        'FW', 79, 26_000_00, 26),
]

export const myClub: Club = {
  id: 1,
  name: 'Goal Father FC',
  cash: 800_000_00,
  stadiumCapacity: 15_000,
  squad: initialSquad,
  ownerId: null,
}

export const marketEntries: MarketEntry[] = [
  { player: player(101, 'Lucas Vidigal',  'FW', 81, 32_000_00, 25), price:  450_000_00 },
  { player: player(102, 'André Pacheco',  'MF', 77, 23_000_00, 24), price:  220_000_00 },
  { player: player(103, 'Bruno Aparício', 'CB', 79, 25_000_00, 27), price:  300_000_00 },
  { player: player(104, 'Eduardo Ramires','GK', 76, 21_000_00, 28), price:  180_000_00 },
]

// ─── Registro de clubes da liga ──────────────────────────────────────────
// Para clubes da IA não precisamos de elenco completo (só ID + nome + força
// média). Elenco real só existe para o clube do usuário (state.clubs[1]).
export interface ClubMeta {
  id: number
  name: string
  strength: number    // overall agregado (mock)
  squad: number[]     // IDs sintéticos para autoria de gols/cartões
}

const aiSquad = (offset: number): number[] =>
  Array.from({ length: 11 }, (_, i) => offset + i + 1)

const myClubStrength =
  initialSquad.reduce((sum, p) => sum + p.overall, 0) / initialSquad.length

export const clubMeta: Record<number, ClubMeta> = {
  1: { id: 1, name: 'Goal Father FC',       strength: myClubStrength, squad: initialSquad.map((p) => p.id) },
  2: { id: 2, name: 'Atlético Bonsucesso',  strength: 76, squad: aiSquad(2000) },
  3: { id: 3, name: 'Esporte Clube Vargem', strength: 72, squad: aiSquad(3000) },
  4: { id: 4, name: 'Tupinambás FC',        strength: 70, squad: aiSquad(4000) },
  5: { id: 5, name: 'Independente Sul',     strength: 74, squad: aiSquad(5000) },
  6: { id: 6, name: 'Real Capela',          strength: 73, squad: aiSquad(6000) },
}

const allClubIds = Object.values(clubMeta).map((c) => c.id)

export const initialStandings: Standings = {
  season: 2026,
  round: 0,
  rows: allClubIds.map((id, i) => ({
    position: i + 1,
    clubId: id,
    clubName: clubMeta[id]!.name,
    played: 0, wins: 0, draws: 0, losses: 0,
    goalsFor: 0, goalsAgainst: 0, goalDifference: 0, points: 0,
  })),
}

// ─── Geração de rodadas ──────────────────────────────────────────────────
// Round-robin simples: pareamentos rotacionam a cada rodada. Para 6 clubes
// teremos 5 rodadas de 3 partidas cada (turno único).

function generateRound(roundNumber: number, season: number): Round {
  // Algoritmo: clube 1 fixo, os outros rotacionam (estilo Berger).
  const n = allClubIds.length
  const fixed = allClubIds[0]!
  const rotating = allClubIds.slice(1)
  const offset = (roundNumber - 1) % (n - 1)
  const rotated = [...rotating.slice(offset), ...rotating.slice(0, offset)]
  const order = [fixed, ...rotated]

  const matches: RoundMatch[] = []
  for (let i = 0; i < n / 2; i++) {
    const homeId = order[i]!
    const awayId = order[n - 1 - i]!
    matches.push({
      matchId: roundNumber * 1000 + i + 1,
      homeClubId: homeId,
      awayClubId: awayId,
      homeClubName: clubMeta[homeId]!.name,
      awayClubName: clubMeta[awayId]!.name,
      status: 'Scheduled',
      homeGoals: 0,
      awayGoals: 0,
      minute: 0,
    })
  }
  return { number: roundNumber, season, status: 'Scheduled', matches }
}

// ─── Atualização da tabela após uma rodada ───────────────────────────────
export function applyRoundToStandings(round: Round, current: Standings): Standings {
  const byId = new Map(current.rows.map((r) => [r.clubId, { ...r }]))

  for (const m of round.matches) {
    if (m.status !== 'Finished') continue
    const home = byId.get(m.homeClubId)
    const away = byId.get(m.awayClubId)
    if (!home || !away) continue

    home.played++; away.played++
    home.goalsFor += m.homeGoals
    home.goalsAgainst += m.awayGoals
    away.goalsFor += m.awayGoals
    away.goalsAgainst += m.homeGoals
    home.goalDifference = home.goalsFor - home.goalsAgainst
    away.goalDifference = away.goalsFor - away.goalsAgainst

    if (m.homeGoals > m.awayGoals) {
      home.wins++; home.points += 3
      away.losses++
    } else if (m.homeGoals < m.awayGoals) {
      away.wins++; away.points += 3
      home.losses++
    } else {
      home.draws++; home.points++
      away.draws++; away.points++
    }
  }

  const sorted = [...byId.values()].sort(
    (a, b) =>
      b.points - a.points ||
      b.goalDifference - a.goalDifference ||
      b.goalsFor - a.goalsFor ||
      a.clubName.localeCompare(b.clubName),
  )

  return {
    season: current.season,
    round: round.number,
    rows: sorted.map((r, i) => ({ ...r, position: i + 1 })),
  }
}

// Estado mutável — mocks alteram aqui para simular persistência durante a sessão.
export const state = {
  clubs: { [myClub.id]: myClub } as Record<number, Club>,
  market: [...marketEntries] as MarketEntry[],
  standings: { ...initialStandings, rows: [...initialStandings.rows] } as Standings,
  currentRound: generateRound(1, 2026) as Round,
  nextRound: () => generateRound(state.currentRound.number + 1, state.currentRound.season),
}
