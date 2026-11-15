import { useEffect, useRef, type ReactNode, type Ref } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { PageTitle } from '../components/PageTitle'
import { useAuth } from '../auth/AuthContext'
import { useAvailableClubs } from '../api/queries/useAvailableClubs'
import { useClaimClub } from '../api/queries/useClaimClub'
import { useWorldCountries } from '../api/queries/useWorldCountries'
import { errorMessage } from '../api/errorMessages'
import { formatMoney, formatSeats } from '../domain/formatters'
import {
  availableClubsIn,
  countLabel,
  onboardingStepOf,
  tierLabel,
  type OnboardingStep,
} from '../domain/onboarding'
import type { AvailableClub, WorldCountry, WorldLeague } from '../domain/types'
import { Button } from '../components/ui/Button'
import { EmptyState } from '../components/ui/EmptyState'
import { ErrorState } from '../components/ui/ErrorState'
import { LoadingState } from '../components/ui/LoadingState'
import { cx } from '../components/ui/cx'

// Chaves da escolha na URL. O passo corrente é DERIVADO delas, não guardado.
const COUNTRY_PARAM = 'country'
const LEAGUE_PARAM = 'league'

const STEP_LABELS: Array<[OnboardingStep['type'], string]> = [
  ['Country', 'País'],
  ['League', 'Liga'],
  ['Club', 'Clube'],
]

/**
 * Onboarding país → liga → clube (issues #19 e #131). Exibido quando o usuário
 * tem token mas ainda não reivindicou clube (`clubId == null`). Reivindicar
 * grava o clube no usuário e leva ao dashboard.
 *
 * A escolha vive na URL (`?country=BRA&league=bra-serie-a`), não em `useState`:
 * cada passo é uma entrada do histórico — o "voltar" do navegador volta um
 * passo, o F5 não joga o técnico de volta ao começo e o link de um passo pode
 * ser compartilhado. Como os filtros do Mercado, é estado de navegação; o que
 * é dado de servidor (países, ligas, clubes livres) fica no TanStack Query.
 */
export function SelectClubPage() {
  const countries = useWorldCountries()
  const [searchParams, setSearchParams] = useSearchParams()

  const step: OnboardingStep | null = countries.data
    ? onboardingStepOf(countries.data, searchParams.get(COUNTRY_PARAM), searchParams.get(LEAGUE_PARAM))
    : null

  // Passo novo → foco no título dele, para o leitor de tela anunciar onde o
  // técnico chegou (o conteúdo de baixo foi trocado). Não no primeiro render.
  const headingRef = useRef<HTMLHeadingElement>(null)
  const shownStep = useRef<string | null>(null)
  const stepKey = step ? `${step.type}:${searchParams.toString()}` : null
  useEffect(() => {
    if (stepKey == null) return
    if (shownStep.current != null && shownStep.current !== stepKey) headingRef.current?.focus()
    shownStep.current = stepKey
  }, [stepKey])

  const goTo = (params: Record<string, string>) => setSearchParams(params)

  const header = (
    <header className="space-y-2 text-center">
      <PageTitle>Escolha seu clube</PageTitle>
      <h1 className="text-3xl font-bold text-slate-100">Escolha seu clube</h1>
      <p className="text-slate-400">
        Escolha o país, a liga e um clube sem dono para começar sua jornada como técnico.
      </p>
    </header>
  )

  if (countries.isPending) {
    return (
      <section className="mx-auto max-w-3xl space-y-6">
        {header}
        <LoadingState label="Carregando países…" shape="cards" withHeader={false} />
      </section>
    )
  }
  if (countries.isLoadingError) {
    return (
      <section className="mx-auto max-w-3xl space-y-6">
        {header}
        <ErrorState error={countries.error} onRetry={countries.refetch} />
      </section>
    )
  }

  return (
    <section className="mx-auto max-w-3xl space-y-6">
      {header}
      <StepIndicator current={step?.type ?? 'Country'} />

      {step == null || countries.data.length === 0 ? (
        <EmptyState className="text-center" message="Nenhum país com liga jogável por enquanto." />
      ) : step.type === 'Country' ? (
        <>
          <StepHeading headingRef={headingRef}>Escolha o país</StepHeading>
          <CountryList
            countries={countries.data}
            onPick={(country) => goTo({ [COUNTRY_PARAM]: country.country.code })}
          />
        </>
      ) : step.type === 'League' ? (
        <>
          <StepHeading headingRef={headingRef}>
            Escolha a liga — <span aria-hidden="true">{step.country.country.flag}</span> {step.country.country.name}
          </StepHeading>
          <BackButton onClick={() => goTo({})}>Voltar aos países</BackButton>
          <LeagueList
            leagues={step.country.leagues}
            onPick={(league) => goTo({ [COUNTRY_PARAM]: step.country.country.code, [LEAGUE_PARAM]: league.id })}
          />
        </>
      ) : (
        <>
          <StepHeading headingRef={headingRef}>Escolha o clube — {step.league.name}</StepHeading>
          <p className="-mt-4 text-center text-sm text-slate-400">
            <span aria-hidden="true">{step.country.country.flag}</span> {step.country.country.name} ·{' '}
            {tierLabel(step.league.tier)} · Temporada {step.league.seasonLabel}
          </p>
          <BackButton onClick={() => goTo({ [COUNTRY_PARAM]: step.country.country.code })}>
            Voltar às ligas
          </BackButton>
          <ClubStep
            key={step.league.id}
            league={step.league}
            onOtherLeague={() => goTo({ [COUNTRY_PARAM]: step.country.country.code })}
          />
        </>
      )}
    </section>
  )
}

