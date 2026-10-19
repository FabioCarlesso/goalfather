import { Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { useClubCareer } from '../api/queries/useSeasonHistory'
import { BOARD_OBJECTIVE_LABEL, ordinal } from '../domain/board'
import type { ClubCareer, Dismissal } from '../domain/types'

/**
 * Game over da v1 (issue #62): a diretoria demitiu o técnico na virada de
 * temporada e o clube voltou para a IA. A tela conta o porquê (meta × posição
 * final, que vêm na `dismissal` do `AuthUser`), mostra o histórico do clube (a
 * projeção da issue #60) e oferece recomeçar — escolher outro clube.
 *
 * Fora do `RequireClub` de propósito: quem chega aqui não tem mais clube.
 */
export function FiredPage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const dismissal = user?.dismissal

  // Sem demissão (ou já com clube novo) não há o que mostrar aqui.
  if (!dismissal || user?.clubId != null) return <Navigate to="/" replace />

  return (
    <section className="mx-auto max-w-2xl space-y-6 text-center">
      <header className="space-y-2">
        <div className="text-5xl">📉</div>
        <h1 className="text-3xl font-bold text-red-300">Você foi demitido</h1>
        <p className="text-slate-400">
          A diretoria do <strong className="text-slate-200">{dismissal.clubName}</strong> perdeu
          a confiança no seu trabalho ao fim da temporada {dismissal.season}.
        </p>
      </header>

      <DismissalSummary dismissal={dismissal} />

      <CareerSummary clubId={dismissal.clubId} />

      <button
        onClick={() => navigate('/select-club')}
        className="rounded-md bg-emerald-600 hover:bg-emerald-500 text-white px-5 py-2.5 font-medium transition-colors"
      >
        Recomeçar em outro clube
      </button>
    </section>
  )
}

function DismissalSummary({ dismissal }: { dismissal: Dismissal }) {
  return (
    <dl className="grid grid-cols-2 gap-4 rounded-lg border border-red-900/50 bg-red-950/20 p-5 text-left">
      <div>
        <dt className="text-xs uppercase tracking-wide text-slate-500">Meta da diretoria</dt>
        <dd className="text-slate-100">{BOARD_OBJECTIVE_LABEL[dismissal.objective]}</dd>
        <dd className="text-xs text-slate-400">até o {ordinal(dismissal.targetPosition)}</dd>
      </div>
      <div>
        <dt className="text-xs uppercase tracking-wide text-slate-500">Posição final</dt>
        <dd className="text-2xl font-bold text-red-300">{ordinal(dismissal.finalPosition)}</dd>
      </div>
    </dl>
  )
}

/**
 * Histórico do clube que demitiu — projeção da issue #60, pronta no backend.
 * É o histórico do CLUBE inteiro (a projeção não separa passagens de técnico),
 * por isso o título diz "no clube", não "sua carreira".
 */
function CareerSummary({ clubId }: { clubId: number }) {
  const { data: career, isLoading } = useClubCareer(clubId)
  if (isLoading) return <p className="text-slate-400">Carregando histórico…</p>
  if (!career) return null
  return <CareerStats career={career} />
}

function CareerStats({ career }: { career: ClubCareer }) {
  const best = career.bestCampaign
  return (
    <div className="rounded-lg border border-slate-800 bg-slate-900/40 p-5 text-left">
      <h2 className="text-lg font-semibold text-slate-100">Histórico do clube</h2>
      <p className="text-xs text-slate-500">Todas as temporadas do clube, não só a sua passagem.</p>
      <dl className="mt-3 grid grid-cols-2 gap-4 sm:grid-cols-4">
        <div>
          <dt className="text-xs uppercase tracking-wide text-slate-500">Temporadas</dt>
          <dd className="text-2xl font-bold text-slate-100">{career.seasonsPlayed}</dd>
        </div>
        <div>
          <dt className="text-xs uppercase tracking-wide text-slate-500">Títulos</dt>
          <dd className="text-2xl font-bold text-amber-300">{career.titles.length}</dd>
        </div>
        <div>
          <dt className="text-xs uppercase tracking-wide text-slate-500">Copas</dt>
          <dd className="text-2xl font-bold text-amber-300">{career.cupTitles.length}</dd>
        </div>
        <div>
          <dt className="text-xs uppercase tracking-wide text-slate-500">Melhor campanha</dt>
          <dd className="text-slate-100">
            {best ? `${ordinal(best.standing.row.position)} em ${best.season}` : '—'}
          </dd>
        </div>
      </dl>
    </div>
  )
}
