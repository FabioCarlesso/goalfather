import { useEffect, useRef } from 'react'
import type { MatchEvent } from '../domain/types'

interface Props {
  events: MatchEvent[]
  emptyLabel?: string
  className?: string
  /**
   * Resolve `playerId` → nome do jogador. Quando ausente (ou sem a entrada),
   * o feed cai no fallback `#N`. Construído pela página a partir do elenco
   * visível (ex.: clube do usuário).
   */
  playerLookup?: Map<number, string>
}

export function MatchEventFeed({
  events,
  emptyLabel = 'Sem eventos ainda.',
  className = '',
  playerLookup,
}: Props) {
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const el = ref.current
    if (el) el.scrollTop = el.scrollHeight
  }, [events])

  const nameOf = (id: number) => playerLookup?.get(id) ?? `#${id}`

  return (
    <div
      ref={ref}
      className={`overflow-y-auto rounded-lg border border-slate-800 bg-slate-900/40 p-3 space-y-1 font-mono text-sm ${className}`}
    >
      {events.length === 0 ? (
        <p className="text-slate-600 italic">{emptyLabel}</p>
      ) : (
        events.map((event, i) => <EventLine key={i} event={event} nameOf={nameOf} />)
      )}
    </div>
  )
}

// Switch exaustivo sobre o discriminated union MatchEvent.
// Espelha o `when` exaustivo do `sealed interface MatchEvent` do Kotlin.
function EventLine({ event, nameOf }: { event: MatchEvent; nameOf: (id: number) => string }) {
  switch (event.type) {
    case 'KickOff':
      return (
        <Line minute={event.minute} color="text-sky-400">
          ⚡ Bola rolando! ({event.homeClubName} × {event.awayClubName})
        </Line>
      )
    case 'Goal':
      return (
        <Line minute={event.minute} color="text-emerald-400 font-bold">
          ⚽ GOL! {nameOf(event.scorerId)} ({event.home ? 'mandante' : 'visitante'})
        </Line>
      )
    case 'Card':
      return (
        <Line minute={event.minute} color={event.red ? 'text-red-400' : 'text-yellow-300'}>
          {event.red ? '🟥 Vermelho' : '🟨 Amarelo'} — {nameOf(event.playerId)}
        </Line>
      )
    case 'Injury':
      return <Line minute={event.minute} color="text-orange-300">🚑 Lesão — {nameOf(event.playerId)}</Line>
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
