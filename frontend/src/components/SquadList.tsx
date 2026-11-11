import { POSITION_ABBR, POSITION_LABEL } from '../domain/formations'
import { injuryRoundsOut, staminaLevel } from '../domain/players'
import type { Availability, Player } from '../domain/types'
import { CountryFlag } from './CountryFlag'

interface Props {
  squad: Player[]
  /** Pede a venda do jogador — quem confirma é o modal da página. */
  onSell: (player: Player) => void
  /** Jogador com venda em voo (mostra "…"), ou `null`. */
  sellingPlayerId: number | null
  /** Trava todos os botões de venda (há uma venda em andamento). */
  sellDisabled: boolean
}

/** Elenco do próprio clube na Dashboard: forma física, lesão, estatísticas e venda. */
export function SquadList({ squad, onSell, sellingPlayerId, sellDisabled }: Props) {
  return (
    <div>
      <h2 className="text-xl font-semibold text-slate-100 mb-3">Elenco</h2>
      <ul className="divide-y divide-slate-800 rounded-lg border border-slate-800">
        {squad.map((p) => (
          <li key={p.id} className="flex flex-wrap items-center justify-between gap-2 px-4 py-2">
            <div className="min-w-0">
              <span className="mr-1.5"><CountryFlag country={p.nationality} /></span>
              <span className="font-medium text-slate-100">{p.name}</span>
              <span className="ml-2 text-xs text-slate-400">
                <abbr title={POSITION_LABEL[p.position]} className="no-underline">
                  {POSITION_ABBR[p.position]}
                </abbr>
                {' · '}{p.age}a
              </span>
              <InjuryBadge availability={p.availability} />
            </div>
            <div className="flex items-center gap-3">
              <StaminaBar stamina={p.stamina} />
              <PlayerStats player={p} />
              {p.star && <span className="text-yellow-400" title="Estrela">★</span>}
              <span className="text-slate-300 font-mono w-8 text-right">{p.overall}</span>
              <button
                type="button"
                onClick={() => onSell(p)}
                disabled={sellDisabled}
                aria-label={`Vender ${p.name}`}
                className="rounded-md border border-slate-700 hover:border-red-600 hover:text-red-400 disabled:opacity-50 px-2 py-1 text-xs text-slate-400 transition-colors"
              >
                {sellingPlayerId === p.id ? '…' : 'Vender'}
              </button>
            </div>
          </li>
        ))}
      </ul>
    </div>
  )
}

/** Badge de lesão com a duração restante (issue #54). */
function InjuryBadge({ availability }: { availability: Availability }) {
  const roundsOut = injuryRoundsOut(availability)
  if (roundsOut === null) return null
  return (
    <span
      className="ml-2 rounded bg-orange-900/40 px-1.5 py-0.5 text-[10px] font-semibold text-orange-300"
      title={`Lesionado — indisponível por mais ${roundsOut} rodada(s)`}
    >
      🚑 LESIONADO · {roundsOut} rod.
    </span>
  )
}

const STAMINA_COLOR = {
  fresh:     'bg-emerald-500',
  tired:     'bg-amber-500',
  exhausted: 'bg-red-500',
} as const

/** Barra de forma física (issue #54). A faixa de cor vem de `staminaLevel`. */
function StaminaBar({ stamina }: { stamina: number }) {
  return (
    <span className="flex items-center gap-1.5" title={`Forma física: ${stamina}%`}>
      <span className="h-1.5 w-12 overflow-hidden rounded-full bg-slate-800">
        <span className={`block h-full ${STAMINA_COLOR[staminaLevel(stamina)]}`} style={{ width: `${stamina}%` }} />
      </span>
      <span className="w-8 text-right font-mono text-[10px] text-slate-400">{stamina}%</span>
    </span>
  )
}

/** Estatísticas acumuladas do jogador (gols/cartões) ao lado da linha do elenco. */
function PlayerStats({ player }: { player: Player }) {
  return (
    <span className="flex items-center gap-2 text-xs font-mono text-slate-400">
      {player.goals > 0 && <span className="text-emerald-400" title="Gols">⚽ {player.goals}</span>}
      {player.yellowCards > 0 && <span className="text-yellow-300" title="Cartões amarelos">🟨 {player.yellowCards}</span>}
      {player.redCards > 0 && <span className="text-red-400" title="Cartões vermelhos">🟥 {player.redCards}</span>}
    </span>
  )
}
