import { useNavigate } from 'react-router-dom'
import { PageTitle } from '../components/PageTitle'
import { useAuth } from '../auth/AuthContext'
import { useAvailableClubs } from '../api/queries/useAvailableClubs'
import { useClaimClub } from '../api/queries/useClaimClub'
import { errorMessage } from '../api/errorMessages'
import { formatMoney, formatSeats } from '../domain/formatters'
import type { AvailableClub } from '../domain/types'
import { Button } from '../components/ui/Button'
import { EmptyState } from '../components/ui/EmptyState'
import { ErrorState } from '../components/ui/ErrorState'
import { LoadingState } from '../components/ui/LoadingState'

/**
 * Seleção de clube (issue #19). Exibida quando o usuário tem token mas ainda
 * não reivindicou clube (`clubId == null`). Reivindicar grava o clube no
 * usuário e leva ao dashboard.
 */
export function SelectClubPage() {
  const { data: clubs, isPending, isLoadingError, error, refetch } = useAvailableClubs()
  const { setClubId } = useAuth()
  const claim = useClaimClub()
  const navigate = useNavigate()

  const onClaim = (clubId: number) => {
    claim.mutate(clubId, {
      onSuccess: (user) => {
        if (user.clubId != null) setClubId(user.clubId)
        navigate('/dashboard')
      },
    })
  }

  if (isPending) return <><PageTitle>Escolha seu clube</PageTitle><LoadingState label="Carregando clubes…" shape="cards" /></>
  if (isLoadingError) return <><PageTitle>Escolha seu clube</PageTitle><ErrorState error={error} onRetry={refetch} /></>

  return (
    <section className="mx-auto max-w-3xl space-y-6">
      <PageTitle>Escolha seu clube</PageTitle>
      <header className="space-y-2 text-center">
        <h1 className="text-3xl font-bold text-slate-100">Escolha seu clube</h1>
        <p className="text-slate-400">
          Selecione um clube sem dono para começar sua jornada como técnico.
        </p>
      </header>

      {clubs.length === 0 ? (
        <EmptyState
          className="text-center"
          message="Todos os clubes já têm dono. (Criar um clube novo virá em uma próxima atualização.)"
        />
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          {clubs.map((club) => (
            <ClubCard
              key={club.id}
              club={club}
              pending={claim.isPending && claim.variables === club.id}
              disabled={claim.isPending}
              onClaim={() => onClaim(club.id)}
            />
          ))}
        </div>
      )}

      {claim.isError && <p className="text-center text-sm text-red-400">{errorMessage(claim.error)}</p>}
    </section>
  )
}

function ClubCard({
  club,
  pending,
  disabled,
  onClaim,
}: {
  club: AvailableClub
  pending: boolean
  disabled: boolean
  onClaim: () => void
}) {
  return (
    <div className="flex flex-col gap-3 rounded-lg border border-slate-800 bg-slate-900/60 p-5">
      <div>
        <div className="flex items-center justify-between gap-2">
          <h2 className="text-lg font-semibold text-slate-100">{club.name}</h2>
          <span className="shrink-0 rounded bg-slate-800 px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-slate-300">
            Divisão {club.division}
          </span>
        </div>
        <p className="text-xs text-slate-500">ID #{club.id}</p>
      </div>
      <dl className="grid grid-cols-3 gap-2 text-center">
        <Metric label="Caixa" value={formatMoney(club.cash)} />
        <Metric label="Estádio" value={formatSeats(club.stadiumCapacity)} />
        <Metric label="Força méd." value={String(club.averageOverall)} />
      </dl>
      <Button
        onClick={onClaim}
        disabled={disabled}
        className="mt-1"
      >
        {pending ? 'Reivindicando…' : 'Treinar este clube'}
      </Button>
    </div>
  )
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-md bg-slate-800/50 px-2 py-1.5">
      <dt className="text-[10px] uppercase tracking-wide text-slate-500">{label}</dt>
      <dd className="text-sm font-semibold text-slate-100">{value}</dd>
    </div>
  )
}
