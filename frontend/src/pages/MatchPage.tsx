import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { MatchEvent } from '../domain/types'

type Status = 'idle' | 'connecting' | 'live' | 'finished' | 'error'

interface Teams {
  home: string
  away: string
  homeStrength: number
  awayStrength: number
}

export function MatchPage() {
  const [events, setEvents] = useState<MatchEvent[]>([])
  const [status, setStatus] = useState<Status>('idle')
  const [error, setError] = useState<string | null>(null)
  const wsRef = useRef<WebSocket | null>(null)
  const feedRef = useRef<HTMLDivElement>(null)

  // ─── Derivados dos eventos ──────────────────────────────────────────────
  const teams: Teams | null = useMemo(() => {
    const kickOff = events.find((e) => e.type === 'KickOff')
    if (!kickOff || kickOff.type !== 'KickOff') return null
    return {
      home: kickOff.homeClubName,
      away: kickOff.awayClubName,
      homeStrength: kickOff.homeStrength,
      awayStrength: kickOff.awayStrength,
    }
  }, [events])

  const score = useMemo(() => {
    return events.reduce(
      (acc, e) => {
        if (e.type === 'Goal') {
          if (e.home) acc.home++
          else acc.away++
        }
        return acc
      },
      { home: 0, away: 0 },
    )
  }, [events])

  const lastMinute = events.length > 0 ? events[events.length - 1]!.minute : 0

  // ─── Ciclo de vida do WebSocket ─────────────────────────────────────────
  const startMatch = useCallback(() => {
    wsRef.current?.close()
    setEvents([])
    setError(null)
    setStatus('connecting')

    const matchId = Date.now()
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const socket = new WebSocket(`${protocol}//${window.location.host}/ws/matches/${matchId}`)
    wsRef.current = socket

    socket.onopen    = () => setStatus('live')
    socket.onmessage = (e) => {
      try {
        const event = JSON.parse(e.data as string) as MatchEvent
        setEvents((prev) => [...prev, event])
      } catch (err) {
        console.error('Falha ao parsear evento', err)
      }
    }
    socket.onerror = () => {
      setError('Erro de conexão com o servidor de partida')
      setStatus('error')
    }
    socket.onclose = (e) => {
      setStatus((prev) => (prev === 'error' ? 'error' : 'finished'))
      if (e.code !== 1000 && e.code !== 1005 && e.reason) setError(e.reason)
    }
  }, [])

  useEffect(() => {
    return () => wsRef.current?.close()
  }, [])

  useEffect(() => {
    const el = feedRef.current
    if (el) el.scrollTop = el.scrollHeight
  }, [events])

  const busy = status === 'connecting' || status === 'live'

  return (
    <section className="space-y-4">
      <header className="flex items-center justify-between">
        <h1 className="text-3xl font-bold text-slate-100">Partida</h1>
        <button
          onClick={startMatch}
          disabled={busy}
          className="rounded-md bg-emerald-600 hover:bg-emerald-500 disabled:bg-slate-700 disabled:cursor-not-allowed text-white px-4 py-2 text-sm font-medium transition-colors"
        >
          {status === 'idle'       && 'Iniciar partida'}
          {status === 'connecting' && 'Conectando…'}
          {status === 'live'       && 'Em andamento…'}
          {status === 'finished'   && 'Jogar nova partida'}
          {status === 'error'      && 'Tentar novamente'}
        </button>
      </header>

      {error && (
        <div className="rounded-lg border border-red-700/50 bg-red-900/20 p-3 text-sm text-red-300">
          {error}
        </div>
      )}

      <Scoreboard teams={teams} score={score} minute={lastMinute} status={status} />

      <div
        ref={feedRef}
        className="h-80 overflow-y-auto rounded-lg border border-slate-800 bg-slate-900/40 p-3 space-y-1 font-mono text-sm"
      >
        {events.length === 0 ? (
          <p className="text-slate-600 italic">Clique em "Iniciar partida" para começar.</p>
        ) : (
          events.map((event, i) => <EventLine key={i} event={event} />)
        )}
      </div>
    </section>
  )
}

