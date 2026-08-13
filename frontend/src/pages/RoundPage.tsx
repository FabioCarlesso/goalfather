import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { useCurrentRound, usePlayRound, currentRoundKey } from '../api/queries/useCurrentRound'
import { useRoundReadiness, useMarkReady, roundReadinessKey } from '../api/queries/useRoundReadiness'
import { useClub } from '../api/queries/useClub'
import { wsUrl } from '../api/wsUrl'
import { useMyClubId } from '../auth/useMyClubId'
import { useAuth } from '../auth/AuthContext'
import { standingsKey } from '../api/queries/useStandings'
import { MatchEventFeed } from '../components/MatchEventFeed'
import { MatchStatsSummary } from '../components/MatchStatsSummary'
import { ReadinessCard } from '../components/ReadinessCard'
import { formatMoney } from '../domain/formatters'
import { TRAINED_ATTRIBUTE_LABEL, TRAINING_FOCUS_LABEL } from '../domain/training'
import type {
  MatchEvent,
  Retirement,
  RoundEvent,
  RoundFinance,
  RoundMatch,
  StandingRow,
  Standings,
  TrainingReport,
} from '../domain/types'

// Variante do union RoundEvent — fim de temporada (issue #11).
type SeasonFinishedEvent = Extract<RoundEvent, { type: 'SeasonFinished' }>

type Status = 'idle' | 'connecting' | 'live' | 'finished' | 'error'

interface LiveMatchState {
  homeGoals: number
  awayGoals: number
  minute: number
  status: 'Scheduled' | 'InProgress' | 'Finished'
}

