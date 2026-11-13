import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import { http, HttpResponse } from 'msw'
import type { ReactNode } from 'react'
import { deriveMatchStates, NOT_STARTED_MESSAGE, useRoundStream } from './useRoundStream'
import { standingsKey } from './useStandings'
import { makeQueryClient } from '../../test/render'
import { server } from '../../test/setup'
import { FakeWebSocket as FakeSocket } from '../../test/fakeWebSocket'
import type { MatchEvent, Round, RoundEvent, RoundMatch, Standings } from '../../domain/types'
import { matchCompetition, testMatchday } from '../../test/fixtures'

const MY_CLUB = 1

function match(overrides: Partial<RoundMatch> & Pick<RoundMatch, 'matchId'>): RoundMatch {
  return {
    homeClubId: MY_CLUB,
    awayClubId: 2,
    homeClubName: 'Goal Father FC',
    awayClubName: 'Rival',
    status: 'Scheduled',
    homeGoals: 0,
    awayGoals: 0,
    minute: 0,
    division: 1,
    ...matchCompetition({ type: 'League', matchday: 3 }),
    ...overrides,
  }
}

const round: Round = {
  matchday: testMatchday(3),
  season: 2026,
  status: 'Scheduled',
  matches: [match({ matchId: 10 }), match({ matchId: 11, homeClubId: 3, awayClubId: 4 })],
}

// Só os campos que o hook lê; o resto do evento é irrelevante aqui.
const update = (matchId: number, event: Partial<MatchEvent> & Pick<MatchEvent, 'type' | 'minute'>): RoundEvent =>
  ({ type: 'MatchUpdate', matchId, event }) as RoundEvent

const table: Standings = {
  season: 2026,
  division: 1,
  round: 3,
  promotionSpots: 0,
  relegationSpots: 2,
  rows: [],
}

function renderStream(initial: Round | undefined = round) {
  const queryClient = makeQueryClient()
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
  const hook = renderHook(({ r }: { r: Round | undefined }) => useRoundStream(r, MY_CLUB), {
    wrapper,
    initialProps: { r: initial },
  })
  return { ...hook, queryClient }
}

async function startAndOpen(result: { current: ReturnType<typeof useRoundStream> }) {
  await act(() => result.current.start())
  const socket = FakeSocket.last()
  socket.open()
  return socket
}

describe('deriveMatchStates', () => {
  it('partida em andamento sem lance acompanha o relógio da rodada', () => {
    const states = deriveMatchStates(round.matches, [
      update(10, { type: 'KickOff', minute: 0 }),
      update(11, { type: 'KickOff', minute: 0 }),
      update(10, { type: 'Goal', minute: 32, home: true }),
      update(11, { type: 'Card', minute: 46 }),
    ])
    expect(states.get(10)?.minute).toBe(46)
    expect(states.get(11)?.minute).toBe(46)
  })

  it('partida encerrada não avança com o relógio das outras', () => {
    const states = deriveMatchStates(round.matches, [
      update(10, { type: 'KickOff', minute: 0 }),
      update(11, { type: 'KickOff', minute: 0 }),
      update(10, { type: 'FullTime', minute: 90, homeGoals: 0, awayGoals: 0 }),
      update(11, { type: 'PenaltyShootout', minute: 120, homeScore: 5, awayScore: 4 }),
    ])
    expect(states.get(10)?.minute).toBe(90)
    expect(states.get(11)?.minute).toBe(120)
  })

  it('partida que ainda não começou fica no minuto 0', () => {
    const states = deriveMatchStates(round.matches, [update(10, { type: 'Goal', minute: 20, home: true })])
    expect(states.get(11)).toMatchObject({ minute: 0, status: 'Scheduled' })
  })

  it('aplica gols, pênaltis e o placar do FullTime sobre o estado inicial', () => {
    const states = deriveMatchStates(round.matches, [
      update(10, { type: 'KickOff', minute: 0 }),
      update(10, { type: 'Goal', minute: 12, home: true }),
      update(10, { type: 'Goal', minute: 30, home: false }),
      update(10, { type: 'PenaltyShootout', minute: 90, homeScore: 4, awayScore: 3 }),
      update(10, { type: 'FullTime', minute: 90, homeGoals: 1, awayGoals: 1 }),
      update(99, { type: 'Goal', minute: 5, home: true }), // partida desconhecida: ignorada
    ])
    expect(states.get(10)).toEqual({
      homeGoals: 1, awayGoals: 1, minute: 90, status: 'Finished', penalties: { home: 4, away: 3 },
    })
    expect(states.get(11)).toMatchObject({ homeGoals: 0, awayGoals: 0, status: 'Scheduled' })
    expect(states.has(99)).toBe(false)
  })
})

