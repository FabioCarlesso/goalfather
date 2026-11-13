import { Link } from 'react-router-dom'
import { PageTitle } from '../components/PageTitle'
import { EmptyState } from '../components/ui/EmptyState'
import { ErrorState } from '../components/ui/ErrorState'
import { LoadingState } from '../components/ui/LoadingState'
import { useStandings } from '../api/queries/useStandings'
import { useMyClubId } from '../auth/useMyClubId'
import type { Standings, TableZone } from '../domain/types'

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

type PaintedZone = Exclude<TableZone, 'NONE'>

/**
 * Cor, rótulo e dica de cada faixa. `Record<PaintedZone, …>` obriga o TS a ter
 * uma entrada por valor do enum do contrato: uma faixa nova no YAML quebra a
 * compilação aqui até ganhar cor.
 */
const zoneStyles: Record<PaintedZone, { row: string; border: string; swatch: string; label: string; title: string }> = {
  QUALIFICATION: {
    row: 'border-l-2 border-indigo-400 bg-indigo-900/15',
    border: 'border-l-2 border-indigo-400',
    swatch: 'bg-indigo-400',
    label: 'Vaga continental',
    title: 'Zona de vaga continental',
  },
  PROMOTION: {
    row: 'border-l-2 border-emerald-500 bg-emerald-900/15',
    border: 'border-l-2 border-emerald-500',
    swatch: 'bg-emerald-500',
    label: 'Promoção',
    title: 'Zona de promoção',
  },
  PLAYOFF: {
    row: 'border-l-2 border-amber-400 bg-amber-900/15',
    border: 'border-l-2 border-amber-400',
    swatch: 'bg-amber-400',
    label: 'Playoff',
    title: 'Zona de playoff',
  },
  RELEGATION: {
    row: 'border-l-2 border-red-500 bg-red-900/15',
    border: 'border-l-2 border-red-500',
    swatch: 'bg-red-500',
    label: 'Rebaixamento',
    title: 'Zona de rebaixamento',
  },
}

const legendOrder: PaintedZone[] = ['QUALIFICATION', 'PROMOTION', 'PLAYOFF', 'RELEGATION']

/** `NONE` não pinta nada; as demais faixas vêm do estilo. Type guard para o TS estreitar. */
const isPainted = (zone: TableZone): zone is PaintedZone => zone !== 'NONE'

/**
 * Tabela de uma divisão com as faixas de cada posição pintadas (issue #128):
 * vaga continental, promoção, playoff e rebaixamento. A faixa vem PRONTA do
 * contrato (`row.zone`) — o frontend não recalcula a regra; a legenda só conta
 * as linhas de cada faixa presente.
 */
function DivisionTable({ table, myClubId }: { table: Standings; myClubId: number }) {
  const legend = legendOrder
    .map((zone) => ({ zone, count: table.rows.filter((r) => r.zone === zone).length }))
    .filter((entry) => entry.count > 0)

  return (
    <div>
      <div className="mb-2 flex items-baseline justify-between">
        <h2 className="text-xl font-semibold text-slate-100">Divisão {table.division}</h2>
        <div className="flex gap-4 text-xs text-slate-400">
          {legend.map(({ zone, count }) => (
            <span key={zone} className="flex items-center gap-1.5">
              <span className={`inline-block h-2.5 w-2.5 rounded-sm ${zoneStyles[zone].swatch}`} />
              {zoneStyles[zone].label} ({count})
            </span>
          ))}
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
            const style = isPainted(r.zone) ? zoneStyles[r.zone] : null
            const mine = r.clubId === myClubId
            return (
              <tr
                key={r.clubId}
                aria-current={mine ? 'true' : undefined}
                // Clube do usuário: o fundo sky substitui o da zona, mas a borda
                // esquerda da zona continua visível.
                className={
                  mine
                    ? `bg-sky-900/40 font-semibold ${style?.border ?? ''}`
                    : `hover:bg-slate-900/40 ${style?.row ?? ''}`
                }
                data-zone={r.zone}
                title={style?.title}
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
