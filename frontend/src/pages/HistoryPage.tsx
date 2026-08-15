import { useState } from 'react'
import { useSeasonHistory, useSeasonRecord, useClubCareer } from '../api/queries/useSeasonHistory'
import { useMyClubId } from '../auth/useMyClubId'
import type { ClubCareer, SeasonRecord, SeasonStanding } from '../domain/types'

/**
 * Histórico da liga (issue #60): o que sobra de cada temporada depois que a
 * virada zera tabela e estatísticas. A lista traz campeão e artilheiro; clicar
 * numa temporada busca o drill-down em `/api/league/history/{season}`.
 */
export function HistoryPage() {
  const myClubId = useMyClubId()
  const { data: seasons, isLoading, error } = useSeasonHistory()
  const { data: career } = useClubCareer(myClubId)
  const [selected, setSelected] = useState<number | null>(null)
  const { data: record, isLoading: loadingRecord } = useSeasonRecord(selected)

  if (isLoading) return <p className="text-slate-400">Carregando histórico…</p>
  if (error)     return <p className="text-red-400">Erro: {String(error)}</p>

  return (
    <section className="space-y-8">
      <header>
        <h1 className="text-3xl font-bold text-slate-100">Histórico</h1>
        <p className="text-sm text-slate-400">
          Campeões, artilheiros e classificações das temporadas encerradas.
        </p>
      </header>

      {career && <CareerCard career={career} />}

      {!seasons || seasons.length === 0 ? (
        <p className="rounded-lg border border-slate-800 bg-slate-900/40 p-6 text-slate-400">
          Nenhuma temporada encerrada ainda. Jogue até a última rodada para
          escrever o primeiro capítulo.
        </p>
      ) : (
        <div className="space-y-4">
          <ul className="divide-y divide-slate-800 rounded-lg border border-slate-800">
            {seasons.map((season) => (
              <SeasonRow
                key={season.season}
                season={season}
                selected={selected === season.season}
                onSelect={() =>
                  setSelected((prev) => (prev === season.season ? null : season.season))
                }
              />
            ))}
          </ul>

          {selected != null && (
            loadingRecord || !record
              ? <p className="text-slate-400">Carregando temporada {selected}…</p>
              : <SeasonDetail record={record} myClubId={myClubId} />
          )}
        </div>
      )}
    </section>
  )
}

/** Perfil do técnico — temporadas, títulos e melhor campanha (vêm prontos do backend). */
function CareerCard({ career }: { career: ClubCareer }) {
  return (
    <div className="rounded-lg border border-slate-800 bg-slate-900/40 p-5">
      <h2 className="text-lg font-semibold text-slate-100">{career.clubName}</h2>
      <dl className="mt-3 grid grid-cols-2 gap-4 sm:grid-cols-3">
        <div>
          <dt className="text-xs uppercase tracking-wide text-slate-500">Temporadas</dt>
          <dd className="text-2xl font-bold text-slate-100">{career.seasonsPlayed}</dd>
        </div>
        <div>
          <dt className="text-xs uppercase tracking-wide text-slate-500">Títulos</dt>
          <dd className="text-2xl font-bold text-amber-300">
            {career.titles.length}
            {career.titles.length > 0 && (
              <span className="ml-2 text-xs font-normal text-slate-400">
                {career.titles.join(', ')}
              </span>
            )}
          </dd>
        </div>
        <div>
          <dt className="text-xs uppercase tracking-wide text-slate-500">Melhor campanha</dt>
          <dd className="text-sm text-slate-200">
            {career.bestCampaign
              ? `${positionLabel(career.bestCampaign.standing)} em ${career.bestCampaign.season} · ` +
                `${career.bestCampaign.standing.pointsPercentage}% de aproveitamento`
              : '—'}
          </dd>
        </div>
      </dl>
    </div>
  )
}

function SeasonRow({
  season,
  selected,
  onSelect,
}: {
  season: SeasonRecord
  selected: boolean
  onSelect: () => void
}) {
  return (
    <li>
      <button
        onClick={onSelect}
        aria-expanded={selected}
        className={`flex w-full items-center gap-4 px-4 py-3 text-left transition-colors ${
          selected ? 'bg-slate-800/60' : 'hover:bg-slate-900/60'
        }`}
      >
        <span className="w-16 font-mono text-slate-400">{season.season}</span>
        <span className="flex-1">
          <span className="text-slate-100">🏆 {season.champion.row.clubName}</span>
          <span className="ml-2 text-xs text-slate-500">
            {season.champion.row.points} pts · {season.champion.pointsPercentage}%
          </span>
        </span>
        <span className="hidden text-sm text-slate-400 sm:block">
          {season.topScorer
            ? `⚽ ${season.topScorer.playerName} (${season.topScorer.goals})`
            : 'sem artilheiro'}
        </span>
      </button>
    </li>
  )
}

/** Classificação final da temporada, uma tabela por divisão. */
function SeasonDetail({ record, myClubId }: { record: SeasonRecord; myClubId: number }) {
  const divisions = [...new Set(record.finalStandings.map((s) => s.division))]

  return (
    <div className="space-y-6 rounded-lg border border-slate-800 p-4">
      <header className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="text-xl font-semibold text-slate-100">Temporada {record.season}</h2>
        {record.topScorer && (
          <p className="text-sm text-slate-400">
            Artilheiro: <span className="text-slate-200">{record.topScorer.playerName}</span>{' '}
            ({record.topScorer.clubName}) — {record.topScorer.goals} gols
          </p>
        )}
      </header>

      {divisions.map((division) => (
        <div key={division}>
          <h3 className="mb-2 text-sm font-semibold uppercase tracking-wide text-slate-400">
            Divisão {division}
          </h3>
          <table className="w-full text-left text-sm">
            <thead className="border-b border-slate-800 text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th className="py-2 pr-2">#</th>
                <th className="py-2 pr-2">Clube</th>
                <th className="py-2 text-center">J</th>
                <th className="py-2 text-center">SG</th>
                <th className="py-2 text-center font-semibold">Pts</th>
                <th className="py-2 text-center">%</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800">
              {record.finalStandings
                .filter((s) => s.division === division)
                .map((s) => (
                  <tr
                    key={s.row.clubId}
                    className={s.row.clubId === myClubId ? 'bg-sky-900/20' : undefined}
                  >
                    <td className="py-2 pr-2 pl-2 text-slate-500">{s.row.position}</td>
                    <td className="py-2 pr-2 text-slate-100">{s.row.clubName}</td>
                    <td className="py-2 text-center text-slate-300">{s.row.played}</td>
                    <td className="py-2 text-center text-slate-300">{s.row.goalDifference}</td>
                    <td className="py-2 text-center font-semibold text-slate-100">{s.row.points}</td>
                    <td className="py-2 text-center text-slate-400">{s.pointsPercentage}%</td>
                  </tr>
                ))}
            </tbody>
          </table>
        </div>
      ))}
    </div>
  )
}

const positionLabel = (standing: SeasonStanding) =>
  `${standing.row.position}º na divisão ${standing.division}`