export function RoundPage() {
  const qc = useQueryClient()
  const myClubId = useMyClubId()
  const { user } = useAuth()
  const { data: round, isLoading } = useCurrentRound()
  const { data: myClub } = useClub(myClubId)
  const playRound = usePlayRound()

  // Lookup id → nome a partir do elenco do usuário, para o feed mostrar
  // nomes em vez de "#N" (issue #7). Só resolve a partida do usuário; os
  // jogadores dos outros clubes seguem como "#N" (fora de escopo).
  const playerLookup = useMemo(
    () => new Map<number, string>(myClub?.squad.map((p) => [p.id, p.name]) ?? []),
    [myClub],
  )

  const [events, setEvents] = useState<RoundEvent[]>([])
  const [status, setStatus] = useState<Status>('idle')
  const [error, setError] = useState<string | null>(null)
  const [finalStandings, setFinalStandings] = useState<Standings[] | null>(null)
  const [finalFinance, setFinalFinance] = useState<RoundFinance | null>(null)
  const [training, setTraining] = useState<TrainingReport | null>(null)
  const [champion, setChampion] = useState<SeasonFinishedEvent | null>(null)

  // Prontidão da liga compartilhada (issue #20). Durante a partida ao vivo o
  // card some, então pausamos o polling de prontidão para não gerar tráfego
  // ocioso (issue #20, ponto #7) — só volta a buscar quando sai do "live".
  const live = status === 'live' || status === 'connecting'
  const { data: readiness } = useRoundReadiness({ paused: live })
  const markReady = useMarkReady()

  // Sou eu pronto = meu username NÃO está na lista de pendentes. Exige `user`
  // já carregado: sem o guard `user?.username != null`, um username vazio
  // ficaria fora de `pendingUsernames` e marcaria `amIReady` como `true`
  // incorretamente enquanto a auth ainda não resolveu (issue #20, ponto #4).
  const amIReady =
    readiness != null &&
    user?.username != null &&
    !readiness.pendingUsernames.includes(user.username)
  const allReady =
    readiness != null && readiness.totalCount > 0 && readiness.readyCount >= readiness.totalCount
  // Escape hatch (issue #45): pode jogar com todos prontos OU quando o timeout
  // expira (ausentes entram com a última escalação salva).
  const canStart = allReady || readiness?.timedOut === true

  const wsRef = useRef<WebSocket | null>(null)

  // ─── Estado por partida derivado dos eventos ─────────────────────────
  const matchStates: Map<number, LiveMatchState> = useMemo(() => {
    const init = new Map<number, LiveMatchState>()
    if (round) {
      for (const m of round.matches) {
        init.set(m.matchId, {
          homeGoals: m.homeGoals,
          awayGoals: m.awayGoals,
          minute: m.minute,
          status: m.status,
        })
      }
    }
    for (const evt of events) {
      if (evt.type !== 'MatchUpdate') continue
      const cur = init.get(evt.matchId)
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
        case 'FullTime':
          next.homeGoals = evt.event.homeGoals
          next.awayGoals = evt.event.awayGoals
          next.status = 'Finished'
          break
        // Card, Injury, Save e Miss não afetam placar/status
      }
      init.set(evt.matchId, next)
    }
    return init
  }, [events, round])

  const aggregateMinute = useMemo(() => {
    const values = Array.from(matchStates.values())
    if (values.length === 0) return 0
    return Math.max(...values.map((s) => s.minute))
  }, [matchStates])

  const allFinished = round != null && Array.from(matchStates.values()).every((s) => s.status === 'Finished')

  // ─── Partida do usuário + feed filtrado ──────────────────────────────
  const myMatch = useMemo<RoundMatch | undefined>(
    () => round?.matches.find((m) => m.homeClubId === myClubId || m.awayClubId === myClubId),
    [round, myClubId],
  )

  const myMatchEvents = useMemo<MatchEvent[]>(() => {
    if (!myMatch) return []
    const out: MatchEvent[] = []
    for (const evt of events) {
      if (evt.type === 'MatchUpdate' && evt.matchId === myMatch.matchId) {
        out.push(evt.event)
      }
    }
    return out
  }, [events, myMatch])

  // Sumário da partida do usuário, pronto no FullTime (issue #57).
  const myMatchStats = useMemo(() => {
    const fullTime = myMatchEvents.find((e) => e.type === 'FullTime')
    return fullTime?.type === 'FullTime' ? fullTime.stats : null
  }, [myMatchEvents])

  // ─── Ciclo de vida do WebSocket ──────────────────────────────────────
  const startRound = useCallback(async () => {
    if (!round) return
    wsRef.current?.close()
    setEvents([])
    setError(null)
    setFinalStandings(null)
    setFinalFinance(null)
    setTraining(null)
    setChampion(null)
    setStatus('connecting')

    try {
      await playRound.mutateAsync()
    } catch (e) {
      setError(`Falha ao iniciar rodada: ${String(e)}`)
      setStatus('error')
      return
    }

    const socket = new WebSocket(wsUrl(`/ws/round/${round.number}`))
    wsRef.current = socket

    socket.onopen = () => setStatus('live')
    socket.onmessage = (e) => {
      try {
        const event = JSON.parse(e.data as string) as RoundEvent
        setEvents((prev) => [...prev, event])
        if (event.type === 'RoundFinished') {
          setFinalStandings(event.standings)
          setFinalFinance(event.finances.find((f) => f.clubId === myClubId) ?? null)
          // Treino da semana (issue #58): o evento traz o extrato de todos os
          // clubes; só o do técnico interessa aqui.
          setTraining(event.training.find((t) => t.clubId === myClubId) ?? null)
          // Empurra a nova tabela direto no cache (sem refetch desnecessário)
          qc.setQueryData(standingsKey, event.standings)
          // Próxima rodada já está pronta no backend mock — invalida para buscar
          qc.invalidateQueries({ queryKey: currentRoundKey })
          // A prontidão foi resetada no servidor — rebusca para a próxima rodada (issue #20)
          qc.invalidateQueries({ queryKey: roundReadinessKey })
        } else if (event.type === 'SeasonFinished') {
          // Fim de temporada (issue #11): celebra o campeão. O backend já
          // abriu a próxima temporada; a invalidação do RoundFinished (que vem
          // logo a seguir) faz a UI buscar a rodada 1 da nova temporada.
          setChampion(event)
          setFinalStandings(event.standings)
          qc.setQueryData(standingsKey, event.standings)
        }
      } catch (err) {
        console.error('Falha ao parsear RoundEvent', err)
      }
    }
    socket.onerror = () => {
      setError('Erro de conexão com o servidor de rodada')
      setStatus('error')
    }
    socket.onclose = (e) => {
      setStatus((prev) => (prev === 'error' ? 'error' : 'finished'))
      if (e.code !== 1000 && e.code !== 1005 && e.reason) setError(e.reason)
    }
  }, [round, playRound, qc, myClubId])

  useEffect(() => {
    return () => wsRef.current?.close()
  }, [])

  if (isLoading || !round) return <p className="text-slate-400">Carregando rodada…</p>

  const busy = status === 'connecting' || status === 'live'
  const buttonLabel =
    status === 'idle'       ? 'Jogar rodada' :
    status === 'connecting' ? 'Iniciando…' :
    status === 'live'       ? 'Rodada em andamento…' :
    status === 'finished'   ? 'Próxima rodada' :
                              'Tentar novamente'

  return (
    <section className="space-y-4">
      <header className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-slate-100">Rodada {round.number}</h1>
          <p className="text-sm text-slate-400">Temporada {round.season} · {round.matches.length} partidas</p>
        </div>
        <button
          onClick={startRound}
          disabled={busy || !canStart}
          title={!canStart ? 'Aguardando técnicos sinalizarem prontos (ou o timeout expirar)' : undefined}
          className="rounded-md bg-emerald-600 hover:bg-emerald-500 disabled:bg-slate-700 disabled:cursor-not-allowed text-white px-4 py-2 text-sm font-medium transition-colors"
        >
          {buttonLabel}
        </button>
      </header>

      {/* Lobby da rodada compartilhada (issue #20): todos prontos → libera jogar. */}
      {readiness && status !== 'live' && status !== 'connecting' && (
        <ReadinessCard
          readyCount={readiness.readyCount}
          totalCount={readiness.totalCount}
          pendingUsernames={readiness.pendingUsernames}
          amIReady={amIReady}
          secondsRemaining={readiness.secondsRemaining}
          timedOut={readiness.timedOut}
          onReady={() => markReady.mutate()}
          marking={markReady.isPending}
        />
      )}

      {error && (
        <div className="rounded-lg border border-red-700/50 bg-red-900/20 p-3 text-sm text-red-300">
          {error}
        </div>
      )}

      <RoundProgress
        minute={aggregateMinute}
        status={status}
        allFinished={allFinished && status === 'finished'}
      />

      {/* Partidas agrupadas por divisão (issue #47). */}
      {divisionsOf(round.matches).map(([division, matches]) => (
        <div key={division} className="space-y-2">
          <h2 className="text-sm uppercase tracking-wide text-slate-500">Divisão {division}</h2>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
            {matches.map((m) => (
              <MatchCard
                key={m.matchId}
                match={m}
                state={matchStates.get(m.matchId)}
                highlight={m.homeClubId === myClubId || m.awayClubId === myClubId}
              />
            ))}
          </div>
        </div>
      ))}

      {myMatch && (
        <div className="space-y-2">
          <h2 className="text-sm uppercase tracking-wide text-slate-500">
            Detalhes da sua partida — {myMatch.homeClubName} × {myMatch.awayClubName}
          </h2>
          <MatchEventFeed
            events={myMatchEvents}
            playerLookup={playerLookup}
            emptyLabel={status === 'idle' ? 'Clique em "Jogar rodada" para começar.' : 'Aguardando início…'}
            className="h-72"
          />
          {myMatchStats && (
            <MatchStatsSummary
              stats={myMatchStats}
              homeName={myMatch.homeClubName}
              awayName={myMatch.awayClubName}
            />
          )}
        </div>
      )}

      {champion && status === 'finished' && (
        <ChampionBanner season={champion.season} champion={champion.champion} />
      )}

      {/* Aposentadorias do clube do usuário na virada (issue #55). */}
      {champion && status === 'finished' && (
        <RetirementsCard retirements={champion.retirements.filter((r) => r.clubId === myClubId)} />
      )}

      {/* Resultado do treino da semana no clube do usuário (issue #58). */}
      {training && status === 'finished' && <TrainingCard report={training} />}

      {finalStandings && status === 'finished' && (
        <FinalBanner
          standings={myDivisionTable(finalStandings, myClubId)}
          round={round.number}
          finance={finalFinance}
        />
      )}
    </section>
  )
}

