import { Link } from 'react-router-dom'
import { PageTitle } from '../components/PageTitle'
import { EmptyState } from '../components/ui/EmptyState'
import { ErrorState } from '../components/ui/ErrorState'
import { LoadingState } from '../components/ui/LoadingState'
import { useStandings } from '../api/queries/useStandings'
import { useMyClubId } from '../auth/useMyClubId'
import type { Standings } from '../domain/types'

export function StandingsPage() {
  const { data, isPending, isLoadingError, error, refetch } = useStandings()
  const myClubId = useMyClubId()

  if (isPending) return <><PageTitle>Tabela</PageTitle><LoadingState label="Carregando tabela…" shape="table" /></>
  if (isLoadingError) return <><PageTitle>Tabela</PageTitle><ErrorState error={error} onRetry={refetch} /></>

  if (data.length === 0) {
    return (
      <section className="space-y-4">
        <PageTitle>Tabela</PageTitle>
        <h1 className="text-3xl font-bold text-slate-100">Tabela</h1>
        <EmptyState message="Nenhuma divisão com tabela por enquanto. A classificação aparece aqui quando a temporada estiver montada." />
      </section>
    )
  }

  const first = data[0]!

  return (
    <section className="space-y-8">
      <PageTitle>Tabela</PageTitle>
      <header>
        <h1 className="text-3xl font-bold text-slate-100">Tabela</h1>
        <p className="text-sm text-slate-400">
          Temporada {first.season} · Rodada {first.round}
        </p>
      </header>

      {/* A divisão do usuário vem primeiro (#108): só reordena a apresentação,
          sem regra de domínio; as demais mantêm a ordem do contrato. */}
      {[...data]
        .sort((a, b) => Number(isMine(b, myClubId)) - Number(isMine(a, myClubId)))
        .map((table) => (
          <DivisionTable key={table.division} table={table} myClubId={myClubId} />
        ))}
    </section>
  )
}

const isMine = (table: Standings, myClubId: number): boolean =>
  table.rows.some((r) => r.clubId === myClubId)

/**
 * Tabela de uma divisão com as zonas de promoção (topo) e rebaixamento
 * (fundo) pintadas. Quantas posições compõem cada zona vem do CONTRATO
 * (`promotionSpots`/`relegationSpots`) — o frontend não recalcula a regra.
 */
function DivisionTable({ table, myClubId }: { table: Standings; myClubId: number }) {
  const zoneOf = (position: number): 'promotion' | 'relegation' | null => {
    if (position <= table.promotionSpots) return 'promotion'
    if (position > table.rows.length - table.relegationSpots) return 'relegation'
    return null
  }

  const zoneClasses = {
    promotion:  'border-l-2 border-emerald-500 bg-emerald-900/15',
    relegation: 'border-l-2 border-red-500 bg-red-900/15',
  } as const
  // Na linha do usuário a zona fica só com a borda; o fundo é o do destaque.
  const zoneBorderClasses = {
    promotion:  'border-l-2 border-emerald-500',
    relegation: 'border-l-2 border-red-500',
  } as const

  return (
    <div>
      <div className="mb-2 flex items-baseline justify-between">
        <h2 className="text-xl font-semibold text-slate-100">Divisão {table.division}</h2>
        <div className="flex gap-4 text-xs text-slate-400">
          {table.promotionSpots > 0 && (
            <span className="flex items-center gap-1.5">
              <span className="inline-block h-2.5 w-2.5 rounded-sm bg-emerald-500" />
              Promoção (top {table.promotionSpots})
            </span>
          )}
          {table.relegationSpots > 0 && (
            <span className="flex items-center gap-1.5">
              <span className="inline-block h-2.5 w-2.5 rounded-sm bg-red-500" />
              Rebaixamento (últimos {table.relegationSpots})
            </span>
          )}
        </div>
      </div>

      <table className="w-full text-left text-sm">
        <thead className="text-xs uppercase tracking-wide text-text-muted border-b border-slate-800">
          <tr>
            <th scope="col" className="py-2 pr-2"><abbr title="Posição" className="no-underline">#</abbr></th>
            <th scope="col" className="py-2 pr-2">Clube</th>
            <th scope="col" className="py-2 text-center"><abbr title="Jogos" className="no-underline">J</abbr></th>
            <th scope="col" className="py-2 text-center"><abbr title="Vitórias" className="no-underline">V</abbr></th>
            <th scope="col" className="py-2 text-center"><abbr title="Empates" className="no-underline">E</abbr></th>
            <th scope="col" className="py-2 text-center"><abbr title="Derrotas" className="no-underline">D</abbr></th>
            <th scope="col" className="py-2 text-center"><abbr title="Saldo de gols" className="no-underline">SG</abbr></th>
            <th scope="col" className="py-2 text-center font-semibold"><abbr title="Pontos" className="no-underline">Pts</abbr></th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-800">
          {table.rows.map((r) => {
            const zone = zoneOf(r.position)
            const mine = r.clubId === myClubId
            return (
              <tr
                key={r.clubId}
                aria-current={mine ? 'true' : undefined}
                // Clube do usuário: o fundo sky substitui o da zona, mas a borda
                // esquerda (emerald/red) da zona continua visível.
                className={
                  mine
                    ? `bg-sky-900/40 font-semibold ${zone ? zoneBorderClasses[zone] : ''}`
                    : `hover:bg-slate-900/40 ${zone ? zoneClasses[zone] : ''}`
                }
                title={
                  zone === 'promotion'  ? 'Zona de promoção' :
                  zone === 'relegation' ? 'Zona de rebaixamento' : undefined
                }
              >
                <td className="py-2 pr-2 pl-2 text-text-muted">{r.position}</td>
                <td className="py-2 pr-2 text-slate-100">
                  {/* Elenco do clube — de onde sai a proposta entre técnicos (issue #63). */}
                  <Link to={`/clubs/${r.clubId}`} className="hover:text-emerald-300 hover:underline">
                    {r.clubName}
                  </Link>
                  {mine && <span className="sr-only"> (seu clube)</span>}
                </td>
                <td className="py-2 text-center text-slate-300">{r.played}</td>
                <td className="py-2 text-center text-slate-300">{r.wins}</td>
                <td className="py-2 text-center text-slate-300">{r.draws}</td>
                <td className="py-2 text-center text-slate-300">{r.losses}</td>
                <td className="py-2 text-center text-slate-300">{r.goalDifference}</td>
                <td className="py-2 text-center font-semibold text-slate-100">{r.points}</td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
