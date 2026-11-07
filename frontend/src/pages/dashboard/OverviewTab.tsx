import { useBoard } from '../../api/queries/useBoard'
import { useClubOffers, useOfferAction } from '../../api/queries/useOffers'
import { BoardPanel } from '../../components/BoardPanel'
import { OffersPanel } from '../../components/OffersPanel'
import { Stat } from '../../components/ui/Stat'
import { formatMoney, formatSeats } from '../../domain/formatters'
import { useDashboardClub } from './tabs'

/** Aba "Visão geral": números do clube, diretoria e propostas. */
export function OverviewTab() {
  const club = useDashboardClub()
  // Painel da diretoria (issue #62): consulta à parte, para um erro nela não
  // derrubar a Dashboard inteira — sem painel, o resto do clube segue de pé.
  const { data: board } = useBoard(club.id)
  // Propostas entre técnicos (issue #63): também à parte, pelo mesmo motivo.
  const { data: offers } = useClubOffers(club.id)
  const offerAction = useOfferAction(club.id)

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <Stat label="Caixa"              value={formatMoney(club.cash)} />
        <Stat label="Capacidade estádio" value={formatSeats(club.stadiumCapacity)} />
        <Stat label="Ingresso"           value={formatMoney(club.ticketPriceCents)} />
        <Stat label="Elenco"             value={`${club.squad.length} jogadores`} />
      </div>

      {board && <BoardPanel status={board} />}

      {offers && (
        <OffersPanel
          offers={offers}
          clubId={club.id}
          onAction={(offerId, action) => offerAction.mutate({ offerId, action })}
          busyOfferId={offerAction.isPending ? offerAction.variables?.offerId : null}
        />
      )}
    </div>
  )
}
