import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { currentRoundKey, usePlayRound } from './useCurrentRound'
import { roundReadinessKey } from './useRoundReadiness'
import { standingsKey } from './useStandings'
import { seasonHistoryKey, clubCareerKey } from './useSeasonHistory'
import { cupKey } from './useCup'
import { boardKey } from './useBoard'
import { errorMessage } from '../errorMessages'
import { wsUrl } from '../wsUrl'
import { handOff } from '../streamHandoff'
import type {
  MatchEvent,
  MatchStats,
  PenaltyScore,
  Round,
  RoundEvent,
  RoundFinance,
  RoundMatch,
  Standings,
  StreamSpeed,
  TrainingReport,
} from '../../domain/types'

// Ciclo de vida do WebSocket do encaixe (`/ws/slot/{key}`, issue #125) e o estado derivado
// dele (issue #112). Saiu da `RoundPage` para ser testável isoladamente: a
// página só desenha o que este hook devolve.

export type RoundStreamStatus = 'idle' | 'connecting' | 'live' | 'finished' | 'error'

// Variante do union RoundEvent — fim de temporada (issue #11).
export type SeasonFinishedEvent = Extract<RoundEvent, { type: 'SeasonFinished' }>
type RoundFinishedEvent = Extract<RoundEvent, { type: 'RoundFinished' }>

/** Mostrado quando o servidor recusa o stream de encaixe não liberado (issue #151). */
export const NOT_STARTED_MESSAGE = 'O encaixe ainda não foi liberado: aguardando a prontidão dos técnicos.'

/** Placar/relógio de uma partida enquanto a rodada é transmitida. */
export interface LiveMatchState {
  homeGoals: number
  awayGoals: number
  minute: number
  status: 'Scheduled' | 'InProgress' | 'Finished'
  // Disputa de pênaltis do jogo de copa (issue #61), quando houve.
  penalties?: PenaltyScore
}

/** O que a rodada jogada deixou para o clube do técnico — pronto no fim do stream. */
export interface RoundOutcome {
  /** A data que foi jogada (não a corrente, que já avançou). */
  played: Round
  /** Tabelas de todas as divisões depois da rodada. */
  standings: Standings[]
  finance: RoundFinance | null
  training: TrainingReport | null
  /** Só na virada de temporada (issue #11). */
  season: SeasonFinishedEvent | null
}

export interface RoundStream {
  status: RoundStreamStatus
  error: string | null
  /** Chama `playRound` e abre o stream da data corrente. */
  start: () => Promise<void>
  /**
   * "Pular para o fim" (issue #115): troca a conexão em andamento por uma
   * `INSTANT`, que reprocessa a rodada desde o início. Não chama `playRound`.
   */
  skip: () => void
  /** Velocidade da conexão em andamento (o seletor só vale para a próxima). */
  liveSpeed: StreamSpeed
  /** Data exibida: a jogada (do início da rodada em diante) ou a corrente. */
  shownRound: Round | undefined
  matchStates: Map<number, LiveMatchState>
  /** Maior minuto entre as partidas — o relógio da rodada. */
  aggregateMinute: number
  allFinished: boolean
  myMatch: RoundMatch | undefined
  myMatchEvents: MatchEvent[]
  /** Sumário da partida do técnico, pronto no FullTime (issue #57). */
  myMatchStats: MatchStats | null
  /** Desfecho da rodada; `null` até ela terminar com o `RoundFinished`. */
  outcome: RoundOutcome | null
}

/**
 * Estado por partida: parte do que veio do REST e aplica os `MatchUpdate` em
 * ordem. Função pura, exportada para teste.
 *
 * Relógio: o backend emite os eventos de TODAS as partidas ordenados por
 * minuto, então o minuto do último evento é o relógio da rodada. Partida em
 * andamento acompanha esse relógio — antes, uma partida sem lance entre os
 * minutos 32 e 46 ficava parada no 32' enquanto as outras andavam.
 */
