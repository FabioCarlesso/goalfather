// Navegação pelo mundo no mock (issue #133): competições, tabela, jogos por
// encaixe, artilharia, chave e o calendário do clube semana a semana.
//
// Como o resto de mocks/, isto é descartável: o backend monta o mesmo a partir
// do plano do mundo (`CompetitionQueryService`). Aqui só se lê o estado do
// mock — a sequência de datas de `seasonSlots`, as rodadas jogadas e a copa —
// com a MESMA regra de visibilidade do backend: de um mata-mata só se conhece
// a fase em disputa.

import { http, HttpResponse, delay } from 'msw'
import {
  clubMeta,
  currentSlotIndex,
  leagueAssignments,
  nationalCupClubs,
  seasonSlots,
  slotRound,
  state,
} from './seed'
import { WORLD_COUNTRIES, WORLD_LEAGUES, seasonLabelOf, type MockLeague } from './world'
import { CUP_COMPETITION_ID, CUP_COMPETITION_NAME } from './cup'
import { NATIONAL_FOREIGN_PLAYER_LIMIT } from './engine'
import type {
  ClubSchedule,
  CompetitionDetail,
  CompetitionMatchday,
  CompetitionScorer,
  CompetitionSummary,
  ErrorResponse,
  Matchday,
  NextMatch,
  Round,
  ScheduleWeek,
  Tiebreaker,
} from '../domain/types'

const LATENCY_MS = 120
const TOP_SCORERS = 20

/** Critérios de desempate de cada liga do mock — os do pack do E2E. */
const TIEBREAKERS: Record<string, Tiebreaker[]> = {
  BRA: ['POINTS', 'WINS', 'GOAL_DIFFERENCE', 'GOALS_FOR', 'HEAD_TO_HEAD', 'FEWER_RED_CARDS', 'FEWER_YELLOW_CARDS', 'DRAWING_OF_LOTS'],
  ENG: ['POINTS', 'GOAL_DIFFERENCE', 'GOALS_FOR', 'HEAD_TO_HEAD', 'DRAWING_OF_LOTS'],
}

const clubsOfLeague = (leagueId: string): number[] =>
  Object.keys(leagueAssignments).map(Number).filter((id) => leagueAssignments[id] === leagueId)

/** Clubes que disputam a competição na temporada corrente. */
function clubsOf(competitionId: string): number[] {
  if (competitionId === CUP_COMPETITION_ID) {
    const cup = state.cup
    return cup ? [...new Set(cup.phases.flatMap((p) => p.entrants.map((c) => c.id)))] : nationalCupClubs().map((c) => c.id)
  }
  return clubsOfLeague(competitionId)
}

function leagueDetail(league: MockLeague): CompetitionDetail {
  const season = state.currentRound.season
  return {
    id: league.id,
    name: league.name,
    format: 'ROUND_ROBIN',
    kind: 'LEAGUE',
    country: league.country,
    confederation: league.country.confederation,
    tier: league.tier,
    season,
    seasonLabel: seasonLabelOf(league, season),
    clubCount: clubsOfLeague(league.id).length,
    tiebreakers: TIEBREAKERS[league.country.code] ?? ['POINTS', 'GOAL_DIFFERENCE', 'GOALS_FOR'],
    ...(league.country.code === WORLD_COUNTRIES.BRA.code ? { foreignPlayerLimit: NATIONAL_FOREIGN_PLAYER_LIMIT } : {}),
  }
}

function cupDetail(): CompetitionDetail | null {
  if (!state.cup) return null
  const season = state.currentRound.season
  return {
    id: CUP_COMPETITION_ID,
    name: CUP_COMPETITION_NAME,
    format: 'KNOCKOUT',
    kind: 'NATIONAL_CUP',
    country: WORLD_COUNTRIES.BRA,
    confederation: WORLD_COUNTRIES.BRA.confederation,
    season,
    seasonLabel: String(season),
    clubCount: clubsOf(CUP_COMPETITION_ID).length,
    tiebreakers: ['POINTS', 'GOAL_DIFFERENCE', 'GOALS_FOR'],
    foreignPlayerLimit: NATIONAL_FOREIGN_PLAYER_LIMIT,
  }
}

/** Todas as competições, na ordem do backend: Brasil primeiro, ligas por divisão, copa depois. */
function competitionDetails(): CompetitionDetail[] {
  const leagues = WORLD_LEAGUES.filter((l) => clubsOfLeague(l.id).length > 0).map(leagueDetail)
  const cup = cupDetail()
  const all = cup ? [...leagues, cup] : leagues
  const rank = (c: CompetitionDetail) => [c.country?.code === 'BRA' ? 0 : 1, c.country?.code ?? '', c.tier ?? Infinity] as const
  return [...all].sort((a, b) => {
    const [ra, rb] = [rank(a), rank(b)]
    return ra[0] - rb[0] || ra[1].localeCompare(rb[1]) || ra[2] - rb[2]
  })
}

