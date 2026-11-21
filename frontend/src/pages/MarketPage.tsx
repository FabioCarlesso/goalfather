import { useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { PageTitle } from '../components/PageTitle'
import { MarketFilterBar } from '../components/MarketFilterBar'
import { useMarket } from '../api/queries/useMarket'
import { useBuyPlayer } from '../api/queries/useBuyPlayer'
import { useClub } from '../api/queries/useClub'
import { useTransferWindow } from '../api/queries/useTransferWindow'
import { TransferWindowBadge } from '../components/TransferWindowBadge'
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
import { CountryFlag } from '../components/CountryFlag'
import { ContractOfferModal } from '../components/ContractOfferModal'
import { MIN_CONTRACT_SALARY_CENTS } from '../domain/contractOffers'
import type { ContractOffer, MarketEntry, PlayerRefused } from '../domain/types'

/** Oferta inicial ao livre: o piso salarial e 3 temporadas — se não bastar, ele diz o que quer. */
const INITIAL_OFFER: ContractOffer = { salary: MIN_CONTRACT_SALARY_CENTS, years: 3 }

export function MarketPage() {
  const myClubId = useMyClubId()
  const market = useMarket()
  const { data: club } = useClub(myClubId)
  // Janela de transferência do clube (issue #137): comprar fora dela é recusado pelo backend.
  const { data: transferWindow } = useTransferWindow(myClubId)
  // O resultado da compra vira toast dentro do próprio hook: o `switch` exaustivo
  // sobre `TransferResult` vive em `transferErrorMessage`, e erro HTTP é tratado
  // pelo `MutationCache` de `main.tsx`. Por isso a página não dispara toast.
  const buy = useBuyPlayer()
  // Estado local de UI: o livre com o modal de oferta aberto e a última recusa dele (issue #138).
  const [signing, setSigning] = useState<MarketEntry | null>(null)
  const [refusal, setRefusal] = useState<PlayerRefused | null>(null)
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

  const openOffer = (entry: MarketEntry) => {
    setRefusal(null)
    setSigning(entry)
  }
  const closeOffer = () => setSigning(null)
  const submitOffer = (entry: MarketEntry, contract: ContractOffer) =>
    buy.mutate(
      { clubId: myClubId, playerId: entry.player.id, contract },
      {
        onSuccess: (result) => {
          // Só a recusa do jogador fica no modal (o técnico ajusta a oferta);
          // os demais desfechos viram toast e fecham — não há o que ajustar aqui.
          if (result.type === 'PlayerRefused') setRefusal(result)
          else closeOffer()
        },
      },
    )

  return (
    <section>
      <PageTitle>Mercado</PageTitle>
      <div className="mb-4 flex flex-wrap items-baseline justify-between gap-x-4">
        <h1 className="text-3xl font-bold text-slate-100">Mercado</h1>
        <TransferWindowBadge status={transferWindow} />
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
                    <div className="text-slate-100 font-medium truncate">
                      <span className="mr-1.5"><CountryFlag country={entry.player.nationality} /></span>
                      {entry.player.name}
                    </div>
                    <div className="text-xs text-text-muted">
                      {POSITION_ABBR[entry.player.position]} · {entry.player.age}a · OVR {entry.player.overall}
                    </div>
                  </div>
                  <div className="text-right">
                    <div className="text-slate-100 font-mono">{formatMoney(entry.price)}</div>
                    {/* Valor de mercado (issue #135): calculado pelo backend, só exibido. */}
                    <div className="text-xs text-text-muted">valor {formatMoney(entry.player.marketValue)}</div>
                    {/* Do mercado só sai livre (issue #136): sem contrato, sem salário —
                        o salário nasce com o contrato assinado na compra. */}
                    <div className="text-xs text-text-muted">livre · sem contrato</div>
                    {/* Comparação de exibição entre dois números do contrato, NÃO regra de
                        transferência: quem decide é o backend (`InsufficientFunds`), por isso
                        o botão continua habilitado. */}
                    {club && entry.price > club.cash && (
                      <Badge tone="warning">acima do seu caixa</Badge>
                    )}
                  </div>
                  <Button
                    onClick={() => openOffer(entry)}
                    disabled={buy.isPending}
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

      {signing && (
        // `key` por jogador: o formulário do modal recomeça a cada livre.
        <ContractOfferModal
          key={signing.player.id}
          open
          title={`Contratar ${signing.player.name}`}
          description={
            <p>
              Sem contrato: {signing.player.name} só assina se aceitar o salário e a duração.
              Taxa ao mercado: <span className="font-mono text-slate-100">{formatMoney(signing.price)}</span>.
            </p>
          }
          initialOffer={INITIAL_OFFER}
          submitLabel="Oferecer contrato"
          pending={buy.isPending}
          refusal={refusal}
          onSubmit={(offer) => submitOffer(signing, offer)}
          onClose={closeOffer}
        />
      )}
    </section>
  )
}