/** Pares [divisão, partidas] na ordem da elite para baixo. */
function divisionsOf(matches: RoundMatch[]): Array<[number, RoundMatch[]]> {
  const grouped = new Map<number, RoundMatch[]>()
  for (const m of matches) {
    const list = grouped.get(m.division) ?? []
    list.push(m)
    grouped.set(m.division, list)
  }
  return [...grouped.entries()].sort(([a], [b]) => a - b)
}

/** Tabela da divisão do usuário (fallback: elite) para o banner de fim de rodada. */
function myDivisionTable(tables: Standings[], myClubId: number): Standings {
  return tables.find((t) => t.rows.some((r) => r.clubId === myClubId)) ?? tables[0]!
}

function ChampionBanner({ season, champion }: { season: number; champion: StandingRow }) {
  return (
    <div className="rounded-lg border border-amber-500/60 bg-gradient-to-r from-amber-900/30 to-amber-700/20 p-5 text-center">
      <div className="text-4xl mb-1">🏆</div>
      <div className="text-xs uppercase tracking-widest text-amber-300/80">Fim da temporada {season}</div>
      <div className="mt-1 text-2xl font-bold text-amber-100">{champion.clubName} é o campeão!</div>
      <div className="mt-1 text-sm font-mono text-amber-200/90">
        {champion.points} pts · {champion.wins}V {champion.draws}E {champion.losses}D · SG{' '}
        {champion.goalDifference >= 0 ? '+' : ''}{champion.goalDifference}
      </div>
      <div className="mt-2 text-xs text-slate-400">Uma nova temporada começou — boa sorte!</div>
    </div>
  )
}

