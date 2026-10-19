import type { ReactNode } from 'react'
import { formatMoney } from '../domain/formatters'
import {
  expiryReasonLabel,
  isPendingOffer,
  offerStatusLabel,
  receivedOffers,
  sentOffers,
} from '../domain/offers'
import type { TransferOffer } from '../domain/types'
import type { OfferAction } from '../api/queries/useOffers'

/** Quantas propostas já resolvidas aparecem por lado — o resto é histórico. */
const RECENT_RESOLVED = 5

interface Props {
  offers: TransferOffer[]
  clubId: number
  onAction: (offerId: number, action: OfferAction) => void
  /** Proposta com comando em voo — os botões dela ficam travados. */
  busyOfferId?: number | null
}

/**
 * Propostas entre técnicos na Dashboard (issue #63): as recebidas, com
 * aceitar/recusar, e as enviadas, com retirar. O painel só exibe e dispara —
 * quem decide se a transferência pode acontecer é o backend.
 */
export function OffersPanel({ offers, clubId, onAction, busyOfferId = null }: Props) {
  const received = receivedOffers(offers, clubId)
  const sent = sentOffers(offers, clubId)
  const pendingReceived = received.filter(isPendingOffer).length

  return (
    <section
      aria-labelledby="offers-heading"
      className="rounded-lg border border-slate-800 bg-slate-900/60 p-4 space-y-4"
    >
      <div className="flex items-center justify-between">
        <h2 id="offers-heading" className="text-lg font-semibold text-slate-100">
          Propostas
          {pendingReceived > 0 && (
            <span className="ml-2 rounded-full bg-amber-500 px-2 py-0.5 text-xs font-bold text-slate-950">
              {pendingReceived} nova{pendingReceived === 1 ? '' : 's'}
            </span>
          )}
        </h2>
        <span className="text-xs text-slate-500">entre técnicos da liga</span>
      </div>

      <OfferList
        title="Recebidas"
        emptyText="Nenhuma proposta recebida."
        offers={received}
        describe={(o) => (
          <>
            <strong className="text-slate-100">{o.buyerClubName}</strong> oferece{' '}
            <span className="font-mono text-slate-100">{formatMoney(o.amount)}</span> por{' '}
            <strong className="text-slate-100">{o.playerName}</strong>
          </>
        )}
        actions={(o) => (
          <>
            <ActionButton
              label="Aceitar"
              tone="accept"
              disabled={busyOfferId === o.id}
              onClick={() => onAction(o.id, 'accept')}
            />
            <ActionButton
              label="Recusar"
              tone="neutral"
              disabled={busyOfferId === o.id}
              onClick={() => onAction(o.id, 'reject')}
            />
          </>
        )}
      />

      <OfferList
        title="Enviadas"
        emptyText="Nenhuma proposta enviada. Veja os elencos rivais na Tabela."
        offers={sent}
        describe={(o) => (
          <>
            <span className="font-mono text-slate-100">{formatMoney(o.amount)}</span> por{' '}
            <strong className="text-slate-100">{o.playerName}</strong> ({o.sellerClubName})
          </>
        )}
        actions={(o) => (
          <ActionButton
            label="Retirar"
            tone="neutral"
            disabled={busyOfferId === o.id}
            onClick={() => onAction(o.id, 'withdraw')}
          />
        )}
      />
    </section>
  )
}

function OfferList({
  title,
  emptyText,
  offers,
  describe,
  actions,
}: {
  title: string
  emptyText: string
  offers: TransferOffer[]
  describe: (offer: TransferOffer) => ReactNode
  actions: (offer: TransferOffer) => ReactNode
}) {
  const pending = offers.filter(isPendingOffer)
  const resolved = offers.filter((o) => !isPendingOffer(o)).slice(0, RECENT_RESOLVED)

  return (
    <div>
      <h3 className="mb-2 text-xs uppercase tracking-wide text-slate-500">{title}</h3>
      {pending.length === 0 && resolved.length === 0 ? (
        <p className="text-sm italic text-slate-500">{emptyText}</p>
      ) : (
        <ul className="divide-y divide-slate-800 rounded-md border border-slate-800">
          {pending.map((o) => (
            <li key={o.id} className="flex flex-wrap items-center justify-between gap-2 px-3 py-2 text-sm text-slate-300">
              <span>
                {describe(o)}
                <span className="ml-2 text-xs text-slate-500">
                  expira em {o.roundsLeft} rodada{o.roundsLeft === 1 ? '' : 's'}
                </span>
              </span>
              <span className="flex gap-2">{actions(o)}</span>
            </li>
          ))}
          {resolved.map((o) => (
            <li key={o.id} className="flex flex-wrap items-center justify-between gap-2 px-3 py-2 text-sm text-slate-500">
              <span>{describe(o)}</span>
              <StatusTag offer={o} />
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

function StatusTag({ offer }: { offer: TransferOffer }) {
  const color = {
    Pending:   'text-amber-300',
    Accepted:  'text-emerald-400',
    Rejected:  'text-red-400',
    Withdrawn: 'text-slate-400',
    Expired:   'text-slate-400',
  }[offer.status.type]
  const reason = offer.status.type === 'Expired' ? expiryReasonLabel(offer.status.reason) : undefined
  return (
    <span className={`text-xs font-semibold ${color}`} title={reason}>
      {offerStatusLabel(offer.status)}
    </span>
  )
}

function ActionButton({
  label,
  tone,
  disabled,
  onClick,
}: { label: string; tone: 'accept' | 'neutral'; disabled: boolean; onClick: () => void }) {
  const toneClasses = tone === 'accept'
    ? 'bg-emerald-600 hover:bg-emerald-500 text-white border-transparent'
    : 'border-slate-700 text-slate-300 hover:border-slate-500'
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      className={`rounded-md border px-2.5 py-1 text-xs font-medium transition-colors disabled:opacity-50 ${toneClasses}`}
    >
      {label}
    </button>
  )
}
