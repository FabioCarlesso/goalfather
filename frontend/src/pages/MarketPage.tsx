import { useEffect, useRef } from 'react'
import { useSearchParams } from 'react-router-dom'
import { PageTitle } from '../components/PageTitle'
import { MarketFilterBar } from '../components/MarketFilterBar'
import { useMarket } from '../api/queries/useMarket'
import { useBuyPlayer } from '../api/queries/useBuyPlayer'
import { useClub } from '../api/queries/useClub'
import { useMyClubId } from '../auth/useMyClubId'
import { formatMoney } from '../domain/formatters'
import { POSITION_ABBR } from '../domain/formations'
import {
  applyMarketFilters,
  parseMarketFilters,
  toMarketSearchParams,
  type MarketFilterState,
} from '../domain/marketFilters'
import { Badge } from '../components/ui/Badge'
import { Button } from '../components/ui/Button'
import { EmptyState } from '../components/ui/EmptyState'
import { ErrorState } from '../components/ui/ErrorState'
import { LoadingState } from '../components/ui/LoadingState'

export function MarketPage() {
  const myClubId = useMyClubId()
  const market = useMarket()
  const { data: club } = useClub(myClubId)
  // O resultado da compra vira toast dentro do próprio hook: o `switch` exaustivo
  // sobre `TransferResult` vive em `transferErrorMessage`, e erro HTTP é tratado
  // pelo `MutationCache` de `main.tsx`. Por isso a página não dispara toast.
  const buy = useBuyPlayer()
  const [searchParams, setSearchParams] = useSearchParams()
  const filters = parseMarketFilters(searchParams)

  // Última mudança PEDIDA. O router aplica cada navegação numa transição, então
  // `filters` pode estar atrás dela: partir dele apagaria a mudança anterior, como
  // uma ordenação perdida na primeira tecla da busca em um aparelho lento.
  const requestedFilters = useRef(filters)
  useEffect(() => {
    // Mudanças externas (voltar do navegador, link do menu) também valem.
    requestedFilters.current = parseMarketFilters(searchParams)
  }, [searchParams])

  if (market.isPending) {
    return (
      <>
        <PageTitle>Mercado</PageTitle>
        <LoadingState label="Carregando mercado…" shape="table" />
      </>
    )
  }
  // Só falha sem dados troca a tela: falha de refetch mantém a lista já carregada,
  // como o toast global (issue #23).
  if (market.isLoadingError) {
    return (
      <>
        <PageTitle>Mercado</PageTitle>
        <ErrorState error={market.error} onRetry={market.refetch} />
      </>
    )
  }

  const updateFilters = (patch: Partial<MarketFilterState>, options?: { replace: boolean }) => {
    const next = { ...requestedFilters.current, ...patch }
    requestedFilters.current = next
    setSearchParams(toMarketSearchParams(next), options)
  }

  const visible = applyMarketFilters(market.data, filters)

  return (
    <section>
      <PageTitle>Mercado</PageTitle>
      <div className="mb-4 flex flex-wrap items-baseline justify-between gap-x-4">
        <h1 className="text-3xl font-bold text-slate-100">Mercado</h1>
        <p className="text-sm text-slate-400">
          Caixa:{' '}
          <span className="font-mono text-slate-100">{club ? formatMoney(club.cash) : '—'}</span>
        </p>
      </div>

      {market.data.length === 0 ? (
        <EmptyState message="Nenhum jogador disponível no momento." />
      ) : (
        <>
          <MarketFilterBar
            filters={filters}
            onChange={updateFilters}
            onQueryChange={(query) => updateFilters({ query }, { replace: true })}
          />

          {visible.length === 0 ? (
            <div className="flex flex-wrap items-center gap-3">
              <p className="text-text-muted italic">Nenhum jogador encontrado.</p>
              <Button
                variant="secondary"
                size="sm"
                onClick={() => updateFilters({ position: null, query: '' })}
              >
                Limpar filtros
              </Button>
            </div>
          ) : (
            <ul aria-label="Jogadores à venda" className="space-y-2">
              {visible.map((entry) => (
                <li
                  key={entry.player.id}
                  className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2 rounded-lg border border-slate-800 bg-slate-900/60 px-4 py-3"
                >
                  <div className="min-w-0 flex-1 basis-40">
                    <div className="text-slate-100 font-medium truncate">{entry.player.name}</div>
                    <div className="text-xs text-text-muted">
                      {POSITION_ABBR[entry.player.position]} · {entry.player.age}a · OVR {entry.player.overall}
                    </div>
                  </div>
                  <div className="text-right">
                    <div className="text-slate-100 font-mono">{formatMoney(entry.price)}</div>
                    <div className="text-xs text-text-muted">salário {formatMoney(entry.player.salary)}</div>
                    {/* Comparação de exibição entre dois números do contrato, NÃO regra de
                        transferência: quem decide é o backend (`InsufficientFunds`), por isso
                        o botão continua habilitado. */}
                    {club && entry.price > club.cash && (
                      <Badge tone="warning">acima do seu caixa</Badge>
                    )}
                  </div>
                  <Button
                    onClick={() => buy.mutate({ clubId: myClubId, playerId: entry.player.id })}
                    disabled={buy.isPending}
                    loading={buy.isPending && buy.variables?.playerId === entry.player.id}
                    size="sm"
                    aria-label={`Comprar ${entry.player.name}`}
                  >
                    Comprar
                  </Button>
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </section>
  )
}