/**
 * Quem pendurou as chuteiras no clube do usuário e qual garoto da base assumiu
 * (issue #55). Sem isso a troca só apareceria abrindo o elenco e comparando com
 * a memória — o backend manda a notícia pronta no `SeasonFinished`.
 */
function RetirementsCard({ retirements }: { retirements: Retirement[] }) {
  if (retirements.length === 0) return null
  return (
    <div className="rounded-lg border border-slate-700 bg-slate-800/60 p-4">
      <h2 className="text-sm font-semibold text-slate-200">Fim de linha</h2>
      <ul className="mt-2 space-y-1 text-sm text-slate-300">
        {retirements.map((r) => (
          <li key={r.retired.id}>
            <span className="text-slate-100">{r.retired.name}</span>
            <span className="text-slate-400"> ({r.retired.age}a, OVR {r.retired.overall}) se aposentou · </span>
            <span className="text-emerald-300">
              sobe {r.promoted.name} ({r.promoted.age}a, OVR {r.promoted.overall})
            </span>
          </li>
        ))}
      </ul>
    </div>
  )
}

/**
 * O que a semana de treino produziu (issue #58): quem evoluiu e quem saiu
 * machucado. O card aparece mesmo sem eventos — "treinei e ninguém evoluiu"
 * é a informação que fecha o ciclo da decisão tomada na Dashboard.
 */
