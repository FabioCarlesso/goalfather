import { POSITION_ABBR, POSITION_LABEL } from '../domain/formations'
import type { Player } from '../domain/types'
import { Button } from './ui/Button'
import { Modal } from './ui/Modal'

interface Props {
  /** Jogador cuja venda aguarda confirmação; `null` mantém o modal fechado. */
  player: Player | null
  onConfirm: (player: Player) => void
  onCancel: () => void
}

/**
 * Confirmação da venda (issue #107), no lugar do `window.confirm` nativo. Não mostra
 * quanto entra no caixa: o contrato não expõe esse valor, e calculá-lo aqui
 * duplicaria a regra do `TransferRules` do backend.
 */
export function SellPlayerModal({ player, onConfirm, onCancel }: Props) {
  return (
    <Modal
      open={player != null}
      onClose={onCancel}
      title={player ? `Vender ${player.name}?` : 'Vender jogador?'}
      actions={
        player && (
          <>
            <Button variant="secondary" onClick={onCancel}>Cancelar</Button>
            <Button variant="danger" onClick={() => onConfirm(player)}>Confirmar venda</Button>
          </>
        )
      }
    >
      {player && (
        <div className="space-y-3">
          <dl className="grid grid-cols-3 gap-3">
            <div>
              <dt className="text-xs uppercase tracking-wide text-slate-400">Posição</dt>
              <dd className="text-slate-100">
                {POSITION_LABEL[player.position]} ({POSITION_ABBR[player.position]})
              </dd>
            </div>
            <div>
              <dt className="text-xs uppercase tracking-wide text-slate-400">Idade</dt>
              <dd className="text-slate-100">{player.age} anos</dd>
            </div>
            <div>
              <dt className="text-xs uppercase tracking-wide text-slate-400">OVR</dt>
              <dd className="font-mono text-slate-100">{player.overall}</dd>
            </div>
          </dl>
          <p className="text-slate-400">A venda é imediata e não pode ser desfeita.</p>
        </div>
      )}
    </Modal>
  )
}
