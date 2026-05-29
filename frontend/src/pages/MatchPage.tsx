import { useCallback, useEffect, useRef, useState } from 'react'
import type { MatchEvent } from '../domain/types'

type Status = 'idle' | 'connecting' | 'live' | 'finished' | 'error'

export function MatchPage() {
  const [events, setEvents] = useState<MatchEvent[]>([])
  const [status, setStatus] = useState<Status>('idle')
  const [error, setError] = useState<string | null>(null)
  const wsRef = useRef<WebSocket | null>(null)
  const feedRef = useRef<HTMLDivElement>(null)

  const startMatch = useCallback(() => {
    wsRef.current?.close()
    setEvents([])
    setError(null)
    setStatus('connecting')

    const matchId = Date.now()
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const socket = new WebSocket(`${protocol}//${window.location.host}/ws/matches/${matchId}`)
    wsRef.current = socket

    socket.onopen = () => setStatus('live')

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
      if (e.code !== 1000 && e.code !== 1005 && e.reason) {
        setError(e.reason)
      }
    }
  }, [])

  useEffect(() => {
    return () => wsRef.current?.close()
  }, [])

  // Auto-scroll do feed
  useEffect(() => {
    const el = feedRef.current
    if (el) el.scrollTop = el.scrollHeight
  }, [events])

  const finalEvent = events.find((e) => e.type === 'FullTime')
  const lastMinute = events.length > 0 ? events[events.length - 1]!.minute : 0
  const busy = status === 'connecting' || status === 'live'

  return (
    <section>
      <header className="mb-4 flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-slate-100">Partida</h1>
          <p className="text-sm text-slate-400">
            Stream ao vivo via WebSocket {status === 'live' && `· ${lastMinute}'`}
          </p>
        </div>
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

      {finalEvent && finalEvent.type === 'FullTime' && (
        <div className="mb-4 rounded-lg border border-emerald-700/50 bg-emerald-900/20 p-4 text-center">
          <div className="text-xs uppercase tracking-wide text-emerald-300">Resultado final</div>
          <div className="text-3xl font-bold text-slate-100 mt-1 font-mono">
            {finalEvent.homeGoals} <span className="text-slate-500">×</span> {finalEvent.awayGoals}
          </div>
        </div>
      )}

      {error && (
        <div className="mb-4 rounded-lg border border-red-700/50 bg-red-900/20 p-3 text-sm text-red-300">
          {error}
        </div>
      )}

      <div
        ref={feedRef}
        className="h-96 overflow-y-auto rounded-lg border border-slate-800 bg-slate-900/40 p-3 space-y-1 font-mono text-sm"
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

// Demonstração do discriminated union: TS exige que todos os branches do
// `switch` sejam cobertos. Mesma forma do `when` exaustivo do Kotlin sobre
// `sealed interface MatchEvent`.
function EventLine({ event }: { event: MatchEvent }) {
  switch (event.type) {
    case 'KickOff':
      return <Line minute={event.minute} color="text-sky-400">⚡ Bola rolando!</Line>
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
      return (
        <Line minute={event.minute} color="text-orange-300">
          🚑 Lesão — jogador #{event.playerId}
        </Line>
      )
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
