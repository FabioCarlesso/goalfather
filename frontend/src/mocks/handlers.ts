// Handlers MSW — implementam o contrato OpenAPI no nível de rede.
// Cada handler casa com um operationId em ../../../contract/openapi.yaml.
// Quando o backend Spring tiver o controller correspondente pronto, basta
// remover/comentar o handler aqui e o app passa a falar com o backend real.

import { http, HttpResponse, delay, ws } from 'msw'
import {
  state,
  clubMeta,
  applyRoundToStandings,
  SEASON_ROUNDS,
  startNewSeason,
  auth,
  clubOwners,
  userIdFromAuthHeader,
  availableClubInfo,
  materializeClub,
  persistMockAuth,
  TOKEN_PREFIX,
  readinessStatus,
  markRoundReady,
  resetRoundReadiness,
  squadOf,
  DEFAULT_POSITIONS,
  type ClubMeta,
} from './seed'
import {
  simulateMatch,
  averageOverall,
  applyRoundFitness,
  applyMedicalTreatment,
  startingEleven,
  MulberryRng,
  matchStats,
  MEDICAL_DEPARTMENT_COST_CENTS,
  type SquadMember,
  type TeamTactics,
} from './engine'
import type {
  TransferResult,
  Club,
  ErrorResponse,
  MatchEvent,
  MatchSummary,
  RoundEvent,
  RoundFinance,
  RoundMatch,
  AuthResponse,
  AuthUser,
  AvailableClub,
} from '../domain/types'
import type { components } from '../api/generated'

type BuyPlayerRequest      = components['schemas']['BuyPlayerRequest']
type SellPlayerRequest     = components['schemas']['SellPlayerRequest']
type LineupRequest         = components['schemas']['LineupRequest']
type ExpandStadiumRequest  = components['schemas']['ExpandStadiumRequest']

// ─── WebSocket de partida e rodada ────────────────────────────────────────
// MSW intercepta o construtor global de WebSocket. Quando MatchPage/RoundPage
// abrir `ws://host/ws/(matches|round)/...`, estes handlers respondem.
const wsProtocol = typeof window !== 'undefined' && window.location.protocol === 'https:'
  ? 'wss:' : 'ws:'
const wsHost = typeof window !== 'undefined' ? window.location.host : 'localhost:5173'
const matchStream = ws.link(`${wsProtocol}//${wsHost}/ws/matches/:id`)
const roundStream = ws.link(`${wsProtocol}//${wsHost}/ws/round/:number`)

const MS_PER_MINUTE = 80   // 90 minutos simulados em ~7s reais

// ─── Regras financeiras (espelham domain/rules/FinanceRules.kt) ───────────
const TICKET_PRICE_CENTS = 50_00
const SALARY_EVERY_N_ROUNDS = 2
const AI_DEFAULT_CAPACITY = 12_000   // seed dos clubes da IA
const AI_SALARY_PER_PLAYER = 10_000_00
const attendanceRate = (strength: number) =>
  Math.min(1, Math.max(0.5, 0.5 + 0.5 * ((strength - 60) / 40)))
const ticketRevenueOf = (capacity: number, strength: number) =>
  Math.floor(capacity * attendanceRate(strength)) * TICKET_PRICE_CENTS

/**
 * Tática com que um clube entra em campo (issue #56) — espelha
 * `Club.startingLineup()` do backend: sai da escalação salva, e quem nunca
 * escalou (todos os clubes da IA neste mock) entra EQUILIBRADO em 4-4-2.
 */
const DEFAULT_TACTICS: TeamTactics = { posture: 'BALANCED', formation: '4-4-2' }

/**
 * Elenco que a engine enxerga (id + posição — issue #57). Para o clube do
 * usuário vale o elenco VIVO, que compras e vendas alteram; para os da IA, a
 * escalação sintética do seed.
 */
const squadInPlay = (meta: ClubMeta): SquadMember[] =>
  meta.id === 1 && state.clubs[1] ? squadOf(state.clubs[1].squad) : meta.squad

/** Adversário do drill-down standalone: 4-4-2 sintético, ids fora da liga. */
const aiOpponentSquad: SquadMember[] = DEFAULT_POSITIONS.map((pos, i) => ({ id: 1001 + i, pos }))

/** Autor dos gols do adversário no stub de `playMatch` (id fora da liga). */
const AWAY_STUB_SCORER_ID = 1011

