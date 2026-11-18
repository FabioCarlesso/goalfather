import { useClubCallUps, useNationalTeams } from '../api/queries/useNationalTeams'
import { useMyClubId } from '../auth/useMyClubId'
import { CallUpNotice } from '../components/CallUpNotice'
import { CountryFlag } from '../components/CountryFlag'
import { PageTitle } from '../components/PageTitle'
import { Badge } from '../components/ui/Badge'
import { Card } from '../components/ui/Card'
import { EmptyState } from '../components/ui/EmptyState'
import { ErrorState } from '../components/ui/ErrorState'
import { LoadingState } from '../components/ui/LoadingState'
import { MATCH_KIND_LABEL, WINDOW_STATUS_LABEL, windowRange } from '../domain/nationalTeams'
import type { FifaWindowSummary, NationalMatch, NationalTableRow, NationalTeamsOverview } from '../domain/types'

/**
 * Seleções nas datas FIFA (issue #134): os convocados do clube, as datas FIFA
 * do ano, os resultados da última janela, a classificação das Eliminatórias da
 * CONMEBOL e o ranking. Tudo chega calculado pelo backend; a página só desenha.
 */
export function NationalTeamsPage() {
  const myClubId = useMyClubId()
  const { data, isPending, isLoadingError, error, refetch } = useNationalTeams()
  const { data: callUps } = useClubCallUps(myClubId)

  if (isPending) return <><PageTitle>Seleções</PageTitle><LoadingState label="Carregando seleções…" shape="cards" /></>
  if (isLoadingError) return <><PageTitle>Seleções</PageTitle><ErrorState error={error} onRetry={refetch} /></>

  return (
    <section className="space-y-6">
      <PageTitle>Seleções</PageTitle>
      <header>
        <h1 className="text-3xl font-bold text-slate-100">Seleções</h1>
        <p className="text-sm text-slate-400">
          Nas datas FIFA as seleções convocam os melhores de cada nacionalidade. As ligas europeias param; o
          Brasileirão segue, e o clube joga sem os convocados.
        </p>
      </header>

      {callUps && <CallUpNotice callUps={callUps} />}
      {callUps && callUps.players.length === 0 && callUps.window && (
        <p className="text-sm text-slate-400">Nenhum jogador do seu clube na data FIFA de {windowRange(callUps.window)}.</p>
      )}

      <WindowsCard windows={data.windows} />
      <ResultsCard overview={data} />
      <TableCard
        title={`Eliminatórias CONMEBOL ${data.qualifiers.cycle}–${data.qualifiers.cycle + 1}`}
        caption={`${data.qualifiers.roundsPlayed} de ${data.qualifiers.totalRounds} rodadas`}
        rows={data.qualifiers.rows}
      />
      <TableCard
        title="Ranking de seleções"
        caption="3 pontos por vitória, 1 por empate; jogo de Eliminatórias vale o dobro"
        rows={data.ranking}
        empty="O ranking começa depois da primeira data FIFA."
      />
    </section>
  )
}

function WindowsCard({ windows }: { windows: FifaWindowSummary[] }) {
  return (
    <Card as="section" title="Datas FIFA" className="space-y-3">
      <ul className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-2">
        {windows.map((w) => (
          <li key={w.start} className="rounded border border-slate-800 px-3 py-2 text-sm">
            <div className="text-slate-200">{windowRange(w)}</div>
            <div className="mt-1 flex items-center gap-2 text-xs text-slate-400">
              <Badge tone={w.status === 'PLAYED' ? 'neutral' : w.status === 'CALLED' ? 'info' : 'warning'}>
                {WINDOW_STATUS_LABEL[w.status]}
              </Badge>
              {w.matchSlots} jogos
            </div>
          </li>
        ))}
      </ul>
    </Card>
  )
}

function ResultsCard({ overview }: { overview: NationalTeamsOverview }) {
  const { lastWindow, lastResults } = overview
  return (
    <Card
      as="section"
      title="Últimos resultados"
      actions={lastWindow && <span className="text-xs text-slate-400">{windowRange(lastWindow)}</span>}
      className="space-y-3"
    >
      {lastResults.length === 0 ? (
        <EmptyState message="Nenhuma data FIFA disputada ainda." />
      ) : (
        <ul className="grid grid-cols-1 md:grid-cols-2 gap-2" aria-label="Jogos de seleção">
          {lastResults.map((m) => <MatchLine key={`${m.matchNumber}-${m.home.code}-${m.away.code}`} match={m} />)}
        </ul>
      )}
    </Card>
  )
}

function MatchLine({ match }: { match: NationalMatch }) {
  return (
    <li className="flex items-center justify-between gap-2 rounded border border-slate-800 px-3 py-1.5 text-sm">
      <span className="text-slate-200">
        <CountryFlag country={match.home} /> {match.home.name}{' '}
        <strong className="font-mono">{match.homeGoals} x {match.awayGoals}</strong>{' '}
        {match.away.name} <CountryFlag country={match.away} />
      </span>
      <span className="text-xs text-slate-400">
        {MATCH_KIND_LABEL[match.kind]}{match.qualifierRound ? ` · ${match.qualifierRound}ª rodada` : ''}
      </span>
    </li>
  )
}

function TableCard({ title, caption, rows, empty }: {
  title: string
  caption: string
  rows: NationalTableRow[]
  empty?: string
}) {
  return (
    <Card as="section" title={title} className="space-y-3">
      {rows.length === 0 && empty ? (
        <EmptyState message={empty} />
      ) : (
        // Tabela larga no celular: região rolável focável pelo teclado (axe:
        // scrollable-region-focusable), como a chave da copa.
        <div
          className="overflow-x-auto"
          role="region"
          aria-label={`Tabela: ${title}`}
          // eslint-disable-next-line jsx-a11y/no-noninteractive-tabindex
          tabIndex={0}
        >
          <table className="w-full text-sm">
            <caption className="text-left text-xs text-slate-400 pb-2">{caption}</caption>
            <thead className="text-xs uppercase text-slate-400">
              <tr>
                <th scope="col" className="text-left px-2">#</th>
                <th scope="col" className="text-left px-2">Seleção</th>
                <th scope="col" className="px-2"><abbr title="Pontos">P</abbr></th>
                <th scope="col" className="px-2"><abbr title="Jogos">J</abbr></th>
                <th scope="col" className="px-2"><abbr title="Vitórias">V</abbr></th>
                <th scope="col" className="px-2"><abbr title="Empates">E</abbr></th>
                <th scope="col" className="px-2"><abbr title="Derrotas">D</abbr></th>
                <th scope="col" className="px-2"><abbr title="Saldo de gols">SG</abbr></th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.country.code} className="border-t border-slate-800 text-center text-slate-200">
                  <td className="text-left px-2 py-1">{r.position}</td>
                  <th scope="row" className="text-left px-2 py-1 font-normal">
                    <CountryFlag country={r.country} withName />
                  </th>
                  <td className="px-2 font-semibold">{r.points}</td>
                  <td className="px-2">{r.played}</td>
                  <td className="px-2">{r.won}</td>
                  <td className="px-2">{r.drawn}</td>
                  <td className="px-2">{r.lost}</td>
                  <td className="px-2">{r.goalDifference}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  )
}
