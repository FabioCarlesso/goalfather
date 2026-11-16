import { Link, useSearchParams } from 'react-router-dom'
import { PageTitle } from '../components/PageTitle'
import { EmptyState } from '../components/ui/EmptyState'
import { ErrorState } from '../components/ui/ErrorState'
import { LoadingState } from '../components/ui/LoadingState'
import { useStandings } from '../api/queries/useStandings'
import { useMyClubId } from '../auth/useMyClubId'
import type { Country, Standings, TableZone } from '../domain/types'

// Liga escolhida no seletor, na URL: sobrevive ao F5 e o link aponta para ela.
const LEAGUE_PARAM = 'league'

/**
 * Tabelas de classificação, uma liga por vez (issue #131): o mundo tem várias
 * ligas, cada uma na sua temporada. O seletor agrupa as ligas por país; sem
 * escolha na URL, abre a liga do clube do técnico.
 */
export function StandingsPage() {
  const { data, isPending, isLoadingError, error, refetch } = useStandings()
  const myClubId = useMyClubId()
  const [searchParams, setSearchParams] = useSearchParams()

  if (isPending) return <><PageTitle>Tabela</PageTitle><LoadingState label="Carregando tabela…" shape="table" /></>
  if (isLoadingError) return <><PageTitle>Tabela</PageTitle><ErrorState error={error} onRetry={refetch} /></>

  if (data.length === 0) {
    return (
      <section className="space-y-4">
        <PageTitle>Tabela</PageTitle>
        <h1 className="text-3xl font-bold text-slate-100">Tabela</h1>
        <EmptyState message="Nenhuma liga com tabela por enquanto. A classificação aparece aqui quando a temporada estiver montada." />
      </section>
    )
  }

  // Liga da URL; senão a do clube do técnico (#108: a dele vem primeiro);
  // senão a primeira do contrato. Só escolhe o que mostrar — sem regra de domínio.
  const selected =
    data.find((t) => t.competitionId === searchParams.get(LEAGUE_PARAM)) ??
    data.find((t) => isMine(t, myClubId)) ??
    data[0]!

  return (
    <section className="space-y-6">
      <PageTitle>Tabela</PageTitle>
      <header className="flex flex-wrap items-end justify-between gap-4">
        <h1 className="text-3xl font-bold text-slate-100">Tabela</h1>
        <LeagueSelect
          tables={data}
          value={selected.competitionId}
          myClubId={myClubId}
          onChange={(competitionId) => setSearchParams({ [LEAGUE_PARAM]: competitionId }, { replace: true })}
        />
      </header>

      <LeagueTable table={selected} myClubId={myClubId} />
    </section>
  )
}

/** Ligas agrupadas por país, na ordem do contrato (país, depois divisão). */
function byCountry(tables: Standings[]): Array<{ country: Country; tables: Standings[] }> {
  const groups: Array<{ country: Country; tables: Standings[] }> = []
  for (const table of tables) {
    const group = groups.find((g) => g.country.code === table.country.code)
    if (group) group.tables.push(table)
    else groups.push({ country: table.country, tables: [table] })
  }
  return groups
}

function LeagueSelect({
  tables,
  value,
  myClubId,
  onChange,
}: {
  tables: Standings[]
  value: string
  myClubId: number
  onChange: (competitionId: string) => void
}) {
  return (
    // `w-full` no celular: o <select> mede a MAIOR opção ("… Série A (sua
    // liga)") e estourava 360px; de `sm` para cima volta à largura natural.
    <label className="flex w-full flex-col gap-1 text-sm text-slate-400 sm:w-auto">
      Liga
      <select
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="w-full max-w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-slate-100 sm:w-auto"
      >
        {byCountry(tables).map(({ country, tables: leagues }) => (
          <optgroup key={country.code} label={`${country.flag} ${country.name}`}>
            {leagues.map((t) => (
              <option key={t.competitionId} value={t.competitionId}>
                {t.competitionName}
                {isMine(t, myClubId) ? ' (sua liga)' : ''}
              </option>
            ))}
          </optgroup>
        ))}
      </select>
    </label>
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
 * Tabela de uma liga com as faixas de cada posição pintadas (issue #128):
 * vaga continental, promoção, playoff e rebaixamento. A faixa vem PRONTA do
 * contrato (`row.zone`) — o frontend não recalcula a regra; a legenda só conta
 * as linhas de cada faixa presente.
 */
function LeagueTable({ table, myClubId }: { table: Standings; myClubId: number }) {
  const legend = legendOrder
    .map((zone) => ({ zone, count: table.rows.filter((r) => r.zone === zone).length }))
    .filter((entry) => entry.count > 0)

  return (
    <div>
      <div className="mb-2 flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
        <div>
          <h2 className="text-xl font-semibold text-slate-100">
            {table.competitionName} {table.seasonLabel}
          </h2>
          <p className="text-sm text-slate-400">
            <span aria-hidden="true">{table.country.flag}</span> {table.country.name} · Rodada {table.round}
          </p>
        </div>
        <div className="flex flex-wrap gap-4 text-xs text-slate-400">
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
