import { useRef } from 'react'
import { PageTitle } from '../components/PageTitle'
import { useCurrentRound } from '../api/queries/useCurrentRound'
import { useRoundReadiness, useMarkReady } from '../api/queries/useRoundReadiness'
import { useMatchPlayerLookup } from '../api/queries/useMatchPlayerLookup'
import { useRoundStream } from '../api/queries/useRoundStream'
import { useStreamSpeed } from '../lib/useStreamSpeed'
import { SpeedControl } from '../components/SpeedControl'
import { useMyClubId } from '../auth/useMyClubId'
import { useAuth } from '../auth/AuthContext'
import { GoalAnnouncer } from '../components/GoalAnnouncer'
import { MatchEventFeed } from '../components/MatchEventFeed'
import { MatchStatsSummary } from '../components/MatchStatsSummary'
import { ReadinessCard } from '../components/ReadinessCard'
import { MatchGrid } from '../components/round/MatchGrid'
import { RoundProgress } from '../components/round/RoundProgress'
import { RoundSummary } from '../components/round/RoundSummary'
import { stageTitle } from '../domain/cup'
import { Button } from '../components/ui/Button'
import { ErrorState } from '../components/ui/ErrorState'
import { LoadingState } from '../components/ui/LoadingState'

export function RoundPage() {
  const myClubId = useMyClubId()
  const { user } = useAuth()
  const { data: round, isPending, isLoadingError, error: roundError, refetch } = useCurrentRound()

  // WebSocket da rodada + estado derivado dele (issue #112). `shownRound` é a
  // data JOGADA desde o início da rodada — o RoundFinished faz `round` virar a
  // próxima, que é a que o botão "Próxima rodada" vai jogar.
  // Velocidade escolhida (issue #115): vale para a próxima transmissão; o
  // "Pular para o fim" troca a atual por uma INSTANT (`stream.skip`).
  const { speed, setSpeed } = useStreamSpeed()
  const stream = useRoundStream(round, myClubId, speed)
  const { status, error, shownRound, matchStates, myMatch, myMatchEvents, myMatchStats, outcome } = stream

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

  // Lookup id → nome dos dois elencos da partida do usuário, para o feed
  // mostrar nomes em vez de "#N" (issues #7 e #84). Antes de a rodada
  // carregar, resolve ao menos o próprio elenco.
  const playerLookup = useMatchPlayerLookup(myMatch?.homeClubId ?? myClubId, myMatch?.awayClubId)

  // Alvo do botão "Ver detalhes da partida" do resumo.
  const detailsRef = useRef<HTMLDivElement>(null)

  if (isPending) return <><PageTitle>Rodada</PageTitle><LoadingState label="Carregando rodada…" shape="cards" /></>
  if (isLoadingError) return <><PageTitle>Rodada</PageTitle><ErrorState error={roundError} onRetry={refetch} /></>

  const shown = shownRound ?? round
  const busy = status === 'connecting' || status === 'live'
  const buttonLabel =
    status === 'idle'       ? 'Jogar rodada' :
    status === 'connecting' ? 'Iniciando…' :
    status === 'live'       ? 'Rodada em andamento…' :
    status === 'finished'   ? 'Próxima rodada' :
                              'Tentar novamente'
  const myMatchState = myMatch && matchStates.get(myMatch.matchId)

  return (
    <section className="space-y-4">
      <PageTitle>Rodada</PageTitle>
      <header className="flex items-center justify-between">
        <div>
          {/* A data do calendário é rodada de liga OU fase de copa (issue #61). */}
          <h1 className="text-3xl font-bold text-slate-100">{stageTitle(round.stage)}</h1>
          <p className="text-sm text-slate-400">
            Temporada {round.season} · Data {round.number} do calendário · {round.matches.length} partidas
            {round.stage.type === 'Cup' && ' · jogo único, empate vai aos pênaltis'}
          </p>
        </div>
        {/* Com o resumo na tela, o "Próxima rodada" mora nele (issue #112). */}
        {!outcome && (
          <Button
            onClick={stream.start}
            disabled={busy || !canStart}
            title={!canStart ? 'Aguardando técnicos sinalizarem prontos (ou o timeout expirar)' : undefined}
          >
            {buttonLabel}
          </Button>
        )}
      </header>

      {/* Resumo pós-rodada no topo, visível sem rolar (issue #112). */}
      {outcome && (
        <RoundSummary
          outcome={outcome}
          myClubId={myClubId}
          myMatch={myMatch}
          myMatchState={myMatchState}
          canStart={canStart}
          onNextRound={stream.start}
          onShowDetails={() => detailsRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })}
        />
      )}

      {/* Lobby da rodada compartilhada (issue #20): todos prontos → libera jogar. */}
      {readiness && !live && (
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

      <SpeedControl
        speed={speed}
        onSpeedChange={setSpeed}
        live={busy}
        canSkip={status === 'live' && stream.liveSpeed !== 'INSTANT'}
        onSkip={stream.skip}
      />

      <RoundProgress
        minute={stream.aggregateMinute}
        status={status}
        allFinished={stream.allFinished && status === 'finished'}
      />

      <MatchGrid stage={shown.stage} matches={shown.matches} states={matchStates} myClubId={myClubId} />

      {myMatch && (
        <div ref={detailsRef} className="space-y-2 scroll-mt-4">
          <h2 className="text-sm uppercase tracking-wide text-text-muted">
            Detalhes da sua partida — {myMatch.homeClubName} × {myMatch.awayClubName}
          </h2>
          {/* Gols da partida do técnico anunciados ao leitor de tela (issue #113). */}
          <GoalAnnouncer
            events={myMatchEvents}
            homeName={myMatch.homeClubName}
            awayName={myMatch.awayClubName}
            homeGoals={myMatchState?.homeGoals ?? myMatch.homeGoals}
            awayGoals={myMatchState?.awayGoals ?? myMatch.awayGoals}
          />
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
    </section>
  )
}