/**
 * Último passo: os clubes livres da liga, com o claim. É da página (busca
 * dados); vive num componente próprio só para a query existir apenas neste
 * passo, com a liga já escolhida.
 */
function ClubStep({ league, onOtherLeague }: { league: WorldLeague; onOtherLeague: () => void }) {
  const clubs = useAvailableClubs(league.id)
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

  if (clubs.isPending) return <LoadingState label="Carregando clubes…" shape="cards" withHeader={false} />
  if (clubs.isLoadingError) return <ErrorState error={clubs.error} onRetry={clubs.refetch} />

  return (
    <>
      {clubs.data.length === 0 ? (
        <EmptyState
          className="text-center"
          message={`Todos os clubes desta liga já têm técnico. (Criar um clube novo virá em uma próxima atualização.)`}
          action={<Button variant="secondary" onClick={onOtherLeague}>Escolher outra liga</Button>}
        />
      ) : (
        <ul className="grid grid-cols-1 gap-4 sm:grid-cols-2" aria-label={`Clubes livres — ${league.name}`}>
          {clubs.data.map((club) => (
            <li key={club.id}>
              <ClubCard
                club={club}
                pending={claim.isPending && claim.variables === club.id}
                disabled={claim.isPending}
                onClaim={() => onClaim(club.id)}
              />
            </li>
          ))}
        </ul>
      )}

      {claim.isError && <p role="alert" className="text-center text-sm text-red-400">{errorMessage(claim.error)}</p>}
    </>
  )
}

function StepIndicator({ current }: { current: OnboardingStep['type'] }) {
  const currentIndex = STEP_LABELS.findIndex(([type]) => type === current)
  return (
    <ol aria-label="Etapas da escolha" className="flex justify-center gap-2 text-xs">
      {STEP_LABELS.map(([type, label], i) => (
        <li
          key={type}
          aria-current={type === current ? 'step' : undefined}
          className={cx(
            'rounded-full border px-3 py-1',
            type === current
              ? 'border-emerald-500 bg-emerald-900/30 font-semibold text-emerald-200'
              : i < currentIndex
                ? 'border-slate-700 text-slate-300'
                : 'border-slate-800 text-slate-400',
          )}
        >
          {i + 1}. {label}
        </li>
      ))}
    </ol>
  )
}

