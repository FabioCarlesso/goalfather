// Dados iniciais para os handlers MSW.
// Mundo pequeno com a forma do pack E2E do backend (issue #131) — ver
// ./world. Quando o backend Kotlin estiver pronto, esta pasta inteira
// (mocks/) é removida.

import type {
  BoardReview,
  CareerCampaign,
  Club,
  ClubCareer,
  Country,
  Cup,
  CupClub,
  Dismissal,
  TransferOffer,
  Matchday,
  Player,
  MarketEntry,
  Position,
  ReadinessStatus,
  SeasonRecord,
  SeasonTopScorer,
  Standings,
  TableZone,
  Retirement,
  Round,
  RoundMatch,
  RoundStage,
  TrainingReport,
} from '../domain/types'
import {
  MulberryRng,
  ageSquadOneSeason,
  DEFAULT_TICKET_PRICE_CENTS,
  type EffectiveLineupFields,
  type PendingYellows,
  type SquadMember,
} from './engine'
import {
  canHoldCup,
  cupRound,
  drawCup,
  seasonCalendar,
} from './cup'
import { matchdaySerial, seasonMatchday } from './calendar'
import {
  boardExpectations,
  boardsAfterRound,
  seasonBoardReviews,
  squadRating,
  toDismissal,
  type BoardClub,
  type MockBoard,
} from './board'
import { expireOffersOf } from './offers'
import {
  WORLD_CLUBS,
  WORLD_COUNTRIES,
  WORLD_LEAGUES,
  SQUAD_POSITIONS,
  leagueById,
  seasonLabelOf,
  syntheticSquad,
  type MockLeague,
} from './world'

// Países do seed (issue #123) — mesmos códigos, nomes e bandeiras que o
// catálogo `Countries` do backend devolve. Dado de mock, não regra. Os do
// mundo (Brasil, Inglaterra) vêm de ./world; os demais são das nacionalidades
// do mercado.
export const COUNTRIES = {
  ...WORLD_COUNTRIES,
  ARG: { code: 'ARG', name: 'Argentina', confederation: 'CONMEBOL', flag: '🇦🇷' },
  URU: { code: 'URU', name: 'Uruguai', confederation: 'CONMEBOL', flag: '🇺🇾' },
  POR: { code: 'POR', name: 'Portugal', confederation: 'UEFA', flag: '🇵🇹' },
  ITA: { code: 'ITA', name: 'Itália', confederation: 'UEFA', flag: '🇮🇹' },
} as const satisfies Record<string, Country>

const player = (
  id: number,
  name: string,
  pos: Player['position'],
  ovr: number,
  salary: number,
  age: number,
  nationality: Country = COUNTRIES.BRA,
  secondNationality?: Country,
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
  yellowCards: 0,
  redCards: 0,
  availability: { type: 'Available' },
  // Suspensões por competição (issue #130): todo mundo começa com a ficha limpa.
  suspensions: [],
  star: ovr >= 82,
  nationality,
  ...(secondNationality ? { secondNationality } : {}),
})

// Elenco escrito à mão do clube 1 (o mais forte da Série A do mock): é o que
// os testes de página usam pelo nome. Os outros 15 clubes têm elenco sintético
// (./world). Média 76, a força do clube 1 em WORLD_CLUBS.
const initialSquad: Player[] = [
  player(1,  'Marcos Figueiredo', 'GK', 78, 5_000_00, 32),
  player(2,  'Rodrigo Alves',     'CB', 75, 3_600_00, 27),
  player(3,  'Túlio Mendes',      'CB', 72, 3_000_00, 24),
  player(4,  'Caio Bernardes',    'CB', 70, 2_600_00, 22),
  player(5,  'Pedro Henrique',    'CB', 73, 3_200_00, 26),
  player(6,  'Felipe Costa',      'MF', 80, 5_600_00, 28),
  player(7,  'Diego Lobato',      'MF', 76, 4_400_00, 25),
  player(8,  'Igor Vasques',      'MF', 74, 3_800_00, 23),
  player(9,  'Carlos Almeida',    'MF', 71, 2_800_00, 21),
  player(10, 'Renato Silva',      'FW', 88, 11_000_00, 29),
  player(11, 'João Faria',        'FW', 79, 5_200_00, 26),
]

/**
 * Clube como o mock o GUARDA: sem os campos derivados da escalação efetiva
 * (issue #87), que os handlers calculam na resposta (`toClubResponse`) — assim
 * nenhum handler que muda elenco ou escalação pode esquecer de recalculá-los.
 */
export type StoredClub = Omit<Club, keyof EffectiveLineupFields>

