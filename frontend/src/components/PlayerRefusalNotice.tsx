import { cooldownMessage, refusalMessage } from '../domain/contractOffers'
import type { PlayerRefused } from '../domain/types'
import { Button } from './ui/Button'

interface Props {
  refusal: PlayerRefused
  /** Copia o pedido dele para o formulário. Ausente quando a conversa esfriou. */
  onMeetDemand?: () => void
}

/**
 * A resposta do jogador (issue #138), com o motivo e o que ele aceitaria. É o
 * backend quem calcula tudo; aqui só se mostra — e, se a conversa não esfriou,
 * oferece copiar o pedido para o formulário.
 */
export function PlayerRefusalNotice({ refusal, onMeetDemand }: Props) {
  return (
    <div role="alert" className="space-y-2 rounded-md border border-amber-800/60 bg-amber-950/30 p-3 text-sm text-amber-200">
      <p>{refusalMessage(refusal)}</p>
      {refusal.cooldownUntil && <p>{cooldownMessage(refusal.cooldownUntil)}</p>}
      {onMeetDemand && !refusal.cooldownUntil && (
        <Button variant="secondary" size="sm" onClick={onMeetDemand}>
          Oferecer o que ele pede
        </Button>
      )}
    </div>
  )
}