function StepHeading({ headingRef, children }: { headingRef: Ref<HTMLHeadingElement>; children: ReactNode }) {
  return (
    <h2 ref={headingRef} tabIndex={-1} className="text-center text-xl font-semibold text-slate-100 focus:outline-none">
      {children}
    </h2>
  )
}

function BackButton({ onClick, children }: { onClick: () => void; children: ReactNode }) {
  return (
    <div className="flex justify-center">
      <Button variant="secondary" size="sm" onClick={onClick}>
        <span aria-hidden="true">←</span> {children}
      </Button>
    </div>
  )
}

/** Card clicável de um passo (país ou liga): o botão inteiro é o alvo. */
const OPTION =
  'flex w-full flex-col items-start gap-1 rounded-lg border border-slate-800 bg-slate-900/60 p-5 text-left ' +
  'transition-colors hover:border-emerald-600 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400'

function CountryList({ countries, onPick }: { countries: WorldCountry[]; onPick: (c: WorldCountry) => void }) {
  return (
    <ul className="grid grid-cols-1 gap-4 sm:grid-cols-2" aria-label="Países">
      {countries.map((country) => {
        const free = availableClubsIn(country)
        return (
          <li key={country.country.code}>
            <button type="button" className={OPTION} onClick={() => onPick(country)}>
              <span className="flex items-center gap-2 text-lg font-semibold text-slate-100">
                <span aria-hidden="true" className="text-2xl">{country.country.flag}</span>
                {country.country.name}
              </span>
              <span className="text-sm text-slate-400">
                {countLabel(country.leagues.length, 'liga', 'ligas')} ·{' '}
                {free === 0 ? 'nenhum clube livre' : countLabel(free, 'clube livre', 'clubes livres')}
              </span>
            </button>
          </li>
        )
      })}
    </ul>
  )
}

function LeagueList({ leagues, onPick }: { leagues: WorldLeague[]; onPick: (l: WorldLeague) => void }) {
  return (
    <ul className="grid grid-cols-1 gap-4 sm:grid-cols-2" aria-label="Ligas">
      {leagues.map((league) => (
        <li key={league.id}>
          <button type="button" className={OPTION} onClick={() => onPick(league)}>
            <span className="text-lg font-semibold text-slate-100">{league.name}</span>
            <span className="text-sm text-slate-400">
              {tierLabel(league.tier)} · Temporada {league.seasonLabel}
            </span>
            <span className={cx('text-sm', league.availableClubCount === 0 ? 'text-amber-300' : 'text-emerald-300')}>
              {league.availableClubCount === 0
                ? 'Nenhum clube livre'
                : `${league.availableClubCount} de ${countLabel(league.clubCount, 'clube livre', 'clubes livres')}`}
            </span>
          </button>
        </li>
      ))}
    </ul>
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
    <article aria-label={club.name} className="flex h-full flex-col gap-3 rounded-lg border border-slate-800 bg-slate-900/60 p-5">
      <div>
        <h3 className="text-lg font-semibold text-slate-100">{club.name}</h3>
        <p className="text-xs text-slate-400">
          <span aria-hidden="true">{club.country.flag}</span> {club.country.name} · {club.competitionName}
        </p>
      </div>
      <dl className="grid grid-cols-3 gap-2 text-center">
        <Metric label="Caixa" value={formatMoney(club.cash)} />
        <Metric label="Estádio" value={formatSeats(club.stadiumCapacity)} />
        <Metric label="Força méd." value={String(club.averageOverall)} />
      </dl>
      <Button onClick={onClaim} disabled={disabled} className="mt-auto">
        {pending ? 'Reivindicando…' : 'Treinar este clube'}
      </Button>
    </article>
  )
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-md bg-slate-800/50 px-2 py-1.5">
      <dt className="text-[10px] uppercase tracking-wide text-text-muted">{label}</dt>
      <dd className="text-sm font-semibold text-slate-100">{value}</dd>
    </div>
  )
}