/** Clube guardado a partir do registro do mundo e de um elenco. */
const storedClubOf = (world: (typeof WORLD_CLUBS)[number], squad: Player[]): StoredClub => ({
  id: world.id,
  name: world.name,
  cash: world.cash,
  stadiumCapacity: world.stadiumCapacity,
  squad,
  // Nenhum clube começa com dono (issue #131): o técnico escolhe no onboarding.
  ownerId: null,
  // Foco de treino da semana (issue #58): quem nunca escolheu começa poupando
  // o elenco, igual ao default do backend.
  trainingFocus: 'DESCANSO',
  // Preço do ingresso (issue #59): o default do backend, US$ 10.
  ticketPriceCents: DEFAULT_TICKET_PRICE_CENTS,
  // País do clube (issue #123) = o da liga em que ele joga.
  country: leagueById(world.leagueId).country,
})

export const myClub: StoredClub = storedClubOf(WORLD_CLUBS[0]!, initialSquad)

export const marketEntries: MarketEntry[] = [
  { player: player(101, 'Lucas Vidigal',  'FW', 81, 6_400_00, 25), price:  90_000_00 },
  // Estrangeiros (issue #123), espelhando o seed do backend.
  { player: player(102, 'André Pacheco',  'MF', 77, 4_600_00, 24, COUNTRIES.POR), price:  44_000_00 },
  { player: player(103, 'Bruno Aparício', 'CB', 79, 5_000_00, 27, COUNTRIES.ARG, COUNTRIES.ITA), price:  60_000_00 },
  { player: player(104, 'Eduardo Ramires','GK', 76, 4_200_00, 28, COUNTRIES.URU), price:  36_000_00 },
]

// ─── Registro de clubes do mundo ─────────────────────────────────────────
// Todo clube tem elenco completo desde o início (issue #131): a engine joga
// com o elenco vivo de cada um, como o backend. `clubMeta` guarda só o que não
// muda — nome e liga INICIAL (a corrente vive em `leagueAssignments`).
export interface ClubMeta {
  id: number
  name: string
  leagueId: string
}

/**
 * Escalação padrão de um clube. As posições importam desde a issue #57: a
 * engine pondera o autor da finalização por posição.
 */
export const DEFAULT_POSITIONS: Position[] = [...SQUAD_POSITIONS]

/** Elenco real → o que a engine precisa saber dele (id + posição). */
export const squadOf = (squad: Player[]): SquadMember[] =>
  squad.map((p) => ({ id: p.id, pos: p.position }))

export const clubMeta: Record<number, ClubMeta> = Object.fromEntries(
  WORLD_CLUBS.map((c) => [c.id, { id: c.id, name: c.name, leagueId: c.leagueId }]),
)

const allClubIds = WORLD_CLUBS.map((c) => c.id)

/** Elencos iniciais: o do clube 1 escrito à mão, os demais sintéticos com nomes únicos no mundo. */
function initialClubs(): Record<number, StoredClub> {
  const taken = new Set([...initialSquad, ...marketEntries.map((e) => e.player)].map((p) => p.name))
  return Object.fromEntries(
    WORLD_CLUBS.map((world) => [
      world.id,
      world.id === myClub.id
        ? myClub
        : storedClubOf(world, syntheticSquad(world, leagueById(world.leagueId).country, taken)),
    ]),
  )
}

// ─── Ligas (issues #47 e #131) ───────────────────────────────────────────
// Liga corrente de cada clube — muda na virada de temporada via
// promoção/rebaixamento, só entre divisões do MESMO país (Série A ↔ Série B;
// a Premier League do mock não tem para onde descer). Espelha pyramidRules
// (QualificationRules.kt) do backend (regra duplicada APENAS aqui em mocks/,
// que é descartável — CLAUDE.md).

export const PROMOTION_RELEGATION_SPOTS = 2

const initialLeagues: Record<number, string> =
  Object.fromEntries(WORLD_CLUBS.map((c) => [c.id, c.leagueId]))

// Estado mutável da liga corrente (sessão) — a virada de temporada troca
// as entradas de quem subiu/desceu.
export const leagueAssignments: Record<number, string> = { ...initialLeagues }

/** Liga corrente do clube. */
export const leagueOf = (clubId: number): MockLeague =>
  leagueById(leagueAssignments[clubId] ?? clubMeta[clubId]?.leagueId ?? WORLD_LEAGUES[0]!.id)

/** Divisão (tier) corrente do clube — o `division` das partidas e das tabelas. */
export const divisionOf = (clubId: number): number => leagueOf(clubId).tier

function clubsOfLeague(assignments: Record<number, string>, leagueId: string): number[] {
  return allClubIds.filter((id) => assignments[id] === leagueId)
}

/** Liga do mesmo país um degrau acima/abaixo (`delta` = -1/+1), se o mundo tiver. */
const neighbourLeague = (league: MockLeague, delta: number): MockLeague | undefined =>
  WORLD_LEAGUES.find((l) => l.country.code === league.country.code && l.tier === league.tier + delta)

const promotionSpotsOf = (league: MockLeague): number =>
  neighbourLeague(league, -1) ? PROMOTION_RELEGATION_SPOTS : 0