describe('useRoundStream (issue #112)', () => {
  beforeEach(() => {
    FakeSocket.instances = []
    vi.stubGlobal('WebSocket', FakeSocket)
    server.use(http.post('/api/league/round/play', () => HttpResponse.json({ matchday: '2026-W03-WEEKEND' })))
  })
  afterEach(() => vi.unstubAllGlobals())

  it('começa ocioso, mostrando a data corrente', () => {
    const { result } = renderStream()
    expect(result.current.status).toBe('idle')
    expect(result.current.shownRound).toBe(round)
    expect(result.current.myMatch?.matchId).toBe(10)
    expect(result.current.outcome).toBeNull()
  })

  it('abre /ws/slot/{key}, acompanha o placar e fecha com o desfecho do clube', async () => {
    const { result, queryClient } = renderStream()
    const socket = await startAndOpen(result)

    expect(new URL(socket.url).pathname).toBe('/ws/slot/2026-W03-WEEKEND')
    expect(result.current.status).toBe('live')

    socket.emit(update(10, { type: 'KickOff', minute: 0 }))
    socket.emit(update(10, { type: 'Goal', minute: 20, home: true }))
    socket.emit(update(11, { type: 'Goal', minute: 40, home: false }))
    expect(result.current.matchStates.get(10)).toMatchObject({ homeGoals: 1, status: 'InProgress' })
    expect(result.current.aggregateMinute).toBe(40)
    expect(result.current.myMatchEvents.map((e) => e.type)).toEqual(['KickOff', 'Goal'])

    socket.emit({
      type: 'RoundFinished',
      standings: [table],
      finances: [
        { clubId: 2, ticketRevenue: 1, salariesPaid: 0, deficit: 0, ticketPrice: 1, attendance: 1, cupPrize: 0 },
        { clubId: MY_CLUB, ticketRevenue: 500, salariesPaid: 0, deficit: 0, ticketPrice: 5, attendance: 100, cupPrize: 0 },
      ],
      training: [{ clubId: MY_CLUB, focus: 'DESCANSO', events: [] }],
    } as RoundEvent)
    expect(queryClient.getQueryData(standingsKey)).toEqual([table])
    // Ainda transmitindo: o desfecho só existe depois que o socket fecha.
    expect(result.current.outcome).toBeNull()

    socket.serverClose()
    expect(result.current.status).toBe('finished')
    expect(result.current.error).toBeNull()
    expect(result.current.outcome).toMatchObject({
      played: round,
      standings: [table],
      finance: { clubId: MY_CLUB, ticketRevenue: 500 },
      training: { clubId: MY_CLUB, focus: 'DESCANSO' },
      season: null,
    })
  })

  it('segue mostrando a data jogada quando a corrente avança para a próxima', async () => {
    const { result, rerender } = renderStream()
    await startAndOpen(result)

    const next: Round = {
      ...round,
      matchday: testMatchday(4, 'MIDWEEK'),
      matches: [match({ matchId: 20, ...matchCompetition({ type: 'Cup', phase: 'ROUND_OF_16' }) })],
    }
    rerender({ r: next })

    expect(result.current.shownRound).toBe(round)
    expect(result.current.myMatch?.matchId).toBe(10)
  })

  it('falha no playRound vira erro sem abrir socket', async () => {
    server.use(
      http.post('/api/league/round/play', () =>
        HttpResponse.json({ code: 'INTERNAL', message: 'Falhou' }, { status: 500 }),
      ),
    )
    const { result } = renderStream()
    await act(() => result.current.start())

    expect(result.current.status).toBe('error')
    expect(result.current.error).toMatch(/^Falha ao iniciar rodada/)
    expect(FakeSocket.instances).toHaveLength(0)
  })

  it('fechamento anormal com motivo vira mensagem de erro', async () => {
    const { result } = renderStream()
    const socket = await startAndOpen(result)
    socket.serverClose(1011, 'Rodada 3 não está corrente')

    expect(result.current.status).toBe('finished')
    expect(result.current.error).toBe('Rodada 3 não está corrente')
    expect(result.current.outcome).toBeNull()
  })

  it('encaixe não liberado volta ao estado ocioso, aguardando a prontidão (issue #151)', async () => {
    const { result } = renderStream()
    const socket = await startAndOpen(result)
    socket.emit({ type: 'RoundNotStarted' })
    socket.serverClose(1000, 'Encaixe nao liberado')

    // `idle`, e não `finished`: a rodada não foi jogada.
    expect(result.current.status).toBe('idle')
    expect(result.current.error).toBe(NOT_STARTED_MESSAGE)
    expect(result.current.outcome).toBeNull()
    expect(result.current.shownRound).toBe(round)
    expect(result.current.matchStates.get(10)?.status).toBe('Scheduled')
  })

  it('jogar de novo solta o socket anterior e recomeça do zero', async () => {
    const { result } = renderStream()
    const first = await startAndOpen(result)
    first.emit(update(10, { type: 'Goal', minute: 20, home: true }))

    const second = await startAndOpen(result)
    expect(first.closed).toBe(true)
    // O onclose do socket velho foi desligado: não derruba o stream novo.
    expect(first.onclose).toBeNull()
    expect(result.current.status).toBe('live')
    expect(result.current.matchStates.get(10)?.homeGoals).toBe(0)
    expect(second).not.toBe(first)
  })

  it('abre o stream na velocidade escolhida (issue #115)', async () => {
    const { result } = renderStream()
    const socket = await startAndOpen(result)
    expect(socket.url).toContain('/ws/slot/2026-W03-WEEKEND')
    expect(socket.url).toContain('speed=NORMAL')
    expect(result.current.liveSpeed).toBe('NORMAL')
  })

  it('pular para o fim abre uma conexão INSTANT e só fecha a antiga quando ela termina (issue #115)', async () => {
    const playCalls = vi.fn()
    const countPlay = ({ request }: { request: Request }) => {
      if (request.method === 'POST' && request.url.endsWith('/api/league/round/play')) playCalls()
    }
    server.events.on('request:start', countPlay)
    const { result } = renderStream()
    const first = await startAndOpen(result)
    first.emit(update(10, { type: 'Goal', minute: 20, home: true }))
    const callsBefore = playCalls.mock.calls.length

    act(() => result.current.skip())
    const second = FakeSocket.last()
    expect(second).not.toBe(first)
    expect(second.url).toContain('speed=INSTANT')
    expect(result.current.liveSpeed).toBe('INSTANT')
    // Não joga a rodada de novo: só reprocessa o stream.
    expect(playCalls.mock.calls.length).toBe(callsBefore)
    // A antiga fica aberta, mas muda: estado recomeça do zero.
    expect(first.closed).toBe(false)
    expect(first.onmessage).toBeNull()
    expect(result.current.matchStates.get(10)?.homeGoals).toBe(0)

    second.open()
    second.emit(update(10, { type: 'Goal', minute: 20, home: true }))
    second.emit({ type: 'RoundFinished', standings: [table], finances: [], training: [] } as unknown as RoundEvent)
    second.serverClose()
    expect(first.closed).toBe(true)
    expect(result.current.status).toBe('finished')
    expect(result.current.outcome?.played.matchday.key).toBe('2026-W03-WEEKEND')
    expect(playCalls).toHaveBeenCalledTimes(1) // só o do start
    server.events.removeListener('request:start', countPlay)
  })

  it('fecha o socket ao desmontar', async () => {
    const { result, unmount } = renderStream()
    const socket = await startAndOpen(result)
    unmount()
    await waitFor(() => expect(socket.closed).toBe(true))
  })
})