function TrainingCard({ report }: { report: TrainingReport }) {
  return (
    <div className="rounded-lg border border-sky-800/60 bg-sky-950/30 p-4">
      <h2 className="text-sm font-semibold text-sky-200">
        Treino da semana — {TRAINING_FOCUS_LABEL[report.focus]}
      </h2>
      {report.events.length === 0 ? (
        <p className="mt-2 text-sm text-slate-400">
          Semana sem novidades: ninguém evoluiu nem saiu machucado.
        </p>
      ) : (
        <ul className="mt-2 space-y-1 text-sm text-slate-300">
          {report.events.map((event, i) => (
            <li key={`${event.type}-${event.player.id}-${i}`}>
              {event.type === 'Improved' ? (
                <>
                  <span className="text-emerald-300">▲</span>{' '}
                  <span className="text-slate-100">{event.player.name}</span>
                  <span className="text-slate-400">
                    {' '}evoluiu {TRAINED_ATTRIBUTE_LABEL[event.attribute]} · OVR{' '}
                    {event.player.overall}
                  </span>
                </>
              ) : (
                <>
                  <span className="text-orange-300">🚑</span>{' '}
                  <span className="text-slate-100">{event.player.name}</span>
                  <span className="text-slate-400">
                    {' '}se machucou no treino · fora por {event.roundsOut} rodada
                    {event.roundsOut === 1 ? '' : 's'}
                  </span>
                </>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

// ─── Componentes auxiliares ────────────────────────────────────────────

function RoundProgress({
  minute,
  status,
  allFinished,
}: { minute: number; status: Status; allFinished: boolean }) {
  const pct = Math.min(100, (minute / 90) * 100)
  const remaining = Math.max(0, 90 - minute)
  const label =
    allFinished                ? 'Rodada encerrada — avaliando pontuações' :
    status === 'live' && remaining <= 10 ? `⏱ Faltam ${remaining}′` :
    status === 'live'          ? `Minuto ${minute}′` :
                                 'Aguardando início'

  return (
    <div className="rounded-lg border border-slate-800 bg-slate-900/40 p-3">
      <div className="flex items-center justify-between mb-2 text-xs font-mono">
        <span className={status === 'live' ? 'text-emerald-400' : 'text-slate-400'}>
          {status === 'live' && <span className="inline-block w-1.5 h-1.5 rounded-full bg-emerald-400 mr-1.5 animate-pulse" />}
          {label}
        </span>
        <span className="text-slate-500 tabular-nums">{minute}/90′</span>
      </div>
      <div className="h-1.5 rounded-full bg-slate-800 overflow-hidden">
        <div
          className={`h-full transition-all duration-300 ${
            allFinished ? 'bg-slate-500' :
            remaining <= 10 && status === 'live' ? 'bg-amber-400' :
            'bg-emerald-500'
          }`}
          style={{ width: `${pct}%` }}
        />
      </div>
    </div>
  )
}

function MatchCard({
  match,
  state,
  highlight,
}: { match: RoundMatch; state: LiveMatchState | undefined; highlight: boolean }) {
  const s = state ?? {
    homeGoals: match.homeGoals,
    awayGoals: match.awayGoals,
    minute: match.minute,
    status: match.status,
  }

  const minuteLabel = s.status === 'Finished' ? 'FIM' : s.status === 'InProgress' ? `${s.minute}'` : '—'
  const minuteColor = s.status === 'InProgress' ? 'text-emerald-400' : 'text-slate-500'

  return (
    <Link
      to={`/round/match/${match.matchId}`}
      className={`block rounded-lg border p-4 transition-colors hover:border-emerald-500/60 ${
        highlight
          ? 'border-emerald-700/50 bg-emerald-900/10'
          : 'border-slate-800 bg-slate-900/40'
      }`}
    >
      <div className="flex items-center justify-between mb-2">
        <span className={`text-xs font-mono tracking-wider ${minuteColor}`}>
          {s.status === 'InProgress' && <span className="inline-block w-1.5 h-1.5 rounded-full bg-emerald-400 mr-1.5 animate-pulse" />}
          {minuteLabel}
        </span>
        {highlight && <span className="text-xs text-emerald-400 font-semibold">SEU JOGO</span>}
      </div>
      <div className="grid grid-cols-[1fr_auto_1fr] items-center gap-2">
        <div className="text-right">
          <div className="text-sm font-medium text-slate-100 truncate">{match.homeClubName}</div>
        </div>
        <div className="text-2xl font-bold font-mono tabular-nums text-slate-100 px-2">
          {s.homeGoals} <span className="text-slate-600">×</span> {s.awayGoals}
        </div>
        <div className="text-left">
          <div className="text-sm font-medium text-slate-100 truncate">{match.awayClubName}</div>
        </div>
      </div>
    </Link>
  )
}

function FinalBanner({
  standings,
  round,
  finance,
}: { standings: Standings; round: number; finance: RoundFinance | null }) {
  const top3 = standings.rows.slice(0, 3)
  return (
    <div className="rounded-lg border border-emerald-700/50 bg-emerald-900/20 p-4">
      <div className="font-semibold text-emerald-200">Pontuações atualizadas após a rodada {round}</div>
      {finance && (
        <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-sm font-mono">
          <span className="text-emerald-300">+{formatMoney(finance.ticketRevenue)} bilheteria</span>
          <span className="text-red-300">−{formatMoney(finance.salariesPaid)} salários</span>
          <span className="text-slate-200">
            saldo {finance.ticketRevenue - finance.salariesPaid >= 0 ? '+' : '−'}
            {formatMoney(Math.abs(finance.ticketRevenue - finance.salariesPaid))}
          </span>
          {finance.deficit > 0 && (
            <span className="font-semibold text-red-400">
              ⚠ no vermelho — {formatMoney(finance.deficit)} de salários sem cobertura
            </span>
          )}
        </div>
      )}
      <ul className="mt-2 text-sm space-y-1">
        {top3.map((r) => (
          <li key={r.clubId} className="flex justify-between text-slate-200">
            <span>{r.position}. {r.clubName}</span>
            <span className="font-mono">{r.points} pts · SG {r.goalDifference >= 0 ? '+' : ''}{r.goalDifference}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}
