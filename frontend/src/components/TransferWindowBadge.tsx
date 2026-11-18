import { transferWindowLabel } from '../domain/transferWindow'
import type { TransferWindowStatus } from '../domain/types'
import { Badge } from './ui/Badge'

/**
 * Selo da janela de transferência (issue #137): "Janela aberta até …" ou
 * "Mercado fechado · abre em …". Apresentação pura — a situação vem do backend.
 */
export function TransferWindowBadge({ status }: { status: TransferWindowStatus | undefined }) {
  if (!status) return null
  const tone = status.type === 'Open' ? (status.scope === 'DOMESTIC_ONLY' ? 'warning' : 'success') : 'neutral'
  return (
    <Badge tone={tone} data-testid="transfer-window" className="text-xs">
      {transferWindowLabel(status)}
    </Badge>
  )
}
