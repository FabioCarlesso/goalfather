import { useState } from 'react'
import { useSellPlayer } from '../../api/queries/useSellPlayer'
import { SaleFeedback } from '../../components/SaleFeedback'
import { SellPlayerModal } from '../../components/SellPlayerModal'
import { SquadList } from '../../components/SquadList'
import type { Player, TransferResult } from '../../domain/types'
import { useDashboardClub } from './tabs'

/** Aba "Elenco": a lista de jogadores, com a venda confirmada por modal. */
export function SquadTab() {
  const club = useDashboardClub()
  const sell = useSellPlayer()
  // Estado local de UI: quem aguarda confirmação e o aviso da última venda.
  const [candidate, setCandidate] = useState<Player | null>(null)
  const [lastResult, setLastResult] = useState<TransferResult | null>(null)

  const confirmSale = (player: Player) => {
    setCandidate(null)
    sell.mutate(
      { clubId: club.id, playerId: player.id },
      { onSuccess: (result) => setLastResult(result) },
    )
  }

  return (
    <div className="space-y-6">
      {lastResult && <SaleFeedback result={lastResult} onDismiss={() => setLastResult(null)} />}

      <SquadList
        squad={club.squad}
        onSell={setCandidate}
        sellingPlayerId={sell.isPending ? sell.variables.playerId : null}
        sellDisabled={sell.isPending}
      />

      <SellPlayerModal player={candidate} onConfirm={confirmSale} onCancel={() => setCandidate(null)} />
    </div>
  )
}