const tacticsOf = (clubId: number): TeamTactics => {
  const lineup = state.clubs[clubId]?.lineup
  return {
    posture: lineup?.posture ?? DEFAULT_TACTICS.posture,
    formation: lineup?.formation ?? DEFAULT_TACTICS.formation,
  }
}

// Latência simulada para parecer com rede real (descomente em testes determinísticos)
const SIMULATED_LATENCY_MS = 120

const notFound = (msg: string): ErrorResponse => ({
  code: 'NOT_FOUND',
  message: msg,
})

type AuthBody = components['schemas']['RegisterRequest']

const toAuthUser = (u: { id: number; username: string; clubId: number | null }): AuthUser => ({
  id: u.id,
  username: u.username,
  clubId: u.clubId,
})

export const handlers = [

  // ─── register (issue #18) ─────────────────────────────────────────────
  http.post('/api/auth/register', async ({ request }) => {
    await delay(SIMULATED_LATENCY_MS)
    const { username, password } = (await request.json()) as AuthBody
    if (auth.users.some((u) => u.username === username)) {
      return HttpResponse.json(
        { code: 'USERNAME_TAKEN', message: `Username '${username}' já está em uso` } satisfies ErrorResponse,
        { status: 409 },
      )
    }
    const user = { id: auth.nextId++, username, password, clubId: null as number | null }
    auth.users.push(user)
    persistMockAuth()
    const body: AuthResponse = { token: TOKEN_PREFIX + user.id, user: toAuthUser(user) }
    return HttpResponse.json(body, { status: 201 })
  }),

  // ─── login ────────────────────────────────────────────────────────────
  http.post('/api/auth/login', async ({ request }) => {
    await delay(SIMULATED_LATENCY_MS)
    const { username, password } = (await request.json()) as AuthBody
    const user = auth.users.find((u) => u.username === username && u.password === password)
    if (!user) {
      return HttpResponse.json(
        { code: 'INVALID_CREDENTIALS', message: 'Usuário ou senha inválidos' } satisfies ErrorResponse,
        { status: 401 },
      )
    }
    const body: AuthResponse = { token: TOKEN_PREFIX + user.id, user: toAuthUser(user) }
    return HttpResponse.json(body)
  }),

  // ─── me (restaura sessão) ─────────────────────────────────────────────
  http.get('/api/auth/me', async ({ request }) => {
    await delay(SIMULATED_LATENCY_MS)
    const userId = userIdFromAuthHeader(request.headers.get('Authorization'))
    const user = userId != null ? auth.users.find((u) => u.id === userId) : undefined
    if (!user) {
      return HttpResponse.json(
        { code: 'UNAUTHORIZED', message: 'Autenticação necessária' } satisfies ErrorResponse,
        { status: 401 },
      )
    }
    return HttpResponse.json(toAuthUser(user))
  }),

  // ─── listAvailableClubs (issue #19) ───────────────────────────────────
  // Registrado ANTES de `/api/clubs/:id` para `available` não cair no :id.
  http.get('/api/clubs/available', async () => {
    await delay(SIMULATED_LATENCY_MS)
    const available: AvailableClub[] = Object.entries(clubOwners)
      .filter(([, owner]) => owner == null)
      .map(([id]) => availableClubInfo(Number(id)))
    return HttpResponse.json(available)
  }),

  // ─── claimClub (issue #19) ────────────────────────────────────────────
  http.post('/api/clubs/:id/claim', async ({ params, request }) => {
    await delay(SIMULATED_LATENCY_MS)
    const userId = userIdFromAuthHeader(request.headers.get('Authorization'))
    const user = userId != null ? auth.users.find((u) => u.id === userId) : undefined
    if (!user) {
      return HttpResponse.json(
        { code: 'UNAUTHORIZED', message: 'Autenticação necessária' } satisfies ErrorResponse,
        { status: 401 },
      )
    }
    const clubId = Number(params.id)
    if (!(clubId in clubOwners)) {
      return HttpResponse.json(notFound(`Clube ${clubId} não encontrado`), { status: 404 })
    }
    if (clubOwners[clubId] != null) {
      return HttpResponse.json(
        { code: 'CLUB_ALREADY_CLAIMED', message: `Clube ${clubId} já foi reivindicado` } satisfies ErrorResponse,
        { status: 409 },
      )
    }
    clubOwners[clubId] = user.id
    materializeClub(clubId)
    state.clubs[clubId] = { ...state.clubs[clubId]!, ownerId: user.id }
    user.clubId = clubId
    persistMockAuth()
    return HttpResponse.json(toAuthUser(user))
  }),

  // ─── getClub ──────────────────────────────────────────────────────────
  http.get('/api/clubs/:id', async ({ params }) => {
    await delay(SIMULATED_LATENCY_MS)
    const id = Number(params.id)
    // Após reload, `state.clubs` volta ao seed mas a posse foi restaurada de
    // `clubOwners` — rematerializa o clube reivindicado sob demanda (issue #19).
    if (!state.clubs[id] && clubOwners[id] != null) materializeClub(id)
    const club = state.clubs[id]
    return club
      ? HttpResponse.json(club)
      : HttpResponse.json(notFound(`Clube ${params.id} não encontrado`), { status: 404 })
  }),

  // ─── saveLineup ───────────────────────────────────────────────────────
  http.post('/api/clubs/:id/lineup', async ({ params, request }) => {
    await delay(SIMULATED_LATENCY_MS)
    const clubId = Number(params.id)
    const club = state.clubs[clubId]
    if (!club) return HttpResponse.json(notFound('Clube não encontrado'), { status: 404 })

    const body = (await request.json()) as LineupRequest
    state.clubs[clubId] = { ...club, lineup: body }
    return new HttpResponse(null, { status: 204 })
  }),

  // ─── expandStadium ────────────────────────────────────────────────────
  http.post('/api/clubs/:id/stadium/expand', async ({ params, request }) => {
    await delay(SIMULATED_LATENCY_MS)
    const clubId = Number(params.id)
    const club = state.clubs[clubId]
    if (!club) return HttpResponse.json(notFound('Clube não encontrado'), { status: 404 })

    const body = (await request.json()) as ExpandStadiumRequest
    const costPerSeat = 100_00 // R$ 100 por assento (mock)
    const totalCost = body.additionalSeats * costPerSeat

    if (club.cash < totalCost) {
      return HttpResponse.json(
        { code: 'INSUFFICIENT_FUNDS', message: 'Caixa insuficiente para ampliar' } satisfies ErrorResponse,
        { status: 402 },
      )
    }

    const updated: Club = {
      ...club,
      cash: club.cash - totalCost,
      stadiumCapacity: club.stadiumCapacity + body.additionalSeats,
    }
    state.clubs[clubId] = updated
    return HttpResponse.json(updated)
  }),

  // ─── treatSquad (departamento médico, issue #54) ──────────────────────
  http.post('/api/clubs/:id/medical', async ({ params }) => {
    await delay(SIMULATED_LATENCY_MS)
    const clubId = Number(params.id)
    const club = state.clubs[clubId]
    if (!club) return HttpResponse.json(notFound('Clube não encontrado'), { status: 404 })

    if (club.cash < MEDICAL_DEPARTMENT_COST_CENTS) {
      return HttpResponse.json(
        {
          code: 'INSUFFICIENT_FUNDS',
          message: 'Caixa insuficiente para o departamento médico',
        } satisfies ErrorResponse,
        { status: 402 },
      )
    }

    const updated: Club = {
      ...club,
      cash: club.cash - MEDICAL_DEPARTMENT_COST_CENTS,
      squad: applyMedicalTreatment(club.squad),
    }
    state.clubs[clubId] = updated
    return HttpResponse.json(updated)
  }),

  // ─── listMarket ───────────────────────────────────────────────────────
  http.get('/api/market', async ({ request }) => {
    await delay(SIMULATED_LATENCY_MS)
    const url = new URL(request.url)
    const position = url.searchParams.get('position')
    const maxPrice = url.searchParams.get('maxPrice')

    let entries = state.market
    if (position) entries = entries.filter((e) => e.player.position === position)
    if (maxPrice) entries = entries.filter((e) => e.price <= Number(maxPrice))

    return HttpResponse.json(entries)
  }),

  // ─── buyPlayer ────────────────────────────────────────────────────────
  http.post('/api/market/buy', async ({ request }) => {
    await delay(SIMULATED_LATENCY_MS)
    const body = (await request.json()) as BuyPlayerRequest
    const club = state.clubs[body.clubId]
    const entry = state.market.find((e) => e.player.id === body.playerId)

    if (!club || !entry) {
      const result: TransferResult = { type: 'PlayerNotAvailable', playerId: body.playerId }
      return HttpResponse.json(result)
    }
    if (club.cash < entry.price) {
      const result: TransferResult = {
        type: 'InsufficientFunds',
        available: club.cash,
        required: entry.price,
      }
      return HttpResponse.json(result)
    }
    if (club.squad.length >= 25) {
      const result: TransferResult = { type: 'SquadFull', maxSize: 25 }
      return HttpResponse.json(result)
    }

    const updatedClub: Club = {
      ...club,
      cash: club.cash - entry.price,
      squad: [...club.squad, entry.player],
    }
    state.clubs[body.clubId] = updatedClub
    state.market = state.market.filter((e) => e.player.id !== body.playerId)

    const result: TransferResult = { type: 'Success', club: updatedClub, player: entry.player }
    return HttpResponse.json(result)
  }),

  // ─── sellPlayer ───────────────────────────────────────────────────────
  http.post('/api/market/sell', async ({ request }) => {
    await delay(SIMULATED_LATENCY_MS)
    const body = (await request.json()) as SellPlayerRequest
    const club = state.clubs[body.clubId]
    if (!club) {
      return HttpResponse.json(
        { type: 'PlayerNotAvailable', playerId: body.playerId } satisfies TransferResult,
      )
    }
    const player = club.squad.find((p) => p.id === body.playerId)
    if (!player) {
      return HttpResponse.json(
        { type: 'PlayerNotAvailable', playerId: body.playerId } satisfies TransferResult,
      )
    }

    const salePrice = player.overall * 100_000_00 // R$ 100k * overall (mock)
    const updatedClub: Club = {
      ...club,
      cash: club.cash + salePrice,
      squad: club.squad.filter((p) => p.id !== body.playerId),
    }
    state.clubs[body.clubId] = updatedClub
    state.market = [...state.market, { player, price: salePrice }]

    return HttpResponse.json(
      { type: 'Success', club: updatedClub, player } satisfies TransferResult,
    )
  }),

  // ─── playMatch ────────────────────────────────────────────────────────
  // Mock simplificado: gera um resumo determinístico. A engine de fato vive
  // em src/mocks/engine.ts (a ser implementada) e é descartada quando o
  // backend Kotlin assumir.
  http.post('/api/clubs/:id/matches', async ({ params, request }) => {
    await delay(SIMULATED_LATENCY_MS)
    const clubId = Number(params.id)
    const round = Number(new URL(request.url).searchParams.get('round'))
    const club = state.clubs[clubId]
    if (!club) return HttpResponse.json(notFound('Clube não encontrado'), { status: 404 })

    const homeGoals = Math.floor(Math.random() * 4)
    const awayGoals = Math.floor(Math.random() * 3)

    // O resumo é montado a partir de eventos DE VERDADE, e as estatísticas
    // saem deles pelo mesmo `matchStats` da engine (issue #57). Preencher o
    // sumário à mão reintroduziria exatamente a divergência que a projeção
    // existe para impedir — um 3×2 reportando zero finalizações.
    const scorerId = (club.squad.find((p) => p.position === 'FW') ?? club.squad[0])?.id ?? 0
    const goalsOf = (count: number, home: boolean, author: number): MatchEvent[] =>
      Array.from({ length: count }, (_, i) => ({
        type: 'Goal' as const,
        minute: 10 + i * 15 + (home ? 0 : 7),
        scorerId: author,
        home,
      }))

    const played = [
      ...goalsOf(homeGoals, true, scorerId),
      ...goalsOf(awayGoals, false, AWAY_STUB_SCORER_ID),
    ].sort((a, b) => a.minute - b.minute)

    const events: MatchEvent[] = [
      {
        type: 'KickOff',
        minute: 0,
        homeClubName: club.name,
        awayClubName: clubMeta[2]?.name ?? 'Adversário',
        homeStrength: averageOverall(club.squad),
        awayStrength: 75,
        homePosture: 'BALANCED',
        awayPosture: 'BALANCED',
      },
      ...played,
      { type: 'FullTime', minute: 90, homeGoals, awayGoals, stats: matchStats(played) },
    ]

    return HttpResponse.json({
      matchId: Date.now(),
      round,
      homeClubId: clubId,
      awayClubId: 2,
      homeGoals,
      awayGoals,
      events,
      attendance: Math.min(club.stadiumCapacity, 12_000),
      ticketRevenue: 12_000 * 50_00,
    } satisfies MatchSummary)
  }),

  // ─── getStandings ─────────────────────────────────────────────────────
  http.get('/api/league/standings', async () => {
    await delay(SIMULATED_LATENCY_MS)
    return HttpResponse.json(state.standings)
  }),

  // ─── streamMatch (WebSocket) ──────────────────────────────────────────
  // Mapeia para o endpoint Spring `/ws/matches/{id}` que emitirá o mesmo
  // schema MatchEvent via Flow → WebSocketSession (Fase 3 do backend).
  matchStream.addEventListener('connection', ({ client, params }) => {
    const matchId = Number(params.id)
    const myClub = state.clubs[1]
    if (!myClub) {
      client.close(1011, 'Clube do usuário não encontrado')
      return
    }

    const setup = {
      matchId,
      homeName:     myClub.name,
      awayName:     'Atlético Bonsucesso', // adversário mock para exibição standalone
      homeStrength: averageOverall(myClub.squad),
      awayStrength: 75,
      homeSquad:    squadOf(myClub.squad),
      awaySquad:    aiOpponentSquad,
      homeTactics:  tacticsOf(1),
      awayTactics:  DEFAULT_TACTICS,   // adversário mock: sempre equilibrado
    }

    let cancelled = false
    client.addEventListener('close', () => { cancelled = true })

    void (async () => {
      let lastMinute = 0
      for (const event of simulateMatch(setup)) {
        if (cancelled) return
        const wait = Math.max(0, (event.minute - lastMinute) * MS_PER_MINUTE)
        if (wait > 0) await delay(wait)
        if (cancelled) return
        client.send(JSON.stringify(event satisfies MatchEvent))
        lastMinute = event.minute
      }
      // Gap antes do close para o cliente processar o FullTime — o MSW pode
      // engolir o último send se o close vier colado (mesmo cuidado do
      // roundStream). Passou a importar quando o FullTime virou portador do
      // sumário da partida (issue #57).
      await delay(50)
      if (cancelled) return
      client.close(1000, 'Partida encerrada')
    })()
  }),

  // ─── getCurrentRound ──────────────────────────────────────────────────
  http.get('/api/league/round/current', async () => {
    await delay(SIMULATED_LATENCY_MS)
    return HttpResponse.json(state.currentRound)
  }),

  // ─── getRoundReadiness (issue #20) ────────────────────────────────────
  http.get('/api/league/round/readiness', async () => {
    await delay(SIMULATED_LATENCY_MS)
    return HttpResponse.json(readinessStatus(state.currentRound.number))
  }),

  // ─── markRoundReady (issue #20) ───────────────────────────────────────
  http.post('/api/league/round/ready', async ({ request }) => {
    await delay(SIMULATED_LATENCY_MS)
    const userId = userIdFromAuthHeader(request.headers.get('Authorization'))
    if (userId == null) {
      return HttpResponse.json(
        { code: 'UNAUTHORIZED', message: 'Autenticação necessária' } satisfies ErrorResponse,
        { status: 401 },
      )
    }
    markRoundReady(state.currentRound.number, userId)
    return HttpResponse.json(readinessStatus(state.currentRound.number))
  }),

  // ─── playRound ────────────────────────────────────────────────────────
  http.post('/api/league/round/play', async () => {
    await delay(SIMULATED_LATENCY_MS)
    if (state.currentRound.status === 'Finished') {
      const err: ErrorResponse = {
        code: 'ROUND_ALREADY_FINISHED',
        message: 'Rodada já encerrada — atualize para a próxima.',
      }
      return HttpResponse.json(err, { status: 409 })
    }
    // Gate de liga compartilhada (issue #20): destrava com todos prontos OU
    // quando o timeout do escape hatch expira (issue #45).
    const status = readinessStatus(state.currentRound.number)
    const allReady = status.totalCount > 0 && status.readyCount >= status.totalCount
    if (!allReady && !status.timedOut) {
      const err: ErrorResponse = {
        code: 'ROUND_NOT_READY',
        message: `Aguardando técnicos: ${status.pendingUsernames.join(', ')}`,
        details: {
          ready: String(status.readyCount),
          total: String(status.totalCount),
          pending: status.pendingUsernames.join(','),
          secondsRemaining: status.secondsRemaining != null ? String(status.secondsRemaining) : '',
        },
      }
      return HttpResponse.json(err, { status: 409 })
    }
    state.currentRound = { ...state.currentRound, status: 'InProgress' }
    return HttpResponse.json({ roundNumber: state.currentRound.number })
  }),

  // ─── streamRound (WebSocket multiplexado) ─────────────────────────────
  // Espelha o futuro endpoint Spring `/ws/round/{n}` que emitirá
  // `Flow<RoundEvent>` coordenando N partidas simultâneas.
  roundStream.addEventListener('connection', ({ client, params }) => {
    const roundNumber = Number(params.number)
    const round = state.currentRound
    if (round.number !== roundNumber) {
      client.close(1011, `Rodada ${roundNumber} não está corrente`)
      return
    }

    // Pré-computa todos os eventos de todas as partidas e ordena por minuto.
    // Cada partida usa o próprio matchId como seed do RNG, então a sequência
    // é determinística por rodada+partida.
    type Tagged = { matchId: number; event: MatchEvent }
    const allEvents: Tagged[] = []
    const finalScores = new Map<number, { home: number; away: number }>()

    for (const match of round.matches) {
      const home = clubMeta[match.homeClubId]
      const away = clubMeta[match.awayClubId]
      if (!home || !away) continue
      const setup = {
        matchId:      match.matchId,
        homeName:     home.name,
        awayName:     away.name,
        homeStrength: home.id === 1 ? averageOverall(state.clubs[1]!.squad) : home.strength,
        awayStrength: away.id === 1 ? averageOverall(state.clubs[1]!.squad) : away.strength,
        homeSquad:    squadInPlay(home),
        awaySquad:    squadInPlay(away),
        homeTactics:  tacticsOf(home.id),
        awayTactics:  tacticsOf(away.id),
      }
      for (const event of simulateMatch(setup)) {
        allEvents.push({ matchId: match.matchId, event })
        if (event.type === 'FullTime') {
          finalScores.set(match.matchId, { home: event.homeGoals, away: event.awayGoals })
        }
      }
    }
    // Estável por minuto — eventos de minutos iguais saem juntos (sensação
    // de "vários estádios ao mesmo tempo").
    allEvents.sort((a, b) => a.event.minute - b.event.minute)

    let cancelled = false
    client.addEventListener('close', () => { cancelled = true })

    void (async () => {
      let lastMinute = 0
      for (const { matchId, event } of allEvents) {
        if (cancelled) return
        const wait = Math.max(0, (event.minute - lastMinute) * MS_PER_MINUTE)
        if (wait > 0) await delay(wait)
        if (cancelled) return
        const update: RoundEvent = { type: 'MatchUpdate', matchId, event }
        client.send(JSON.stringify(update))
        lastMinute = event.minute
      }

      // Atualiza estado da rodada com placares finais e fecha a tabela.
      const finishedMatches: RoundMatch[] = round.matches.map((m) => {
        const score = finalScores.get(m.matchId)
        return score
          ? { ...m, status: 'Finished', homeGoals: score.home, awayGoals: score.away, minute: 90 }
          : m
      })
      state.currentRound = { ...round, status: 'Finished', matches: finishedMatches }
      // A mesma rodada é aplicada à tabela de cada divisão — só os jogos da
      // divisão em questão contam (issue #47).
      state.standings = state.standings.map((table) =>
        applyRoundToStandings(state.currentRound, table))

      // Rodada consumida: zera a prontidão para a próxima começar limpa (issue #20).
      resetRoundReadiness(round.number)

      // Balanço financeiro da rodada (issue #4) — espelha computeFinances do
      // backend. Bilheteria só para mandantes; folha salarial a cada N rodadas.
      const isSalaryRound = round.number % SALARY_EVERY_N_ROUNDS === 0
      const homeIds = new Set(round.matches.map((m) => m.homeClubId))
      const finances: RoundFinance[] = round.matches
        .flatMap((m) => [m.homeClubId, m.awayClubId])
        .map((clubId) => {
          const capacity = clubId === 1 ? (state.clubs[1]?.stadiumCapacity ?? AI_DEFAULT_CAPACITY) : AI_DEFAULT_CAPACITY
          const strength = clubId === 1 ? averageOverall(state.clubs[1]!.squad) : (clubMeta[clubId]?.strength ?? 70)
          const salaries = isSalaryRound
            ? (clubId === 1 ? state.clubs[1]!.squad.reduce((s, p) => s + p.salary, 0) : 11 * AI_SALARY_PER_PLAYER)
            : 0
          const revenue = homeIds.has(clubId) ? ticketRevenueOf(capacity, strength) : 0
          const cash = clubId === 1 ? (state.clubs[1]?.cash ?? 0) : Number.MAX_SAFE_INTEGER
          // Rombo da folha não coberto pelo caixa+bilheteria (issue #23). Só o
          // clube do usuário tem caixa rastreado; a IA nunca fica no vermelho.
          return {
            clubId,
            ticketRevenue: revenue,
            salariesPaid: salaries,
            deficit: Math.max(0, salaries - revenue - cash),
          }
        })

      // Acumula estatísticas dos jogadores do clube do usuário a partir dos
      // eventos da rodada — espelha o PlayRoundService do backend (issue #2),
      // mantendo a parity mock ↔ real. Aplica também o caixa (issue #4).
      const myClub = state.clubs[1]
      if (myClub) {
        const goals = new Map<number, number>()
        const yellow = new Map<number, number>()
        const red = new Map<number, number>()
        // Lesão carrega a duração sorteada pela engine; duas na mesma rodada,
        // vale o afastamento mais longo (issue #54).
        const injuries = new Map<number, number>()
        for (const { event } of allEvents) {
          if (event.type === 'Goal') {
            goals.set(event.scorerId, (goals.get(event.scorerId) ?? 0) + 1)
          } else if (event.type === 'Card') {
            const target = event.red ? red : yellow
            target.set(event.playerId, (target.get(event.playerId) ?? 0) + 1)
          } else if (event.type === 'Injury') {
            injuries.set(event.playerId, Math.max(injuries.get(event.playerId) ?? 0, event.roundsOut))
          }
        }
        const myFinance = finances.find((f) => f.clubId === 1)
        const cashDelta = myFinance ? myFinance.ticketRevenue - myFinance.salariesPaid : 0
        // Titulares = escalação salva revalidada (lesionado fora, reserva apto
        // assume), exatamente como `Club.startingLineup()` do backend.
        const starterIds = startingEleven(myClub.squad, myClub.lineup?.playerIds)
        const rested = applyRoundFitness(
          myClub.squad,
          starterIds,
          new MulberryRng(round.number * 1000 + 1),
          injuries,
        )
        state.clubs[1] = {
          ...myClub,
          cash: Math.max(0, myClub.cash + cashDelta),
          squad: rested.map((p) => ({
            ...p,
            goals: p.goals + (goals.get(p.id) ?? 0),
            yellowCards: p.yellowCards + (yellow.get(p.id) ?? 0),
            redCards: p.redCards + (red.get(p.id) ?? 0),
          })),
        }
      }

      // Tabelas finais desta temporada (capturadas antes de eventual reset).
      const finalStandings = state.standings
      const seasonEnded = round.number >= SEASON_ROUNDS

      // Pequeno gap antes do RoundFinished para o cliente processar os
      // ultimos FullTime (MSW WS pode entregar fora de ordem se close vier
      // colado nos sends).
      await delay(50)
      if (cancelled) return

      if (seasonEnded) {
        // Fim de temporada (issue #11): emite SeasonFinished ANTES do
        // RoundFinished (mesma ordem do backend) e abre a próxima temporada
        // com promoção/rebaixamento aplicados (issue #47).
        const elite = finalStandings.find((t) => t.division === 1) ?? finalStandings[0]!
        // A virada roda ANTES do evento: é ela que produz as aposentadorias
        // (issue #55), que viajam junto do campeão.
        const retirements = startNewSeason(round.season + 1, finalStandings)
        const seasonFin: RoundEvent = {
          type: 'SeasonFinished',
          season: round.season,
          champion: elite.rows[0]!,
          standings: finalStandings,
          retirements,
        }
        client.send(JSON.stringify(seasonFin))
      } else {
        // Avança para a próxima rodada (cliente busca via getCurrentRound depois)
        state.currentRound = state.nextRound()
      }

      const finished: RoundEvent = { type: 'RoundFinished', standings: finalStandings, finances }
      client.send(JSON.stringify(finished))

      // Gap para o cliente processar RoundFinished antes do close
      await delay(50)
      client.close(1000, 'Rodada encerrada')
    })()
  }),
]
