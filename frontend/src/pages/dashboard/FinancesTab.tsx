import { useExpandStadium } from '../../api/queries/useExpandStadium'
import { useSetTicketPrice } from '../../api/queries/useSetTicketPrice'
import { StadiumExpandPanel } from '../../components/StadiumExpandPanel'
import { TicketPricePanel } from '../../components/TicketPricePanel'
import { useDashboardClub } from './tabs'

/** Aba "Finanças": preço do ingresso e ampliação do estádio — a mesma conta. */
export function FinancesTab() {
  const club = useDashboardClub()
  const setPrice = useSetTicketPrice(club.id)
  const expand = useExpandStadium(club.id)

  return (
    <div className="space-y-6">
      <TicketPricePanel
        currentCents={club.ticketPriceCents}
        onSubmit={(cents) => setPrice.mutate(cents)}
        pending={setPrice.isPending}
        error={setPrice.error}
        succeeded={setPrice.isSuccess}
      />
      <StadiumExpandPanel
        cash={club.cash}
        onExpand={(additionalSeats) => expand.mutate({ additionalSeats })}
        pending={expand.isPending}
        error={expand.error}
        succeeded={expand.isSuccess}
      />
    </div>
  )
}