// ─── Scoreboard ───────────────────────────────────────────────────────────
function Scoreboard({
  teams,
  score,
  minute,
  status,
}: {
  teams: Teams | null
  score: { home: number; away: number }
  minute: number
  status: Status
}) {
  // Placeholder enquanto o KickOff ainda não chegou
  if (!teams) {
    return (
      <div className="rounded-lg border border-slate-800 bg-slate-900/40 p-6 text-center text-slate-600 italic">
        Aguardando início da partida…
      </div>
    )
  }

  const minuteLabel = status === 'finished' ? 'FIM' : `${minute}'`
  const progress = Math.min(100, (minute / 90) * 100)

  return (
    <div className="rounded-lg border border-slate-800 bg-gradient-to-b from-slate-900/80 to-slate-950 p-5">
      <div className="grid grid-cols-[1fr_auto_1fr] items-center gap-4">
        <TeamSide name={teams.home} strength={teams.homeStrength} align="right" />
        <div className="flex flex-col items-center gap-1 min-w-[140px]">
          <div className="text-5xl font-bold text-slate-100 font-mono tabular-nums">
            {score.home} <span className="text-slate-600">×</span> {score.away}
          </div>
          <div className={`text-xs font-mono tracking-widest ${status === 'live' ? 'text-emerald-400' : 'text-slate-500'}`}>
            {status === 'live' && <span className="inline-block w-1.5 h-1.5 rounded-full bg-emerald-400 mr-1.5 animate-pulse" />}
            {minuteLabel}
          </div>
        </div>
        <TeamSide name={teams.away} strength={teams.awayStrength} align="left" />
      </div>

      {/* Barra de progresso da partida (0–90') */}
      <div className="mt-4 h-1 rounded-full bg-slate-800 overflow-hidden">
        <div
          className={`h-full transition-all duration-300 ${status === 'finished' ? 'bg-slate-500' : 'bg-emerald-500'}`}
          style={{ width: `${progress}%` }}
        />
      </div>
    </div>
  )
}

function TeamSide({
  name,
  strength,
  align,
}: { name: string; strength: number; align: 'left' | 'right' }) {
  return (
    <div className={align === 'right' ? 'text-right' : 'text-left'}>
      <div className="text-lg font-semibold text-slate-100 truncate">{name}</div>
      <div className="text-xs text-slate-500 font-mono">OVR {strength.toFixed(1)}</div>
    </div>
  )
}

// ─── Feed de eventos (switch exaustivo sobre MatchEvent) ──────────────────
function EventLine({ event }: { event: MatchEvent }) {
  switch (event.type) {
    case 'KickOff':
      return <Line minute={event.minute} color="text-sky-400">⚡ Bola rolando! ({event.homeClubName} × {event.awayClubName})</Line>
    case 'Goal':
      return (
        <Line minute={event.minute} color="text-emerald-400 font-bold">
          ⚽ GOL! ({event.home ? 'mandante' : 'visitante'}) — jogador #{event.scorerId}
        </Line>
      )
    case 'Card':
      return (
        <Line minute={event.minute} color={event.red ? 'text-red-400' : 'text-yellow-300'}>
          {event.red ? '🟥 Vermelho' : '🟨 Amarelo'} — jogador #{event.playerId}
        </Line>
      )
    case 'Injury':
      return <Line minute={event.minute} color="text-orange-300">🚑 Lesão — jogador #{event.playerId}</Line>
    case 'Save':
      return <Line minute={event.minute} color="text-slate-300">🧤 Defesa difícil</Line>
    case 'FullTime':
      return (
        <Line minute={event.minute} color="text-slate-100 font-semibold">
          🏁 Fim de jogo — {event.homeGoals} × {event.awayGoals}
        </Line>
      )
  }
}

function Line({
  minute,
  color,
  children,
}: { minute: number; color: string; children: React.ReactNode }) {
  return (
    <div className="flex gap-3">
      <span className="w-10 text-slate-500 tabular-nums">{minute}'</span>
      <span className={color}>{children}</span>
    </div>
  )
}
