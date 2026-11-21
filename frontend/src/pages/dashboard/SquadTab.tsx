import { useState } from 'react'
import { useRenewContract } from '../../api/queries/useRenewContract'
import { useSellPlayer } from '../../api/queries/useSellPlayer'
import { ContractInfo } from '../../components/ContractInfo'
import { ContractOfferModal } from '../../components/ContractOfferModal'
import { SaleFeedback } from '../../components/SaleFeedback'
import { SellPlayerModal } from '../../components/SellPlayerModal'
import { SquadList } from '../../components/SquadList'
import { cooldownMessage, MIN_CONTRACT_SALARY_CENTS } from '../../domain/contractOffers'
import type { ContractOffer, Player, PlayerRefused, TransferResult } from '../../domain/types'
import { useDashboardClub } from './tabs'

/** Oferta inicial de renovação: o salário de hoje e 3 temporadas. */
const renewalOfferFor = (player: Player): ContractOffer => ({
  salary: Math.max(player.salary, MIN_CONTRACT_SALARY_CENTS),
  years: 3,
})

/** Aba "Elenco": a lista de jogadores, com a venda confirmada por modal e a renovação (issue #138). */
export function SquadTab() {
  const club = useDashboardClub()
  const sell = useSellPlayer()
  const renew = useRenewContract(club.id)
  // Estado local de UI: quem aguarda confirmação e o aviso da última venda.
  const [candidate, setCandidate] = useState<Player | null>(null)
  const [lastResult, setLastResult] = useState<TransferResult | null>(null)
  // Renovação: o jogador em negociação, a última recusa e o esfriamento.
  const [renewing, setRenewing] = useState<Player | null>(null)
  const [refusal, setRefusal] = useState<PlayerRefused | null>(null)
  const [coolingUntil, setCoolingUntil] = useState<string | null>(null)

  const confirmSale = (player: Player) => {
    setCandidate(null)
    sell.mutate(
      { clubId: club.id, playerId: player.id },
      { onSuccess: (result) => setLastResult(result) },
    )
  }

  const openRenewal = (player: Player) => {
    setRefusal(null)
    setCoolingUntil(null)
    setRenewing(player)
  }

  const submitRenewal = (player: Player, offer: ContractOffer) =>
    renew.mutate(
      { playerId: player.id, offer },
      {
        onSuccess: (result) => {
          switch (result.type) {
            case 'Renewed':
            case 'PlayerNotAvailable':
            case 'Conflict':
              setRenewing(null)
              return
            case 'PlayerRefused':
              setRefusal(result)
              return
            case 'TalksCoolingDown':
              setCoolingUntil(result.until)
              return
          }
        },
      },
    )

  return (
    <div className="space-y-6">
      {lastResult && <SaleFeedback result={lastResult} onDismiss={() => setLastResult(null)} />}

      <SquadList
        squad={club.squad}
        onSell={setCandidate}
        sellingPlayerId={sell.isPending ? sell.variables.playerId : null}
        sellDisabled={sell.isPending}
        onRenew={openRenewal}
      />

      <SellPlayerModal player={candidate} onConfirm={confirmSale} onCancel={() => setCandidate(null)} />

      {renewing && (
        <ContractOfferModal
          key={renewing.id}
          open
          title={`Renovar com ${renewing.name}`}
          description={
            <div className="space-y-1">
              <p>Contrato atual:</p>
              <ContractInfo player={renewing} />
              <p className="text-text-muted">
                Aceitando, o contrato novo substitui o atual e a duração recomeça hoje.
              </p>
            </div>
          }
          initialOffer={renewalOfferFor(renewing)}
          submitLabel="Oferecer renovação"
          pending={renew.isPending}
          refusal={refusal}
          blockedMessage={coolingUntil ? cooldownMessage(coolingUntil) : null}
          onSubmit={(offer) => submitRenewal(renewing, offer)}
          onClose={() => setRenewing(null)}
        />
      )}
    </div>
  )
}