const toSummary = (c: CompetitionDetail): CompetitionSummary => ({
  id: c.id,
  name: c.name,
  format: c.format,
  kind: c.kind,
  ...(c.country ? { country: c.country } : {}),
  confederation: c.confederation,
  ...(c.tier != null ? { tier: c.tier } : {}),
  ...(c.season != null ? { season: c.season, seasonLabel: c.seasonLabel } : {}),
  clubCount: c.clubCount,
})

/**
 * Os encaixes da temporada corrente como o técnico os enxerga hoje: os jogados
 * com placar, o corrente como está e os adiante pelo calendário — com a regra
 * de visibilidade do backend (`visibleRoundsAfter`): a copa só aparece no
 * PRIMEIRO encaixe dela adiante, e em nenhum se a fase corrente está em jogo.
 */
export function visibleSeasonRounds(): Round[] {
  const current = state.currentRound
  const slots = seasonSlots(current.season)
  const index = currentSlotIndex()
  const inPlay = current.status !== 'Finished'
  let cupKnown = !(inPlay && current.matches.some((m) => m.stage.type === 'Cup'))
  return slots.map((slot, i) => {
    if (i === index) return current
    if (i < index) {
      return state.playedRounds.find((r) => r.matchday.key === slot.matchday.key) ?? slotRound(slot, current.season)
    }
    const isCup = slot.stage.type === 'Cup'
    const round = isCup && !cupKnown
      ? { matchday: slot.matchday, season: current.season, status: 'Scheduled' as const, matches: [] }
      : slotRound(slot, current.season)
    if (isCup) cupKnown = false
    return round
  })
}

function competitionMatches(competitionId: string): CompetitionMatchday[] {
  const isCup = competitionId === CUP_COMPETITION_ID
  const slots = seasonSlots(state.currentRound.season)
  return visibleSeasonRounds()
    .filter((_, i) => (slots[i]?.stage.type === 'Cup') === isCup)
    .map((round) => ({ matchday: round.matchday, matches: round.matches.filter((m) => m.competitionId === competitionId) }))
    // Rodada que a liga não joga (turno mais curto que o da maior liga) não é encaixe dela.
    .filter((day) => isCup || day.matches.length > 0)
}

function competitionScorers(competitionId: string): CompetitionScorer[] {
  return clubsOf(competitionId)
    .flatMap((clubId) => {
      const club = state.clubs[clubId]
      return (club?.squad ?? [])
        .filter((p) => p.goals > 0)
        .map((p) => ({ playerId: p.id, playerName: p.name, clubId, clubName: clubMeta[clubId]?.name ?? club!.name, goals: p.goals }))
    })
    .sort((a, b) => b.goals - a.goals || (a.playerName < b.playerName ? -1 : a.playerName > b.playerName ? 1 : 0) || a.playerId - b.playerId)
    .slice(0, TOP_SCORERS)
}

// ─── Semanas ISO e datas FIFA ────────────────────────────────────────────

const DAY_MS = 24 * 60 * 60 * 1000

/** Datas FIFA interinas — as mesmas do `interimFifaWindows` do backend. */
function fifaWindows(year: number): Array<[number, number]> {
  return [
    [Date.UTC(year, 2, 23), Date.UTC(year, 2, 31)],
    [Date.UTC(year, 5, 1), Date.UTC(year, 5, 9)],
    [Date.UTC(year, 8, 21), Date.UTC(year, 9, 6)],
    [Date.UTC(year, 10, 9), Date.UTC(year, 10, 17)],
  ]
}

/** Segunda-feira (UTC) da semana do encaixe. */
const mondayOf = (matchday: Matchday): number => Date.parse(`${matchday.date}T00:00:00Z`) - (matchday.slot === 'MIDWEEK' ? 2 : 6) * DAY_MS

/**
 * Semana ISO da segunda `monday`: a quinta-feira da semana decide o ano, e a
 * semana é quantas semanas inteiras ela está de 1º de janeiro desse ano.
 */
export function isoWeekOf(monday: number): { year: number; week: number } {
  const thursday = monday + 3 * DAY_MS
  const year = new Date(thursday).getUTCFullYear()
  return { year, week: Math.floor((thursday - Date.UTC(year, 0, 1)) / (7 * DAY_MS)) + 1 }
}