export function deriveMatchStates(
  matches: readonly RoundMatch[],
  events: readonly RoundEvent[],
): Map<number, LiveMatchState> {
  const states = new Map<number, LiveMatchState>()
  for (const m of matches) {
    states.set(m.matchId, {
      homeGoals: m.homeGoals,
      awayGoals: m.awayGoals,
      minute: m.minute,
      status: m.status,
      ...(m.penalties ? { penalties: m.penalties } : {}),
    })
  }
  let clock = 0
  for (const evt of events) {
    if (evt.type !== 'MatchUpdate') continue
    clock = Math.max(clock, evt.event.minute)
    const cur = states.get(evt.matchId)
    if (!cur) continue
    const next: LiveMatchState = { ...cur, minute: evt.event.minute }
    switch (evt.event.type) {
      case 'KickOff':
        next.status = 'InProgress'
        break
      case 'Goal':
        if (evt.event.home) next.homeGoals++
        else next.awayGoals++
        break
      case 'PenaltyShootout':
        next.penalties = { home: evt.event.homeScore, away: evt.event.awayScore }
        break
      case 'FullTime':
        next.homeGoals = evt.event.homeGoals
        next.awayGoals = evt.event.awayGoals
        next.status = 'Finished'
        break
      // Card, Injury, Save e Miss não afetam placar/status
    }
    states.set(evt.matchId, next)
  }
  for (const [id, st] of states) {
    if (st.status === 'InProgress' && st.minute < clock) states.set(id, { ...st, minute: clock })
  }
  return states
}

/**
 * Solta um socket sem deixar os handlers dele mexerem no estado: o `onclose`
 * de um socket antigo chega assíncrono e marcaria como `finished` a conexão
 * nova que acabou de abrir.
 */
function discard(socket: WebSocket | null) {
  if (!socket) return
  socket.onopen = null
  socket.onmessage = null
  socket.onerror = null
  socket.onclose = null
  socket.close()
}