const relegationSpotsOf = (league: MockLeague): number =>
  neighbourLeague(league, +1) ? PROMOTION_RELEGATION_SPOTS : 0

// Faixa de cada posição (issue #128) — espelha `tableZones` sobre as regras da
// pirâmide (`pyramidRules`) do backend: só acesso e rebaixamento, porque o
// mundo do mock não tem vaga continental nem playoff. Liga sem vizinha (a
// Premier League do mock) vem toda `NONE`, como as europeias do MVP. A faixa
// é da POSIÇÃO e viaja na linha; quem reordena a tabela devolve a faixa ao lugar.
function zoneOf(position: number, size: number, promotion: number, relegation: number): TableZone {
  if (position <= promotion) return 'PROMOTION'
  if (position > size - relegation) return 'RELEGATION'
  return 'NONE'
}

// Tabelas zeradas de uma temporada, uma por liga do mundo, na ordem do
// contrato (país, depois divisão) — espelha seasonStandings do backend.
function freshStandings(season: number, assignments: Record<number, string>): Standings[] {
  return WORLD_LEAGUES.flatMap((league) => {
    const clubs = clubsOfLeague(assignments, league.id)
    if (clubs.length === 0) return []
    const promotionSpots = promotionSpotsOf(league)
    const relegationSpots = relegationSpotsOf(league)
    return [{
      season,
      seasonLabel: seasonLabelOf(league, season),
      competitionId: league.id,
      competitionName: league.name,
      country: league.country,
      division: league.tier,
      round: 0,
      promotionSpots,
      relegationSpots,
      rows: clubs.map((id, i) => ({
        position: i + 1,
        clubId: id,
        clubName: clubMeta[id]!.name,
        played: 0, wins: 0, draws: 0, losses: 0,
        goalsFor: 0, goalsAgainst: 0, goalDifference: 0, points: 0,
        zone: zoneOf(i + 1, clubs.length, promotionSpots, relegationSpots),
      })),
    }]
  })
}

export const initialStandings: Standings[] = freshStandings(2026, initialLeagues)

/**
 * Promoção/rebaixamento sobre as tabelas finais: em cada país, em cada par de
 * divisões adjacentes, os últimos N da de cima trocam com os primeiros N da de
 * baixo. Devolve a liga da próxima temporada por clube — espelha
 * applyPromotionRelegation do backend.
 */
export function applyPromotionRelegation(tables: Standings[]): Record<number, string> {
  const next: Record<number, string> = {}
  for (const table of tables) {
    for (const row of table.rows) next[row.clubId] = table.competitionId
  }
  const byPosition = (t: Standings) => [...t.rows].sort((a, b) => a.position - b.position)
  for (const country of new Set(tables.map((t) => t.country.code))) {
    const pyramid = tables.filter((t) => t.country.code === country).sort((a, b) => a.division - b.division)
    for (let i = 0; i + 1 < pyramid.length; i++) {
      const upper = pyramid[i]!
      const lower = pyramid[i + 1]!
      if (lower.division !== upper.division + 1) continue
      const spots = Math.min(PROMOTION_RELEGATION_SPOTS, upper.rows.length, lower.rows.length)
      for (const row of byPosition(upper).slice(-spots)) next[row.clubId] = lower.competitionId
      for (const row of byPosition(lower).slice(0, spots)) next[row.clubId] = upper.competitionId
    }
  }
  return next
}

// ─── Geração de rodadas ──────────────────────────────────────────────────
// Round-robin (Berger) POR LIGA, mesclado numa rodada global. Para 6 clubes
// teremos 5 rodadas de 3 partidas; a Premier League do mock (4 clubes) acaba
// o turno antes e folga nas últimas datas.

// `matchday` é o ENCAIXE do calendário semanal (issue #125) e `leagueRound` a
// rodada da LIGA: o Berger gira pela rodada; o matchId usa o serial do
// encaixe, como no backend. Cada partida carrega o `stage` e a competição.
function generateRound(matchday: Matchday, season: number, leagueRound: number): Round {
  const matches: RoundMatch[] = []

  WORLD_LEAGUES.forEach((league, leagueIndex) => {
    const ids = clubsOfLeague(leagueAssignments, league.id).sort((a, b) => a - b)
    const n = ids.length
    if (n < 2 || leagueRound > n - 1) return // turno desta liga já terminou

    // Algoritmo: clube de menor id fixo, os outros rotacionam (estilo Berger).
    const fixed = ids[0]!
    const rotating = ids.slice(1)
    const offset = (leagueRound - 1) % (n - 1)
    const rotated = [...rotating.slice(offset), ...rotating.slice(0, offset)]
    const order = [fixed, ...rotated]

    for (let i = 0; i < n / 2; i++) {
      const homeId = order[i]!
      const awayId = order[n - 1 - i]!
      matches.push({
        // encaixe × 1000 + (liga + 1) × 100 + índice — único no jogo inteiro
        // (a copa usa encaixe × 1000 + 1..99). Duas ligas de mesma divisão
        // (Série A e Premier League) não colidem porque entra a liga, não o tier.
        matchId: matchdaySerial(matchday) * 1000 + (leagueIndex + 1) * 100 + i + 1,
        homeClubId: homeId,
        awayClubId: awayId,
        homeClubName: clubMeta[homeId]!.name,
        awayClubName: clubMeta[awayId]!.name,
        status: 'Scheduled',
        homeGoals: 0,
        awayGoals: 0,
        minute: 0,
        division: league.tier,
        stage: { type: 'League', matchday: leagueRound },
        competitionId: league.id,
        competitionName: league.name,
      })
    }
  })
  return { matchday, season, status: 'Scheduled', matches }
}

