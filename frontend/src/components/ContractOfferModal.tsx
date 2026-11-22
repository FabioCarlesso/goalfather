import { useState } from 'react'
import { offerMeetingRefusal } from '../domain/contractOffers'
import type { ContractOffer, PlayerRefused } from '../domain/types'
import { ContractTermsFields } from './ContractTermsFields'
import { PlayerRefusalNotice } from './PlayerRefusalNotice'
import { Button } from './ui/Button'
import { Modal } from './ui/Modal'

interface Props {
  open: boolean
  title: string
  /** Contexto acima dos campos (valor, contrato atual…). */
  description?: React.ReactNode
  initialOffer: ContractOffer
  submitLabel: string
  pending: boolean
  /** Última recusa do jogador a esta negociação, ou `null`. */
  refusal: PlayerRefused | null
  /** Aviso que trava o envio (ex.: conversa esfriada até tal data). */
  blockedMessage?: string | null
  onSubmit: (offer: ContractOffer) => void
  onClose: () => void
}

/**
 * Modal da oferta de contrato (issue #138): compra de livre no mercado e
 * renovação. Guarda só o estado do formulário; a resposta do jogador chega
 * pelo chamador (`refusal`), que a recebe do backend. O chamador remonta o
 * modal com `key` a cada jogador, para o formulário começar do zero.
 */
export function ContractOfferModal({
  open,
  title,
  description,
  initialOffer,
  submitLabel,
  pending,
  refusal,
  blockedMessage,
  onSubmit,
  onClose,
}: Props) {
  const [offer, setOffer] = useState<ContractOffer>(initialOffer)
  const blocked = Boolean(blockedMessage) || Boolean(refusal?.cooldownUntil)

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={title}
      actions={
        <>
          <Button variant="secondary" onClick={onClose}>Cancelar</Button>
          <Button onClick={() => onSubmit(offer)} disabled={pending || blocked || offer.salary <= 0} loading={pending}>
            {submitLabel}
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        {description}
        <ContractTermsFields value={offer} onChange={setOffer} />
        {refusal && (
          <PlayerRefusalNotice refusal={refusal} onMeetDemand={() => setOffer(offerMeetingRefusal(offer, refusal))} />
        )}
        {blockedMessage && <p role="alert" className="text-sm text-amber-300">{blockedMessage}</p>}
      </div>
    </Modal>
  )
}
