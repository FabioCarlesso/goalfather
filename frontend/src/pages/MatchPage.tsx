import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useClub } from '../api/queries/useClub'
import { MatchEventFeed } from '../components/MatchEventFeed'
import type { MatchEvent } from '../domain/types'

const MY_CLUB_ID = 1

type Status = 'connecting' | 'live' | 'finished' | 'error'

interface Teams {
  home: string
  away: string
  homeStrength: number
  awayStrength: number
}

/**
 * Drill-down de uma partida específica (issue #9). Conecta em
 * `/ws/matches/{id}` ao montar e renderiza placar + feed ao vivo. Os
 * nomes dos jogadores do clube do usuário são resolvidos pelo lookup.
 */
export function MatchPage() {
  const { matchId } = useParams<{ matchId: string }>()
  const { data: myClub } = useClub(MY_CLUB_ID)

  const [events, setEvents] = useState<MatchEvent[]>([])
  const [status, setStatus] = useState<Status>('connecting')
  const [error, setError] = useState<string | null>(null)
  const wsRef = useRef<WebSocket | null>(null)

  const playerLookup = useMemo(
    () => new Map<number, string>(myClub?.squad.map((p) => [p.id, p.name]) ?? []),
    [myClub],
  )

  // Conecta ao montar / quando o matchId muda.
  useEffect(() => {
    if (!matchId) return
    setEvents([])
    setError(null)
    setStatus('connecting')

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const socket = new WebSocket(`${protocol}//${window.location.host}/ws/matches/${matchId}`)
    wsRef.current = socket

    socket.onopen = () => setStatus('live')
    socket.onmessage = (e) => {
      try {
        setEvents((prev) => [...prev, JSON.parse(e.data as string) as MatchEvent])
      } catch (err) {
        console.error('Falha ao parsear MatchEvent', err)
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

    return () => socket.close()
  }, [matchId])

  const teams = useMemo<Teams | null>(() => {
    const kickOff = events.find((e) => e.type === 'KickOff')
    if (!kickOff || kickOff.type !== 'KickOff') return null
    return {
      home: kickOff.homeClubName,
      away: kickOff.awayClubName,
      homeStrength: kickOff.homeStrength,
      awayStrength: kickOff.awayStrength,
    }
  }, [events])

  const score = useMemo(
    () =>
      events.reduce(
        (acc, e) => {
          if (e.type === 'Goal') (e.home ? acc.home++ : acc.away++)
          return acc
        },
        { home: 0, away: 0 },
      ),
    [events],
  )

  const lastMinute = events.length > 0 ? events[events.length - 1]!.minute : 0

  return (
    <section className="space-y-4">
      <header className="flex items-center justify-between">
        <h1 className="text-3xl font-bold text-slate-100">Partida</h1>
        <Link to="/round" className="text-sm text-slate-400 hover:text-slate-200">
          ← Voltar para a rodada
        </Link>
      </header>

      {error && (
        <div className="rounded-lg border border-red-700/50 bg-red-900/20 p-3 text-sm text-red-300">
          {error}
        </div>
      )}

      <Scoreboard teams={teams} score={score} minute={lastMinute} status={status} />

      <MatchEventFeed
        events={events}
        playerLookup={playerLookup}
        emptyLabel={status === 'connecting' ? 'Conectando…' : 'Aguardando início…'}
        className="h-80"
      />
    </section>
  )
}

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
  if (!teams) {
    return (
      <div className="rounded-lg border border-slate-800 bg-slate-900/40 p-6 text-center italic text-slate-600">
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
        <div className="flex min-w-[140px] flex-col items-center gap-1">
          <div className="font-mono text-5xl font-bold tabular-nums text-slate-100">
            {score.home} <span className="text-slate-600">×</span> {score.away}
          </div>
          <div className={`font-mono text-xs tracking-widest ${status === 'live' ? 'text-emerald-400' : 'text-slate-500'}`}>
            {status === 'live' && <span className="mr-1.5 inline-block h-1.5 w-1.5 animate-pulse rounded-full bg-emerald-400" />}
            {minuteLabel}
          </div>
        </div>
        <TeamSide name={teams.away} strength={teams.awayStrength} align="left" />
      </div>
      <div className="mt-4 h-1 overflow-hidden rounded-full bg-slate-800">
        <div
          className={`h-full transition-all duration-300 ${status === 'finished' ? 'bg-slate-500' : 'bg-emerald-500'}`}
          style={{ width: `${progress}%` }}
        />
      </div>
    </div>
  )
}

function TeamSide({ name, strength, align }: { name: string; strength: number; align: 'left' | 'right' }) {
  return (
    <div className={align === 'right' ? 'text-right' : 'text-left'}>
      <div className="truncate text-lg font-semibold text-slate-100">{name}</div>
      <div className="font-mono text-xs text-slate-500">OVR {strength.toFixed(1)}</div>
    </div>
  )
}
