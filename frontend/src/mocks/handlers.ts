// Handlers MSW — implementam o contrato OpenAPI no nível de rede.
// Cada handler casa com um operationId em ../../../contract/openapi.yaml.
// Quando o backend Spring tiver o controller correspondente pronto, basta
// remover/comentar o handler aqui e o app passa a falar com o backend real.

import { http, HttpResponse, delay, ws } from 'msw'
import { state } from './seed'
import { simulateMatch, averageOverall } from './engine'
import type {
  TransferResult,
  Club,
  ErrorResponse,
  MatchEvent,
} from '../domain/types'
import type { components } from '../api/generated'

type BuyPlayerRequest      = components['schemas']['BuyPlayerRequest']
type SellPlayerRequest     = components['schemas']['SellPlayerRequest']
type LineupRequest         = components['schemas']['LineupRequest']
type ExpandStadiumRequest  = components['schemas']['ExpandStadiumRequest']

// ─── WebSocket de partida ao vivo ─────────────────────────────────────────
// MSW intercepta o construtor global de WebSocket. Quando MatchPage abrir
// `ws://host/ws/matches/${id}`, este handler responde.
const wsProtocol = typeof window !== 'undefined' && window.location.protocol === 'https:'
  ? 'wss:' : 'ws:'
const wsHost = typeof window !== 'undefined' ? window.location.host : 'localhost:5173'
const matchStream = ws.link(`${wsProtocol}//${wsHost}/ws/matches/:id`)

const MS_PER_MINUTE = 80   // 90 minutos simulados em ~7s reais

// Latência simulada para parecer com rede real (descomente em testes determinísticos)
const SIMULATED_LATENCY_MS = 120

const notFound = (msg: string): ErrorResponse => ({
  code: 'NOT_FOUND',
  message: msg,
})

export const handlers = [

  // ─── getClub ──────────────────────────────────────────────────────────
  http.get('/api/clubs/:id', async ({ params }) => {
    await delay(SIMULATED_LATENCY_MS)
    const club = state.clubs[Number(params.id)]
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

    return HttpResponse.json({
      matchId: Date.now(),
      round,
      homeClubId: clubId,
      awayClubId: 2,
      homeGoals,
      awayGoals,
      events: [
        { type: 'KickOff',  minute: 0 },
        { type: 'FullTime', minute: 90, homeGoals, awayGoals },
      ],
      attendance: Math.min(club.stadiumCapacity, 12_000),
      ticketRevenue: 12_000 * 50_00,
    })
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
      awayName:     'Atlético Bonsucesso', // adversário mock enquanto não há liga completa
      homeStrength: averageOverall(myClub.squad),
      awayStrength: 75,
      homeSquad:    myClub.squad.map((p) => p.id),
      awaySquad:    [1001, 1002, 1003, 1004, 1005],
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
      client.close(1000, 'Partida encerrada')
    })()
  }),
]
