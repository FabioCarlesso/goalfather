// Escalação visual em campinho (issue #110). Componente de apresentação: recebe
// formação, slots e elenco por props e emite as trocas por callback — quem
// guarda o rascunho da escalação (slots, dirty, restaurar) é a `LineupPage`.
// O único estado daqui é a seleção em curso (o "primeiro clique"), que é estado
// local de UI e por isso fica num `useState`.
//
// Nada de força do time: o campo só EXIBE overall, stamina e estrela, que já
// vêm prontos do contrato. "Fora de posição" é só comparar a posição do jogador
// com a do slot — leitura, não regra.
import { useState } from 'react'
import { FORMATION_SLOTS, POSITION_ABBR, POSITION_LABEL } from '../domain/formations'
import { injuryRoundsOut, isInjured, staminaLevel } from '../domain/players'
import type { Formation, Player, Position } from '../domain/types'
import { cx } from './ui/cx'

export interface FormationGridProps {
  formation: Formation
  /** Os 11 slots, na ordem de `FORMATION_SLOTS[formation]`; `null` = vazio. */
  slots: readonly (number | null)[]
  squad: readonly Player[]
  /** Põe `playerId` no slot (ou o esvazia com `null`); quem estava lá vai para a reserva. */
  onAssign: (slotIndex: number, playerId: number | null) => void
  /** Troca de lugar os ocupantes de dois slots. */
  onSwap: (a: number, b: number) => void
}

/**
 * O primeiro clique da troca. União discriminada pelo `kind` — o par TS do
 * `sealed interface` Kotlin: o `playerId` só existe quando a seleção é de reserva.
 */
type Selection = { kind: 'slot'; index: number } | { kind: 'reserve'; playerId: number } | null

/** Setores de cima (ataque) para baixo (gol): a ordem visual E a do Tab. */
const SECTORS: readonly Position[] = ['FW', 'MF', 'CB', 'GK']

const SECTOR_LABEL: Record<Position, string> = {
  FW: 'Ataque',
  MF: 'Meio-campo',
  CB: 'Defesa',
  GK: 'Gol',
}

const STAMINA_BAR: Record<ReturnType<typeof staminaLevel>, string> = {
  fresh: 'bg-emerald-400',
  tired: 'bg-amber-400',
  exhausted: 'bg-red-400',
}

/** "Marcos Figueiredo" → "Figueiredo": o sobrenome cabe no slot em 375px. */
const shortName = (name: string): string => name.trim().split(/\s+/).at(-1) ?? name

/** Nome acessível do jogador: o que o slot mostra em forma de frase. */
const playerSummary = (p: Player): string =>
  [
    p.name,
    `${POSITION_LABEL[p.position]}`,
    `OVR ${p.overall}`,
    `stamina ${p.stamina}%`,
    p.star ? 'estrela' : null,
  ]
    .filter(Boolean)
    .join(', ')

