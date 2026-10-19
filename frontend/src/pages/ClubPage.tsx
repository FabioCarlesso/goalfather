import { useState, type ReactNode } from 'react'
import { Link, Navigate, useParams } from 'react-router-dom'
import { useClub } from '../api/queries/useClub'
import { useCreateOffer, MIN_OFFER_CENTS } from '../api/queries/useOffers'
import { errorMessage } from '../api/errorMessages'
import { useMyClubId } from '../auth/useMyClubId'
import { formatMoney } from '../domain/formatters'
import type { Club, Player } from '../domain/types'

/**
 * Elenco de um clube rival (issue #63) — a vitrine de onde sai a proposta.
 * Clube com técnico humano mostra o formulário; clube da IA só o elenco, porque
 * proposta é negociação entre técnicos (o backend responde `SellerNotHuman`).
 */
export function ClubPage() {
  const { id } = useParams()
  const clubId = Number(id)
  const myClubId = useMyClubId()
  const { data: club, isLoading, error } = useClub(Number.isFinite(clubId) ? clubId : null)

  // O próprio clube já tem tela: a Dashboard.
  if (clubId === myClubId) return <Navigate to="/dashboard" replace />
  if (isLoading) return <p className="text-slate-400">Carregando clube…</p>
  if (error)     return <p className="text-red-400">{errorMessage(error)}</p>
  if (!club)     return null

  const human = club.ownerId != null

  return (
    <section className="space-y-6">
      <header>
        <Link to="/standings" className="text-xs text-slate-500 hover:text-slate-300">← Tabela</Link>
        <h1 className="text-3xl font-bold text-slate-100">{club.name}</h1>
        <p className="text-sm text-slate-400">
          {human ? 'Técnico humano — aceita propostas' : 'Controlado pela IA — não negocia propostas'}
        </p>
      </header>

      {human ? <OfferForm seller={club} myClubId={myClubId} /> : <SquadList squad={club.squad} />}
    </section>
  )
}

/** Elenco + formulário de proposta pelo jogador escolhido. */
function OfferForm({ seller, myClubId }: { seller: Club; myClubId: number }) {
  const create = useCreateOffer(myClubId)
  const [selected, setSelected] = useState<Player | null>(null)
  const [reais, setReais] = useState(MIN_OFFER_CENTS / 100)

  const valid = Number.isInteger(reais) && reais * 100 >= MIN_OFFER_CENTS

  const submit = () => {
    if (!selected || !valid) return
    create.mutate(
      { buyerClubId: myClubId, sellerClubId: seller.id, playerId: selected.id, amount: reais * 100 },
      { onSuccess: (result) => { if (result.type === 'Success') setSelected(null) } },
    )
  }

  return (
    <>
      {selected && (
        <div className="rounded-lg border border-emerald-800/60 bg-emerald-950/30 p-4 space-y-3">
          <h2 className="text-lg font-semibold text-slate-100">Proposta por {selected.name}</h2>
          <p className="text-sm text-slate-400">
            O {seller.name} tem algumas rodadas para responder. Se aceitar, o
            valor sai do seu caixa e o jogador entra no seu elenco na hora.
          </p>
          <div className="flex flex-wrap items-end gap-3">
            <label className="text-sm text-slate-400">
              Valor da proposta (R$)
              <input
                type="number"
                min={MIN_OFFER_CENTS / 100}
                step={1000}
                value={reais}
                onChange={(e) => setReais(Number(e.target.value))}
                className="mt-1 block w-40 rounded-md border border-slate-700 bg-slate-900 px-2 py-1 text-sm text-slate-100"
              />
            </label>
            <button
              onClick={submit}
              disabled={!valid || create.isPending}
              className="rounded-md bg-emerald-600 hover:bg-emerald-500 disabled:bg-slate-700 disabled:cursor-not-allowed text-white px-4 py-2 text-sm font-medium transition-colors"
            >
              {create.isPending ? 'Enviando…' : 'Enviar proposta'}
            </button>
            <button onClick={() => setSelected(null)} className="text-sm text-slate-400 hover:text-slate-200">
              Cancelar
            </button>
          </div>
          {!valid && (
            <p className="text-xs text-amber-300">A proposta mínima é de {formatMoney(MIN_OFFER_CENTS)}.</p>
          )}
        </div>
      )}

      <SquadList
        squad={seller.squad}
        action={(p) => (
          <button
            onClick={() => setSelected(p)}
            aria-pressed={selected?.id === p.id}
            className="rounded-md border border-slate-700 hover:border-emerald-600 hover:text-emerald-300 px-2 py-1 text-xs text-slate-400 transition-colors"
          >
            Propor
          </button>
        )}
      />
    </>
  )
}

function SquadList({ squad, action }: { squad: Player[]; action?: (player: Player) => ReactNode }) {
  return (
    <div>
      <h2 className="text-xl font-semibold text-slate-100 mb-3">Elenco</h2>
      <ul className="divide-y divide-slate-800 rounded-lg border border-slate-800">
        {squad.map((p) => (
          <li key={p.id} className="flex items-center justify-between px-4 py-2">
            <div className="min-w-0">
              <span className="font-medium text-slate-100">{p.name}</span>
              <span className="ml-2 text-xs text-slate-500">{p.position} · {p.age}a</span>
            </div>
            <div className="flex items-center gap-3">
              {p.star && <span className="text-yellow-400" title="Estrela">★</span>}
              <span className="text-slate-300 font-mono w-8 text-right">{p.overall}</span>
              {action?.(p)}
            </div>
          </li>
        ))}
      </ul>
    </div>
  )
}