export function useRoundStream(
  round: Round | undefined,
  myClubId: number,
  speed: StreamSpeed = 'NORMAL',
): RoundStream {
  const qc = useQueryClient()
  const playRound = usePlayRound()

  const [events, setEvents] = useState<RoundEvent[]>([])
  const [status, setStatus] = useState<RoundStreamStatus>('idle')
  const [error, setError] = useState<string | null>(null)
  const [finalStandings, setFinalStandings] = useState<Standings[] | null>(null)
  const [finalFinance, setFinalFinance] = useState<RoundFinance | null>(null)
  const [training, setTraining] = useState<TrainingReport | null>(null)
  const [champion, setChampion] = useState<SeasonFinishedEvent | null>(null)
  // A data JOGADA (issue #61; partidas incluídas desde a #112). Guardada no
  // início porque o RoundFinished invalida a rodada corrente: dali em diante
  // `round` já é a PRÓXIMA data, e o resumo falaria da fase de copa seguinte
  // em vez da rodada de liga que acabou de ser jogada — e a grade e o feed
  // mostrariam partidas que ainda nem começaram.
  const [playedRound, setPlayedRound] = useState<Round | null>(null)
  const [liveSpeed, setLiveSpeed] = useState<StreamSpeed>(speed)

  const wsRef = useRef<WebSocket | null>(null)

  const onRoundFinished = useCallback(
    (event: RoundFinishedEvent) => {
      setFinalStandings(event.standings)
      setFinalFinance(event.finances?.find((f) => f.clubId === myClubId) ?? null)
      // Empurra a nova tabela direto no cache (sem refetch desnecessário)
      qc.setQueryData(standingsKey, event.standings)
      // Próxima rodada já está pronta no backend mock — invalida para buscar
      qc.invalidateQueries({ queryKey: currentRoundKey })
      // A prontidão foi resetada no servidor — rebusca para a próxima rodada (issue #20)
      qc.invalidateQueries({ queryKey: roundReadinessKey })
      // Data de copa avança a chave (issue #61); data de liga pode ter
      // sido a última antes da fase seguinte. Rebusca sempre — é barato.
      qc.invalidateQueries({ queryKey: cupKey })
      // Rodada de liga mexe na confiança da diretoria (issue #62); na
      // virada, a meta também é outra.
      qc.invalidateQueries({ queryKey: boardKey(myClubId) })
      // O relógio andou um encaixe (issue #125): data corrente e próximo jogo.
      qc.invalidateQueries({ queryKey: ['calendar'] })
      // Treino da semana (issue #58): o evento traz o extrato de todos os
      // clubes; só o do técnico interessa aqui. Fica DEPOIS das
      // invalidações e com acesso opcional de propósito: um payload sem
      // `training`/`finances` (backend fora de sincronia com o contrato)
      // custa o card, não o avanço da rodada — o `catch` do onmessage só
      // loga, então o que estivesse abaixo do throw nunca rodaria.
      setTraining(event.training?.find((t) => t.clubId === myClubId) ?? null)
    },
    [qc, myClubId],
  )

  const onSeasonFinished = useCallback(
    (event: SeasonFinishedEvent) => {
      // Fim de temporada (issue #11): celebra o campeão. O backend já
      // abriu a próxima temporada; a invalidação do RoundFinished (que vem
      // logo a seguir) faz a UI buscar a rodada 1 da nova temporada.
      setChampion(event)
      setFinalStandings(event.standings)
      qc.setQueryData(standingsKey, event.standings)
      // A temporada que acabou virou história (issue #60): o record já foi
      // gravado no servidor, então a tela de Histórico precisa rebuscar.
      qc.invalidateQueries({ queryKey: seasonHistoryKey })
      qc.invalidateQueries({ queryKey: clubCareerKey(myClubId) })
    },
    [qc, myClubId],
  )

  const handleEvent = useCallback(
    (event: RoundEvent) => {
      switch (event.type) {
        case 'RoundNotStarted':
          // Encaixe ainda não liberado (issue #151): o servidor não simulou
          // nada e vai fechar a conexão em seguida. Volta ao "aguardando
          // prontidão" — `idle` faz o `onclose` não marcar `finished`, e a
          // data exibida volta a ser a corrente.
          setPlayedRound(null)
          setStatus('idle')
          setError(NOT_STARTED_MESSAGE)
          qc.invalidateQueries({ queryKey: roundReadinessKey })
          qc.invalidateQueries({ queryKey: currentRoundKey })
          return
        case 'MatchUpdate':
          setEvents((prev) => [...prev, event])
          return
        case 'RoundFinished':
          setEvents((prev) => [...prev, event])
          onRoundFinished(event)
          return
        case 'SeasonFinished':
          setEvents((prev) => [...prev, event])
          onSeasonFinished(event)
          return
        default: {
          // Exaustividade: variante nova no contrato quebra a compilação aqui.
          const unhandled: never = event
          console.error('RoundEvent desconhecido', unhandled)
        }
      }
    },
    [qc, onRoundFinished, onSeasonFinished],
  )

  /** Descarta o que o stream anterior acumulou (eventos, erro, desfecho). */
  const resetStream = useCallback(() => {
    setEvents([])
    setError(null)
    setFinalStandings(null)
    setFinalFinance(null)
    setTraining(null)
    setChampion(null)
  }, [])

  /**
   * Abre o stream do encaixe `key` (issue #125) do zero: solta o socket anterior,
   * descarta o estado acumulado e reprocessa tudo o que chegar. Ponto ÚNICO
   * onde a URL do WebSocket é montada.
   *
   * Com `keepPrevious` (o "Pular para o fim" da #115) o socket anterior não
   * fecha já: fica mudo e só fecha quando o novo terminar — ver `handOff`,
   * que explica a corrida pelo `RoundFinished` completo no backend.
   */
  const openStream = useCallback(
    (key: string, connectionSpeed: StreamSpeed, keepPrevious = false) => {
      const previous = wsRef.current
      if (!keepPrevious) discard(previous)
      resetStream()
      setStatus('connecting')
      setLiveSpeed(connectionSpeed)

      const socket = new WebSocket(wsUrl(`/ws/slot/${key}`, { speed: connectionSpeed }))
      wsRef.current = socket
      if (keepPrevious) handOff(previous, socket)

      socket.onopen = () => setStatus('live')
      socket.onmessage = (e) => {
        try {
          handleEvent(JSON.parse(e.data as string) as RoundEvent)
        } catch (err) {
          console.error('Falha ao parsear RoundEvent', err)
        }
      }
      socket.onerror = () => {
        setError('Erro de conexão com o servidor de rodada')
        setStatus('error')
      }
      socket.onclose = (e) => {
        // `idle` aqui = o servidor recusou com `RoundNotStarted` (issue #151):
        // a rodada não foi jogada, então nada de `finished`.
        setStatus((prev) => (prev === 'error' || prev === 'idle' ? prev : 'finished'))
        if (e.code !== 1000 && e.code !== 1005 && e.reason) setError(e.reason)
      }
    },
    [handleEvent, resetStream],
  )

  const start = useCallback(async () => {
    if (!round) return
    discard(wsRef.current)
    wsRef.current = null
    resetStream()
    setPlayedRound(round)
    setStatus('connecting')

    try {
      await playRound.mutateAsync()
    } catch (e) {
      setError(`Falha ao iniciar rodada: ${errorMessage(e)}`)
      setStatus('error')
      return
    }

    openStream(round.matchday.key, speed)
  }, [round, playRound, resetStream, openStream, speed])

  const skip = useCallback(() => {
    // Só faz sentido com uma transmissão em andamento da data jogada.
    if (!playedRound || !wsRef.current) return
    openStream(playedRound.matchday.key, 'INSTANT', true)
  }, [playedRound, openStream])

  useEffect(() => () => discard(wsRef.current), [])

  // ─── Derivações ─────────────────────────────────────────────────────
  const shownRound = playedRound ?? round

  const matchStates = useMemo(
    () => deriveMatchStates(shownRound?.matches ?? [], events),
    [shownRound, events],
  )

  const aggregateMinute = useMemo(() => {
    const values = Array.from(matchStates.values())
    if (values.length === 0) return 0
    return Math.max(...values.map((s) => s.minute))
  }, [matchStates])

  const allFinished =
    shownRound != null && Array.from(matchStates.values()).every((s) => s.status === 'Finished')

  const myMatch = useMemo(
    () => shownRound?.matches.find((m) => m.homeClubId === myClubId || m.awayClubId === myClubId),
    [shownRound, myClubId],
  )

  const myMatchEvents = useMemo<MatchEvent[]>(() => {
    if (!myMatch) return []
    const out: MatchEvent[] = []
    for (const evt of events) {
      if (evt.type === 'MatchUpdate' && evt.matchId === myMatch.matchId) out.push(evt.event)
    }
    return out
  }, [events, myMatch])

  const myMatchStats = useMemo(() => {
    const fullTime = myMatchEvents.find((e) => e.type === 'FullTime')
    return fullTime?.type === 'FullTime' ? fullTime.stats : null
  }, [myMatchEvents])

  const outcome = useMemo<RoundOutcome | null>(
    () =>
      status === 'finished' && playedRound && finalStandings
        ? {
            played: playedRound,
            standings: finalStandings,
            finance: finalFinance,
            training,
            season: champion,
          }
        : null,
    [status, playedRound, finalStandings, finalFinance, training, champion],
  )

  return {
    status,
    error,
    start,
    skip,
    liveSpeed,
    shownRound,
    matchStates,
    aggregateMinute,
    allFinished,
    myMatch,
    myMatchEvents,
    myMatchStats,
    outcome,
  }
}