export function FormationGrid({ formation, slots, squad, onAssign, onSwap }: FormationGridProps) {
  const [selection, setSelection] = useState<Selection>(null)

  const positions = FORMATION_SLOTS[formation]
  const byId = new Map(squad.map((p) => [p.id, p]))
  const starterIds = new Set(slots.filter((id): id is number => id !== null))
  // Reservas = elenco fora dos 11, agrupado por posição na ordem do campo
  // (GL, ZG, MC, AT). `sort` é estável, então dentro da posição vale a do elenco.
  const reserves = squad
    .filter((p) => !starterIds.has(p.id))
    .sort((a, b) => SECTORS.indexOf(b.position) - SECTORS.indexOf(a.position))

  // Rótulo do slot: "Zagueiro 2" é o segundo ZG da formação, não o índice global.
  const slotLabel = (i: number): string => {
    const pos = positions[i]!
    const nth = positions.slice(0, i + 1).filter((p) => p === pos).length
    return `${POSITION_LABEL[pos]} ${nth}`
  }

  const onSlot = (i: number) => {
    if (selection?.kind === 'reserve') {
      onAssign(i, selection.playerId)
      setSelection(null)
    } else if (selection?.kind === 'slot') {
      if (selection.index !== i) onSwap(selection.index, i)
      setSelection(null)
    } else {
      setSelection({ kind: 'slot', index: i })
    }
  }

  const onReserve = (playerId: number) => {
    if (selection?.kind === 'slot') {
      onAssign(selection.index, playerId)
      setSelection(null)
    } else {
      setSelection(selection?.kind === 'reserve' && selection.playerId === playerId ? null : { kind: 'reserve', playerId })
    }
  }

  const selectedSlot = selection?.kind === 'slot' ? selection.index : null
  const selectedOccupant = selectedSlot !== null ? byId.get(slots[selectedSlot] ?? -1) : undefined

  const hint =
    selection?.kind === 'slot'
      ? `${slotLabel(selection.index)} selecionado: escolha um reserva ou outro titular para trocar.`
      : selection?.kind === 'reserve'
        ? `${byId.get(selection.playerId)?.name ?? 'Reserva'} selecionado: escolha o slot no campo.`
        : 'Selecione um titular no campo e depois um reserva (ou outro titular) para trocar.'

  return (
    <div className="grid grid-cols-1 gap-4 lg:grid-cols-[minmax(0,1fr)_18rem]">
      <p aria-live="polite" className="text-sm text-slate-400 lg:col-span-2">
        {hint}
      </p>

      {/* O campo: uma linha de CSS Grid por setor, com tantas colunas quantos
          slots o setor tem nesta formação. */}
      <div
        role="group"
        aria-label={`Campo — formação ${formation}`}
        className="flex flex-col justify-between gap-3 rounded-lg border-2 border-emerald-700 bg-emerald-900 px-2 py-4 sm:px-4"
      >
        {SECTORS.map((sector) => {
          const indices = positions.flatMap((pos, i) => (pos === sector ? [i] : []))
          return (
            <div
              key={sector}
              role="group"
              aria-label={SECTOR_LABEL[sector]}
              data-sector={sector}
              className="grid justify-center gap-1.5 sm:gap-3"
              style={{ gridTemplateColumns: `repeat(${indices.length}, minmax(0, 7rem))` }}
            >
              {indices.map((i) => (
                <SlotButton
                  key={i}
                  index={i}
                  label={slotLabel(i)}
                  position={positions[i]!}
                  player={byId.get(slots[i] ?? -1)}
                  selected={selectedSlot === i}
                  onClick={() => onSlot(i)}
                />
              ))}
            </div>
          )
        })}
      </div>

      <section aria-labelledby="reserves-title" className="rounded-lg border border-slate-800 bg-slate-900/40 p-3">
        <div className="mb-2 flex items-center justify-between gap-2">
          <h2 id="reserves-title" className="text-sm font-semibold text-slate-100">
            Reservas <span className="text-slate-400">({reserves.length})</span>
          </h2>
          {selectedOccupant && selectedSlot !== null && (
            <button
              type="button"
              onClick={() => {
                onAssign(selectedSlot, null)
                setSelection(null)
              }}
              className="rounded border border-slate-700 px-2 py-0.5 text-xs text-slate-300 hover:border-slate-500 hover:text-slate-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400"
            >
              Tirar {shortName(selectedOccupant.name)} do time
            </button>
          )}
        </div>
        {reserves.length === 0 ? (
          <p className="text-sm text-slate-400">Todo o elenco está em campo.</p>
        ) : (
          <ul className="space-y-1">
            {reserves.map((p) => (
              <li key={p.id}>
                <ReserveButton
                  player={p}
                  selected={selection?.kind === 'reserve' && selection.playerId === p.id}
                  onClick={() => onReserve(p.id)}
                />
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  )
}

function StaminaBar({ stamina }: { stamina: number }) {
  return (
    <span aria-hidden="true" className="block h-1 w-full overflow-hidden rounded bg-slate-700">
      <span
        className={cx('block h-full', STAMINA_BAR[staminaLevel(stamina)])}
        style={{ width: `${Math.max(0, Math.min(100, stamina))}%` }}
      />
    </span>
  )
}

const FOCUS = 'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-yellow-300'

function SlotButton({
  index,
  label,
  position,
  player,
  selected,
  onClick,
}: {
  /** Índice no array de slots — exposto em `data-slot` para os testes lerem a ordem. */
  index: number
  label: string
  position: Position
  player: Player | undefined
  selected: boolean
  onClick: () => void
}) {
  const offPosition = player !== undefined && player.position !== position
  const name = player
    ? `${label}: ${playerSummary(player)}${offPosition ? ', fora de posição' : ''}`
    : `${label}: vazio`

  return (
    // O nome acessível vem de um texto `sr-only`, não de `aria-label`: com
    // `aria-label` o nome substitui o conteúdo e deixa de conter o texto
    // visível ("Alves 75"), o que o axe acusa (label-content-name-mismatch,
    // WCAG 2.5.3) e atrapalha quem usa controle por voz. O visual fica
    // `aria-hidden` para não ser lido duas vezes.
    <button
      type="button"
      aria-pressed={selected}
      data-slot={index}
      data-player-id={player?.id ?? ''}
      onClick={onClick}
      className={cx(
        'flex min-h-16 min-w-0 flex-col items-center justify-center gap-0.5 rounded-md border px-1 py-1.5 text-center',
        FOCUS,
        player ? 'bg-slate-950/85 text-slate-100' : 'border-dashed bg-emerald-950/60 text-emerald-100',
        offPosition ? 'border-amber-400' : player ? 'border-slate-600' : 'border-emerald-500',
        selected && 'ring-2 ring-yellow-300',
      )}
    >
      <span className="sr-only">{name}</span>
      {player ? (
        <span aria-hidden="true" className="contents">
          <span className="flex w-full min-w-0 items-center justify-center gap-0.5 text-xs font-medium">
            {player.star && <span aria-hidden="true" className="text-yellow-400">★</span>}
            <span className="truncate">{shortName(player.name)}</span>
          </span>
          <span className="text-xs tabular-nums text-slate-300">
            {player.overall}
            {offPosition && (
              <span aria-hidden="true" className="ml-1 font-semibold text-amber-300" title="Fora de posição">
                {POSITION_ABBR[player.position]}!
              </span>
            )}
          </span>
          <StaminaBar stamina={player.stamina} />
        </span>
      ) : (
        <span aria-hidden="true" className="text-xs font-semibold">{POSITION_ABBR[position]}</span>
      )}
    </button>
  )
}

function ReserveButton({
  player,
  selected,
  onClick,
}: {
  player: Player
  selected: boolean
  onClick: () => void
}) {
  // Lesionados aparecem, mas não podem entrar (issues #2/#54): o backend
  // recusaria com INJURED_PLAYERS, então o botão já nasce desabilitado.
  const roundsOut = injuryRoundsOut(player.availability)
  const injured = isInjured(player)
  const name = `${playerSummary(player)}${roundsOut !== null ? `, lesionado por ${roundsOut} rodada(s)` : ''}`

  return (
    <button
      type="button"
      aria-label={name}
      aria-pressed={injured ? undefined : selected}
      disabled={injured}
      onClick={onClick}
      className={cx(
        'flex w-full items-center gap-2 rounded-md border px-2 py-1.5 text-left text-sm',
        FOCUS,
        injured
          ? 'cursor-not-allowed border-slate-800 bg-slate-900/40 text-slate-400'
          : 'border-slate-700 bg-slate-900 text-slate-100 hover:border-slate-500',
        selected && 'ring-2 ring-yellow-300',
      )}
    >
      <span className="w-6 shrink-0 text-xs font-semibold text-slate-400">{POSITION_ABBR[player.position]}</span>
      <span className="min-w-0 flex-1 truncate">
        {player.star && <span aria-hidden="true" className="mr-0.5 text-yellow-400">★</span>}
        {player.name}
      </span>
      {roundsOut !== null && (
        <span aria-hidden="true" className="shrink-0 rounded bg-red-900/40 px-1.5 py-0.5 text-[10px] font-semibold text-red-300">
          Lesionado
        </span>
      )}
      <span className="w-6 shrink-0 text-right text-xs tabular-nums text-slate-300">{player.overall}</span>
      <span className="w-10 shrink-0">
        <StaminaBar stamina={player.stamina} />
      </span>
    </button>
  )
}