const isoDate = (ms: number): string => new Date(ms).toISOString().slice(0, 10)

function clubSchedule(clubId: number, weeks: number): ClubSchedule {
  const current = state.currentRound
  const firstMonday = mondayOf(current.matchday)
  const lastSunday = firstMonday + (weeks * 7 - 1) * DAY_MS
  const matches: NextMatch[] = visibleSeasonRounds()
    .filter((round) => {
      const monday = mondayOf(round.matchday)
      return monday >= firstMonday && monday <= lastSunday
    })
    .flatMap((round) => round.matches
      .filter((m) => m.homeClubId === clubId || m.awayClubId === clubId)
      .map((match) => ({ matchday: round.matchday, match })))

  const windows = [new Date(firstMonday).getUTCFullYear(), new Date(lastSunday).getUTCFullYear()]
    .flatMap((year) => fifaWindows(year))
  const result: ScheduleWeek[] = Array.from({ length: weeks }, (_, i) => {
    const monday = firstMonday + i * 7 * DAY_MS
    const sunday = monday + 6 * DAY_MS
    const { year, week } = isoWeekOf(monday)
    return {
      year,
      week,
      start: isoDate(monday),
      end: isoDate(sunday),
      fifaWindow: windows.some(([start, end]) => !(end < monday || start > sunday)),
      matches: matches.filter((m) => mondayOf(m.matchday) === monday),
    }
  })
  return { clubId, current: current.matchday, weeks: result }
}

// ─── Handlers ────────────────────────────────────────────────────────────

const competitionNotFound = (id: string) =>
  HttpResponse.json({ code: 'COMPETITION_NOT_FOUND', message: `Competição ${id} não encontrada` } satisfies ErrorResponse, { status: 404 })

const notAvailable = (code: string, id: string) =>
  HttpResponse.json({ code, message: `Competição ${id} sem este recurso` } satisfies ErrorResponse, { status: 404 })

const findCompetition = (id: string): CompetitionDetail | undefined => competitionDetails().find((c) => c.id === id)

export const worldNavigationHandlers = [
  http.get('/api/competitions', async () => {
    await delay(LATENCY_MS)
    return HttpResponse.json(competitionDetails().map(toSummary))
  }),

  http.get('/api/competitions/:id', async ({ params }) => {
    await delay(LATENCY_MS)
    const id = String(params.id)
    const competition = findCompetition(id)
    return competition ? HttpResponse.json(competition) : competitionNotFound(id)
  }),

  http.get('/api/competitions/:id/standings', async ({ params }) => {
    await delay(LATENCY_MS)
    const id = String(params.id)
    if (!findCompetition(id)) return competitionNotFound(id)
    const table = state.standings.find((t) => t.competitionId === id)
    return table ? HttpResponse.json(table) : notAvailable('STANDINGS_NOT_FOUND', id)
  }),

  http.get('/api/competitions/:id/matches', async ({ params }) => {
    await delay(LATENCY_MS)
    const id = String(params.id)
    if (!findCompetition(id)) return competitionNotFound(id)
    return HttpResponse.json(competitionMatches(id))
  }),

  http.get('/api/competitions/:id/scorers', async ({ params }) => {
    await delay(LATENCY_MS)
    const id = String(params.id)
    if (!findCompetition(id)) return competitionNotFound(id)
    return HttpResponse.json(competitionScorers(id))
  }),

  http.get('/api/competitions/:id/bracket', async ({ params }) => {
    await delay(LATENCY_MS)
    const id = String(params.id)
    if (!findCompetition(id)) return competitionNotFound(id)
    return state.cup && state.cup.competitionId === id ? HttpResponse.json(state.cup) : notAvailable('BRACKET_NOT_FOUND', id)
  }),

  http.get('/api/clubs/:id/schedule', async ({ params, request }) => {
    await delay(LATENCY_MS)
    const clubId = Number(params.id)
    if (!state.clubs[clubId]) {
      return HttpResponse.json({ code: 'CLUB_NOT_FOUND', message: `Clube ${clubId} não encontrado` } satisfies ErrorResponse, { status: 404 })
    }
    const weeks = Number(new URL(request.url).searchParams.get('weeks') ?? 8)
    if (!Number.isInteger(weeks) || weeks < 1 || weeks > 26) {
      return HttpResponse.json(
        { code: 'INVALID_PARAMETER', message: "Parâmetro 'weeks' fora de 1..26", details: { parameter: 'weeks', value: String(weeks) } } satisfies ErrorResponse,
        { status: 400 },
      )
    }
    return HttpResponse.json(clubSchedule(clubId, weeks))
  }),
]
