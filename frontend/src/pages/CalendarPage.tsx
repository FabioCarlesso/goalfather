import { useState } from 'react'
import { useClubSchedule } from '../api/queries/useClubSchedule'
import { useMyClubId } from '../auth/useMyClubId'
import { ClubScheduleList } from '../components/ClubScheduleList'
import { PageTitle } from '../components/PageTitle'
import { EmptyState } from '../components/ui/EmptyState'
import { ErrorState } from '../components/ui/ErrorState'
import { LoadingState } from '../components/ui/LoadingState'
import { formatMatchday } from '../domain/calendar'

/** Horizontes oferecidos no seletor — o contrato aceita de 1 a 26 semanas. */
const HORIZONS = [4, 8, 16, 26] as const
type Horizon = (typeof HORIZONS)[number]

/**
 * Calendário do clube (issue #133): as próximas semanas, cada jogo com a
 * competição, e as datas FIFA destacadas. O horizonte é estado de UI; os
 * dados vêm de `useClubSchedule`.
 */
export function CalendarPage() {
  const myClubId = useMyClubId()
  const [weeks, setWeeks] = useState<Horizon>(8)
  const { data, isPending, isLoadingError, error, refetch } = useClubSchedule(myClubId, weeks)

  return (
    <section className="space-y-6">
      <PageTitle>Calendário</PageTitle>
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold text-slate-100">Calendário</h1>
          {data?.current && (
            <p className="text-sm text-slate-400" data-testid="schedule-current">Hoje: {formatMatchday(data.current)}</p>
          )}
        </div>
        <label className="flex flex-col gap-1 text-sm text-slate-400">
          Período
          <select
            value={weeks}
            onChange={(e) => setWeeks(Number(e.target.value) as Horizon)}
            className="rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-slate-100"
          >
            {HORIZONS.map((h) => <option key={h} value={h}>Próximas {h} semanas</option>)}
          </select>
        </label>
      </header>

      {isPending ? (
        <LoadingState label="Carregando calendário…" shape="list" />
      ) : isLoadingError ? (
        <ErrorState error={error} onRetry={refetch} />
      ) : data.weeks.length === 0 ? (
        <EmptyState message="Sem calendário por enquanto. As semanas aparecem quando a temporada estiver montada." />
      ) : (
        <ClubScheduleList schedule={data} />
      )}
    </section>
  )
}
