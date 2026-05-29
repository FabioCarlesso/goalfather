// Dados iniciais para os handlers MSW.
// Espelham o protótipo (../../../prototype/goalfather-web.jsx) de forma simplificada.
// Quando o backend Kotlin estiver pronto, esta pasta inteira (mocks/) é removida.

import type {
  Club,
  Player,
  MarketEntry,
  Standings,
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

export const initialStandings: Standings = {
  season: 2026,
  round: 0,
  rows: [
    { position: 1,  clubId: 1,  clubName: 'Goal Father FC',  played: 0, wins: 0, draws: 0, losses: 0, goalsFor: 0, goalsAgainst: 0, goalDifference: 0, points: 0 },
    { position: 2,  clubId: 2,  clubName: 'Atlético Bonsucesso', played: 0, wins: 0, draws: 0, losses: 0, goalsFor: 0, goalsAgainst: 0, goalDifference: 0, points: 0 },
    { position: 3,  clubId: 3,  clubName: 'Esporte Clube Vargem', played: 0, wins: 0, draws: 0, losses: 0, goalsFor: 0, goalsAgainst: 0, goalDifference: 0, points: 0 },
    { position: 4,  clubId: 4,  clubName: 'Tupinambás FC',    played: 0, wins: 0, draws: 0, losses: 0, goalsFor: 0, goalsAgainst: 0, goalDifference: 0, points: 0 },
    { position: 5,  clubId: 5,  clubName: 'Independente Sul',  played: 0, wins: 0, draws: 0, losses: 0, goalsFor: 0, goalsAgainst: 0, goalDifference: 0, points: 0 },
  ],
}

// Estado mutável — mocks alteram aqui para simular persistência durante a sessão.
export const state = {
  clubs: { [myClub.id]: myClub } as Record<number, Club>,
  market: [...marketEntries] as MarketEntry[],
  standings: { ...initialStandings, rows: [...initialStandings.rows] } as Standings,
}
