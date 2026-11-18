import { useSearchParams } from 'react-router-dom'
import { LeagueTable } from '../components/LeagueTable'
import { PageTitle } from '../components/PageTitle'
import { EmptyState } from '../components/ui/EmptyState'
import { ErrorState } from '../components/ui/ErrorState'
import { LoadingState } from '../components/ui/LoadingState'
import { useStandings } from '../api/queries/useStandings'
import { useMyClubId } from '../auth/useMyClubId'
import type { Country, Standings } from '../domain/types'

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
