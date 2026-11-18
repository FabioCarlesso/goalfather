import { formatGameDate } from '../domain/calendar'
import { formatMoney } from '../domain/formatters'
import type { Player } from '../domain/types'
import { Badge } from './ui/Badge'

/**
 * Salário e término do contrato de um jogador do elenco (issue #136), com
 * destaque para o contrato na última temporada. Apresentação pura: quem diz se
 * é o último ano é o backend (`contract.finalSeason`) — a UI não sabe a data
 * do jogo nem quando a temporada da liga termina.
 */
export function ContractInfo({ player }: { player: Pick<Player, 'name' | 'salary' | 'contract'> }) {
  const { contract } = player
  if (!contract) {
    return <span className="text-xs text-text-muted">livre</span>
  }
  return (
    <span className="flex flex-wrap items-center gap-x-1.5 gap-y-0.5 text-xs text-text-muted">
      <span className="font-mono" title="Salário mensal do contrato">{formatMoney(player.salary)}/mês</span>
      <span title={`Contrato de ${player.name} até ${formatGameDate(contract.end)}`}>
        até {formatGameDate(contract.end)}
      </span>
      {contract.finalSeason && (
        <Badge tone="warning" title="Contrato vence na próxima virada da temporada da liga">
          ÚLTIMO ANO
        </Badge>
      )}
    </span>
  )
}
