// Seleções do mundo do mock (issue #134).
//
// DADO de mock, não regra: o mock não simula datas FIFA (o backend é quem
// convoca, joga e devolve — `NationalTeamRules.kt`). O que existe aqui é só o
// suficiente para a UI ser desenvolvida contra o contrato: as datas FIFA de
// 2026–2027 (as mesmas do backend), uma prévia fixa de convocados (os 3
// melhores do elenco, sempre pela nacionalidade deles) e a tabela vazia das
// Eliminatórias.

import { COUNTRIES } from './seed'
import type {
  ClubCallUps,
  Country,
  FifaWindowSummary,
  NationalTableRow,
  NationalTeamsOverview,
  Player,
} from '../domain/types'

/** As datas FIFA interinas do backend (`interimFifaWindows`) para 2026 e 2027. */
export const MOCK_FIFA_WINDOWS: FifaWindowSummary[] = [
  { start: '2026-03-23', end: '2026-03-31', status: 'UPCOMING', matchSlots: 2 },
  { start: '2026-06-01', end: '2026-06-09', status: 'UPCOMING', matchSlots: 2 },
  { start: '2026-09-21', end: '2026-10-06', status: 'UPCOMING', matchSlots: 4 },
  { start: '2026-11-09', end: '2026-11-17', status: 'UPCOMING', matchSlots: 2 },
  { start: '2027-03-22', end: '2027-03-30', status: 'UPCOMING', matchSlots: 2 },
  { start: '2027-05-31', end: '2027-06-08', status: 'UPCOMING', matchSlots: 2 },
  { start: '2027-09-20', end: '2027-10-05', status: 'UPCOMING', matchSlots: 4 },
  { start: '2027-11-08', end: '2027-11-16', status: 'UPCOMING', matchSlots: 2 },
]

/** Quantos do elenco o mock "convoca" na prévia. */
export const MOCK_CALL_UPS_PER_CLUB = 3

const CONMEBOL: Country[] = [
  COUNTRIES.ARG,
  { code: 'BOL', name: 'Bolívia', confederation: 'CONMEBOL', flag: '🇧🇴' },
  COUNTRIES.BRA,
  { code: 'CHI', name: 'Chile', confederation: 'CONMEBOL', flag: '🇨🇱' },
  { code: 'COL', name: 'Colômbia', confederation: 'CONMEBOL', flag: '🇨🇴' },
  { code: 'ECU', name: 'Equador', confederation: 'CONMEBOL', flag: '🇪🇨' },
  { code: 'PAR', name: 'Paraguai', confederation: 'CONMEBOL', flag: '🇵🇾' },
  { code: 'PER', name: 'Peru', confederation: 'CONMEBOL', flag: '🇵🇪' },
  COUNTRIES.URU,
  { code: 'VEN', name: 'Venezuela', confederation: 'CONMEBOL', flag: '🇻🇪' },
]

const emptyRow = (country: Country, position: number): NationalTableRow => ({
  position, country, played: 0, won: 0, drawn: 0, lost: 0, goalsFor: 0, goalsAgainst: 0, goalDifference: 0, points: 0,
})

/** Painel das seleções antes da primeira data FIFA. */
export function mockNationalTeams(): NationalTeamsOverview {
  return {
    windows: MOCK_FIFA_WINDOWS,
    nextWindow: MOCK_FIFA_WINDOWS[0],
    lastResults: [],
    qualifiers: { cycle: 2026, roundsPlayed: 0, totalRounds: 18, rows: CONMEBOL.map((c, i) => emptyRow(c, i + 1)) },
    ranking: [],
  }
}

/** Prévia dos convocados do clube: os melhores aptos do elenco. */
export function mockClubCallUps(clubId: number, clubName: string, squad: Player[]): ClubCallUps {
  const best = squad
    .filter((p) => p.availability.type === 'Available')
    .sort((a, b) => b.overall - a.overall || a.id - b.id)
    .slice(0, MOCK_CALL_UPS_PER_CLUB)
  return {
    window: MOCK_FIFA_WINDOWS[0],
    players: best.map((p) => ({
      playerId: p.id,
      name: p.name,
      position: p.position,
      overall: p.overall,
      clubId,
      clubName,
      country: p.nationality,
    })),
  }
}