// ─── Atualização da tabela após uma rodada ───────────────────────────────
// Aplica a rodada à tabela de UMA liga: partidas de clubes fora de
// `current.rows` são ignoradas, então a MESMA rodada global pode ser
// aplicada a cada tabela (mesma semântica do backend, issue #47).
// Jogo de COPA não mexe na tabela (issue #61) — filtrado POR PARTIDA desde o
// encaixe misto (issue #125) — e a tabela conta a rodada de liga, não o
// encaixe. Encaixe sem jogo desta tabela devolve a tabela intacta.
export function applyRoundToStandings(round: Round, current: Standings): Standings {
  const byId = new Map(current.rows.map((r) => [r.clubId, { ...r }]))
  const leagueMatches = round.matches.filter(
    (m) => m.stage.type === 'League' && byId.has(m.homeClubId) && byId.has(m.awayClubId),
  )
  const matchday = Math.max(0, ...leagueMatches.map((m) => (m.stage.type === 'League' ? m.stage.matchday : 0)))
  if (leagueMatches.length === 0) return current

  for (const m of leagueMatches) {
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

  // A faixa é da posição (issue #128): quem muda de lugar herda a do lugar novo.
  const zoneAt = new Map(current.rows.map((r) => [r.position, r.zone]))
  return {
    // Spread preserva divisão e vagas de promoção/rebaixamento da tabela.
    ...current,
    round: matchday,
    rows: sorted.map((r, i) => ({ ...r, position: i + 1, zone: zoneAt.get(i + 1) ?? 'NONE' })),
  }
}


// Turno único (Berger): a temporada dura o turno da MAIOR liga (N-1
// rodadas para N clubes). Constante vale porque as trocas de promoção/
// rebaixamento são balanceadas — os tamanhos das ligas não mudam.
export const SEASON_ROUNDS =
  Math.max(...WORLD_LEAGUES.map((l) => clubsOfLeague(initialLeagues, l.id).length)) - 1

// ─── Copa (issue #61) ────────────────────────────────────────────────────
// Sorteio da temporada com seed = temporada (no backend, `cupDrawSeed`). O
// RNG é outro, então a chave do mock não coincide com a do backend — só as
// regras (folgas, avanço, prêmio) coincidem.

/**
 * Clubes da copa nacional: os do Brasil (issue #131 — a Copa do Brasil reúne
 * Série A e B). Derivado pela liga corrente de cada clube, sem lista de ids.
 */
export const brazilianClubIds = (): number[] =>
  allClubIds.filter((id) => leagueOf(id).country.code === WORLD_COUNTRIES.BRA.code)

export function drawSeasonCup(season: number): Cup | null {
  const ids = brazilianClubIds()
  if (!canHoldCup(ids.length)) return null
  const clubs: CupClub[] = ids.map((id) => ({ id, name: clubMeta[id]!.name }))
  return drawCup(season, clubs, new MulberryRng(season * 7 + 61))
}

/**
 * Extrato de treino da rodada para TODOS os clubes do mundo — o backend treina
 * todo mundo em `persistRoundEffects`, tenha o clube jogado ou não (review do
 * PR #78). Quem não veio em `trained` entra com o relatório vazio do default.
 */
export function roundTrainingReports(...trained: TrainingReport[]): TrainingReport[] {
  const byClub = new Map(trained.map((r) => [r.clubId, r]))
  return allClubIds.map((clubId) => byClub.get(clubId) ?? { clubId, focus: 'DESCANSO', events: [] })
}

/** Calendário da temporada: as rodadas de liga com as fases da copa intercaladas. */
export function calendar(): RoundStage[] {
  return calendarFor(state.cup)
}

function calendarFor(cup: Cup | null): RoundStage[] {
  return seasonCalendar(SEASON_ROUNDS, cup?.phases.map((p) => p.phase) ?? [])
}

/**
 * As datas da temporada já com encaixe (issue #125): liga no domingo, copa na
 * quarta, espalhadas pelo ano — ver `./calendar`.
 */
export function seasonSlots(season: number, cup: Cup | null = state.cup): Array<{ stage: RoundStage; matchday: Matchday }> {
  const stages = calendarFor(cup)
  return stages.map((stage, i) => ({
    stage,
    matchday: seasonMatchday(season, i, stages.length, stage.type === 'League' ? 'WEEKEND' : 'MIDWEEK'),
  }))
}

/** Os jogos de uma data da temporada: rodada de liga ou fase corrente da copa. */
function roundOf(slot: { stage: RoundStage; matchday: Matchday }, season: number, cup: Cup | null): Round {
  if (slot.stage.type === 'Cup' && cup) return cupRound(slot.matchday, season, cup, divisionOf)
  return generateRound(slot.matchday, season, slot.stage.type === 'League' ? slot.stage.matchday : 1)
}

/** Os jogos de uma data da temporada corrente, com a copa como ela está agora. */
export function slotRound(slot: { stage: RoundStage; matchday: Matchday }, season: number): Round {
  return roundOf(slot, season, state.cup)
}

/** Posição (0-based) do encaixe corrente na temporada; -1 se não está nela. */
export function currentSlotIndex(): number {
  const { matchday, season } = state.currentRound
  return seasonSlots(season).findIndex((s) => s.matchday.key === matchday.key)
}

/** O encaixe corrente é o último da temporada? Então ela vira ao terminá-lo. */
export function isLastSlotOfSeason(): boolean {
  return currentSlotIndex() >= seasonSlots(state.currentRound.season).length - 1
}
const initialCup = drawSeasonCup(2026)

// Estado mutável — mocks alteram aqui para simular persistência durante a sessão.
export const state = {
  clubs: initialClubs(),
  market: [...marketEntries] as MarketEntry[],
  standings: initialStandings.map((s) => ({ ...s, rows: [...s.rows] })) as Standings[],
  // Temporadas encerradas, da mais recente para a mais antiga (issue #60).
  history: [] as SeasonRecord[],
  // Copa da temporada corrente (issue #61).
  cup: initialCup as Cup | null,
  currentRound: roundOf(seasonSlots(2026, initialCup)[0]!, 2026, initialCup) as Round,
  // Metas da diretoria da temporada corrente, por clube (issue #62) —
  // preenchidas logo abaixo, porque a força de cada clube sai de `state.clubs`.
  boards: {} as Record<number, MockBoard>,
  // Propostas entre técnicos (issue #63), da mais nova para a mais antiga —
  // estado de sessão, como o resto do mock.
  offers: [] as TransferOffer[],
  nextOfferId: 1,
  // Amarelos pendentes por jogador e competição (issue #130) — o contrato não
  // os expõe, então o mock guarda à parte, como o backend no `discipline_json`.
  pendingYellows: {} as PendingYellows,
  /** Próximo encaixe do calendário — rodada de liga ou fase de copa (issue #125). */
  nextRound: (): Round => {
    const { season } = state.currentRound
    const slots = seasonSlots(season)
    const next = slots[currentSlotIndex() + 1] ?? slots[slots.length - 1]!
    return roundOf(next, season, state.cup)
  },
}

// ─── Diretoria (issue #62) ───────────────────────────────────────────────
// As regras vivem em ./board (espelho de BoardRules.kt); aqui só o estado.

/** Força de cada clube para a diretoria, pelo elenco vivo; a meta é por liga. */
function boardClubs(): BoardClub[] {
  return allClubIds.map((id) => ({
    id,
    leagueId: leagueOf(id).id,
    rating: squadRating((state.clubs[id]?.squad ?? []).map((p) => p.overall)),
  }))
}

state.boards = boardExpectations(2026, boardClubs())

/**
 * Rodada de LIGA encerrada: a confiança de cada técnico anda conforme a posição
 * × a meta, só nas divisões que jogaram (data de copa não chama isto — a
 * tabela não mexe).
 */
export function evaluateBoards(tables: Standings[], playedClubIds: ReadonlySet<number>): void {
  state.boards = boardsAfterRound(state.boards, tables, playedClubIds)
}

/**
 * Veredito da diretoria na virada (issue #62) — chamar ANTES de
 * `startNewSeason`, que fixa as metas da temporada nova. Bônus entra no caixa
 * de quem tem caixa no mock (clubes materializados); a demissão de um clube com
 * dono humano devolve o clube à IA e registra a `dismissal` no usuário — o
 * claim ao contrário, como no backend.
 */
export function applyBoardTurnover(endedSeason: number, finalTables: Standings[]): BoardReview[] {
  const reviews = seasonBoardReviews(endedSeason, finalTables, state.boards, (id) => clubMeta[id]?.name ?? `Clube ${id}`)
  for (const review of reviews) {
    const club = state.clubs[review.clubId]
    switch (review.verdict.type) {
      case 'TargetMet':
        if (club) state.clubs[review.clubId] = { ...club, cash: club.cash + review.verdict.bonusCents }
        break
      case 'TargetMissed':
        break
      case 'Fired': {
        const ownerId = clubOwners[review.clubId]
        if (ownerId == null) break
        clubOwners[review.clubId] = null
        if (club) state.clubs[review.clubId] = { ...club, ownerId: null }
        // Propostas do técnico que saiu morrem com ele (revisão do PR #94).
        state.offers = expireOffersOf(state.offers, review.clubId)
        // Demissão só para quem AINDA aponta para este clube — como o
        // `release` do backend; quem já está em outro clube não é demitido.
        const owner = auth.users.find((u) => u.id === ownerId && u.clubId === review.clubId)
        if (owner) {
          owner.clubId = null
          owner.dismissal = toDismissal(review)
        }
        persistMockAuth()
        break
      }
    }
  }
  return reviews
}

// ─── Auth + seleção de clube (issues #18/#19) ─────────────────────────────
// Estado de auth do mock. O token é `mock-token-<userId>` — o handler de /me
// extrai o id daí. `clubOwners` espelha o `ownerId` do backend (todos os
// clubes do mundo começam sem dono — issue #131). A engine de mock joga com o
// elenco vivo de todo clube, então qualquer um pode ser reivindicado.
export interface MockUser {
  id: number
  username: string
  password: string
  clubId: number | null
  /** Demissão mais recente (issue #62); some ao reivindicar outro clube. */
  dismissal?: Dismissal | null
}

// Persistência do estado de auth do mock no localStorage: sobrevive ao reload
// (que reinicia os módulos JS e zeraria `auth`/`clubOwners`). É o que permite
// o E2E (Playwright) navegar para deep-links autenticado após um page.goto.
const MOCK_AUTH_KEY = 'gf:mockauth'

interface PersistedAuth {
  users: MockUser[]
  owners: Record<number, number | null>
  nextId: number
}

function loadPersistedAuth(): PersistedAuth | null {
  try {
    const raw = localStorage.getItem(MOCK_AUTH_KEY)
    return raw ? (JSON.parse(raw) as PersistedAuth) : null
  } catch {
    return null
  }
}

export function persistMockAuth(): void {
  try {
    const snapshot: PersistedAuth = { users: auth.users, owners: clubOwners, nextId: auth.nextId }
    localStorage.setItem(MOCK_AUTH_KEY, JSON.stringify(snapshot))
  } catch {
    /* ignora — sem persistência, o mock só perde estado em reloads */
  }
}

const persistedAuth = loadPersistedAuth()

export const auth = {
  users: (persistedAuth?.users ?? []) as MockUser[],
  nextId: persistedAuth?.nextId ?? 1,
}

// Merge com os defaults em vez de substituição: um localStorage persistido
// com um mundo menor não pode esconder clubes novos da seleção; e um dono de
// clube que não existe mais no mundo (o de antes da issue #131) é descartado.
export const clubOwners: Record<number, number | null> = {
  ...Object.fromEntries(allClubIds.map((id) => [id, null])),
  ...Object.fromEntries(
    Object.entries(persistedAuth?.owners ?? {}).filter(([id]) => Number(id) in clubMeta),
  ),
}

export const TOKEN_PREFIX = 'mock-token-'

// ─── Prontidão de rodada (issue #20) ──────────────────────────────────────
// Espelha a tabela `round_readiness`: ids de usuário prontos por número de
// rodada. Estado de sessão (não persistido) — o mock é descartável.
export const roundReadiness: Record<string, Set<number>> = {}

// Instante (ms) do primeiro "pronto" de cada rodada — ancora o timeout do
// escape hatch (issue #45), espelhando `MIN(ready_at)` do backend.
const roundFirstReadyAt: Record<string, number> = {}

// Timeout do escape hatch no mock (issue #45). Curto de propósito (30s) para o
// comportamento ser demonstrável/E2E-ável sem esperar os 2min do backend real;
// o valor real vem de `app.round-readiness.timeout` no servidor.
export const READINESS_TIMEOUT_MS = 30_000

/** Técnicos humanos = usuários do mock que já reivindicaram um clube. */
export function managers(): MockUser[] {
  return auth.users.filter((u) => u.clubId != null)
}

/**
 * Status de prontidão de uma rodada (espelha ReadinessStatus do backend).
 *
 * ÚNICO lugar do frontend com a regra do gate (issue #98): o mock precisa
 * simular o backend, então calcula `allReady`/`canStart` aqui — o `playRound`
 * de `handlers.ts` lê `canStart` daqui, e a app só lê os campos da resposta.
 */
export function readinessStatus(matchday: string): ReadinessStatus {
  const ready = roundReadiness[matchday] ?? new Set<number>()
  const mgrs = managers()
  const pending = mgrs.filter((u) => !ready.has(u.id))

  // Cronômetro só corre com pendentes E ao menos um pronto (o primeiro pronto
  // o arma) — parity com `buildStatus` do backend (issue #45). O guard
  // `ready.size > 0` é essencial: sem ele, um `firstAt` remanescente de uma
  // rodada anterior de mesmo número (rollover de temporada volta à rodada 1)
  // faria uma rodada nova, sem ninguém pronto, aparecer como `timedOut`.
  const firstAt = roundFirstReadyAt[matchday]
  const timerRunning = pending.length > 0 && ready.size > 0 && firstAt != null
  const secondsRemaining = timerRunning
    ? Math.max(0, Math.ceil((firstAt + READINESS_TIMEOUT_MS - Date.now()) / 1000))
    : null

  const readyCount = mgrs.length - pending.length
  const totalCount = mgrs.length
  const timedOut = secondsRemaining === 0
  // Parity com `ReadinessStatus.allReady`/`canStart` do domínio Kotlin.
  const allReady = totalCount > 0 && readyCount >= totalCount

  return {
    matchday,
    readyCount,
    totalCount,
    pendingUsernames: pending.map((u) => u.username),
    secondsRemaining,
    timedOut,
    allReady,
    canStart: allReady || timedOut,
  }
}

export function markRoundReady(matchday: string, userId: number): void {
  // Só técnicos (donos de clube) sinalizam prontidão — parity com o guard do
  // backend (issue #20, ponto #6); evita marcar usuário sem clube.
  if (!managers().some((u) => u.id === userId)) return
  ;(roundReadiness[matchday] ??= new Set<number>()).add(userId)
  roundFirstReadyAt[matchday] ??= Date.now()
}

export function resetRoundReadiness(matchday: string): void {
  delete roundReadiness[matchday]
  // Limpa também a âncora do timeout: senão o timestamp vaza para uma futura
  // rodada de mesmo número (rollover de temporada reusa a rodada 1).
  delete roundFirstReadyAt[matchday]
}

export function userIdFromAuthHeader(header: string | null): number | null {
  if (!header || !header.startsWith(`Bearer ${TOKEN_PREFIX}`)) return null
  const id = Number(header.slice(`Bearer ${TOKEN_PREFIX}`.length))
  return Number.isFinite(id) ? id : null
}

/** Overall médio cru do elenco (sem fadiga) — a "força média" do card de seleção. */
const meanOverall = (squad: Player[]): number =>
  squad.length === 0 ? 0 : Math.round(squad.reduce((s, p) => s + p.overall, 0) / squad.length)

/** Resumo de um clube disponível (espelha AvailableClubDto do backend). */
export function availableClubInfo(id: number) {
  const club = state.clubs[id]!
  const league = leagueOf(id)
  return {
    id,
    name: club.name,
    cash: club.cash,
    stadiumCapacity: club.stadiumCapacity,
    averageOverall: meanOverall(club.squad),
    division: league.tier,
    country: club.country,
    competitionId: league.id,
    competitionName: league.name,
  }
}

/**
 * Sincroniza o dono do clube guardado com `clubOwners` (issue #19). Todo clube
 * do mundo já nasce com elenco (issue #131); depois de um reload, `state.clubs`
 * volta ao seed mas a posse foi restaurada do localStorage.
 */
export function materializeClub(id: number): void {
  const club = state.clubs[id]
  if (!club) return
  state.clubs[id] = { ...club, ownerId: clubOwners[id] ?? club.ownerId ?? null }
}

/** Id do garoto da base promovido — único por temporada, clube e vaga (`youthPlayerId` no backend). */
const youthPlayerId = (season: number, clubId: number, slot: number): number =>
  1_000_000 + season * 10_000 + clubId * 100 + slot

// Vira a temporada (issue #11): aplica promoção/rebaixamento às tabelas
// finais (issue #47), envelhece e zera as estatísticas de TODOS os elencos
// (issue #55), cria tabelas novas (uma por liga) e gera a rodada 1 da
// próxima temporada. Espelha startNextSeason do backend, mantendo a parity
// mock ↔ real.
export function startNewSeason(season: number, finalTables: Standings[]): Retirement[] {
  // História ANTES do reset (issue #60): o snapshot precisa dos gols da
  // temporada, que o envelhecimento logo abaixo zera. Mesma ordem do backend
  // — é ela que o critério de aceite da issue exige. O campeão da copa
  // (issue #61) entra no record antes do sorteio da copa nova.
  state.history = [seasonRecordOf(season - 1, finalTables, state.cup?.champion), ...state.history]

  Object.assign(leagueAssignments, applyPromotionRelegation(finalTables))
  const retirements: Retirement[] = []
  for (const club of Object.values(state.clubs)) {
    // Envelhecimento (issue #55): +1 ano, evolução/regressão por faixa etária,
    // aposentadoria do veterano que já não rende e promoção da base no lugar
    // dele. Seed por temporada e clube, como o `agingSeed` do backend.
    const turn = ageSquadOneSeason(
      club.squad,
      new MulberryRng(season * 1_000 + club.id),
      club.id,
      (slot) => youthPlayerId(season, club.id, slot),
    )
    retirements.push(...turn.retirements)
    state.clubs[club.id] = {
      ...club,
      // Pré-temporada: elenco volta inteiro e descansado (issue #54).
      squad: turn.squad.map((p) => ({
        ...p,
        goals: 0,
        yellowCards: 0,
        redCards: 0,
        stamina: 100,
        availability: { type: 'Available' as const },
        // Pré-temporada sem gancho (issue #130).
        suspensions: [],
        // `star` é derivado no backend; aqui o mock recalcula, já que o
        // overall mudou na virada.
        star: p.overall >= 82,
      })),
    }
  }
  state.pendingYellows = {}
  state.standings = freshStandings(season, leagueAssignments)
  state.cup = drawSeasonCup(season)
  state.currentRound = roundOf(seasonSlots(season)[0]!, season, state.cup)
  // Metas da temporada nova (issue #62), com o elenco já envelhecido e os
  // clubes já nas ligas novas — mesma ordem do backend.
  state.boards = boardExpectations(season, boardClubs())
  return retirements
}

// ─── Histórico de temporadas (issue #60) ─────────────────────────────────
// Espelha domain/rules/SeasonHistoryRules.kt. Como o resto de mocks/, é
// código descartável: quando o backend estiver ligado, o handler sai e esta
// função vai junto.

/**
 * Snapshot da temporada encerrada: campeão, artilheiro e classificação final
 * de todas as divisões, já na ordem de exibição (elite primeiro, por posição).
 */
export function seasonRecordOf(season: number, finalTables: Standings[], cupChampion?: CupClub): SeasonRecord {
  const standings = [...finalTables]
    .sort((a, b) => a.division - b.division)
    .flatMap((table) =>
      [...table.rows]
        .sort((a, b) => a.position - b.position)
        .map((row) => ({
          division: table.division,
          row,
          // Aproveitamento: pontos conquistados / disputados. Time sem jogo
          // fica em 0 (e não numa divisão por zero).
          pointsPercentage: row.played === 0 ? 0 : Math.round((row.points * 100) / (row.played * 3)),
        })),
    )
  const topScorer = topScorerOf()
  return {
    season,
    champion: standings[0]!,
    finalStandings: standings,
    ...(topScorer ? { topScorer } : {}),
    ...(cupChampion ? { cupChampion } : {}),
  }
}

/**
 * Artilheiro entre os clubes MATERIALIZADOS. Limitação conhecida do mock: só
 * quem tem elenco de verdade em `state.clubs` acumula gols (os demais são
 * `clubMeta`, que guarda força e posições, não estatísticas). No backend a
 * artilharia é da liga inteira, IA incluída.
 */
function topScorerOf(): SeasonTopScorer | null {
  const scorers = Object.values(state.clubs).flatMap((club) =>
    club.squad.filter((p) => p.goals > 0).map((p) => ({ club, player: p })),
  )
  if (scorers.length === 0) return null
  // Mais gols; empate resolve pelo menor id, como no backend.
  const best = scorers.reduce((a, b) =>
    b.player.goals > a.player.goals || (b.player.goals === a.player.goals && b.player.id < a.player.id) ? b : a,
  )
  return {
    playerId: best.player.id,
    playerName: best.player.name,
    clubId: best.club.id,
    clubName: best.club.name,
    goals: best.player.goals,
  }
}

/**
 * Carreira do clube ao longo das temporadas registradas, ou `null` se ele
 * ainda não fechou nenhuma. Espelha `careerOf` do backend: "melhor" é
 * primeiro a divisão, depois a posição, e só então os pontos.
 */
export function careerOf(clubId: number, records: SeasonRecord[]): ClubCareer | null {
  const campaigns = records.flatMap((record) => {
    const standing = record.finalStandings.find((s) => s.row.clubId === clubId)
    return standing ? [{ season: record.season, standing }] : []
  })
  if (campaigns.length === 0) return null

  const latest = campaigns.reduce((a, b) => (b.season > a.season ? b : a))
  const best = campaigns.reduce((a, b) => (isBetterCampaign(b, a) ? b : a))
  return {
    clubId,
    clubName: latest.standing.row.clubName,
    seasonsPlayed: campaigns.length,
    titles: records.filter((r) => r.champion.row.clubId === clubId).map((r) => r.season).sort((a, b) => a - b),
    cupTitles: records.filter((r) => r.cupChampion?.id === clubId).map((r) => r.season).sort((a, b) => a - b),
    bestCampaign: best,
  }
}

/** Divisão pesa mais que posição, que pesa mais que pontos; temporada desempata. */
function isBetterCampaign(a: CareerCampaign, b: CareerCampaign): boolean {
  if (a.standing.division !== b.standing.division) return a.standing.division < b.standing.division
  if (a.standing.row.position !== b.standing.row.position) return a.standing.row.position < b.standing.row.position
  if (a.standing.row.points !== b.standing.row.points) return a.standing.row.points > b.standing.row.points
  return a.season < b.season
}
