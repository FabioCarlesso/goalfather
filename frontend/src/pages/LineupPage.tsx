import { useState } from 'react'
import { useClub } from '../api/queries/useClub'
import { useSaveLineup } from '../api/queries/useSaveLineup'
import { useMyClubId } from '../auth/useMyClubId'
import {
  FORMATIONS,
  FORMATION_SLOTS,
  POSITION_ABBR,
  POSITION_LABEL,
} from '../domain/formations'
import type { Club, Formation, Player } from '../domain/types'

/**
 * Carrega o clube e só então monta o formulário. O `key={club.id}` remonta
 * `LineupForm` ao trocar de clube, fazendo o estado reiniciar a partir da
 * escalação salva via inicializadores de `useState` — sem `useEffect`.
 */
export function LineupPage() {
  const myClubId = useMyClubId()
  const { data: club, isLoading } = useClub(myClubId)

  if (isLoading || !club) return <p className="text-slate-400">Carregando…</p>

  return <LineupForm key={club.id} club={club} />
}

function LineupForm({ club }: { club: Club }) {
  const save = useSaveLineup(club.id)

  // Estado inicializado a partir da escalação salva (estado derivado só na
  // montagem); edições subsequentes ficam locais ao formulário.
  const [formation, setFormation] = useState<Formation>(club.lineup?.formation ?? '4-4-2')
  const [slots, setSlots] = useState<(number | null)[]>(() => {
    const ids = club.lineup?.playerIds ?? []
    return Array.from({ length: 11 }, (_, i) => ids[i] ?? null)
  })

  const positions = FORMATION_SLOTS[formation]
  const complete = slots.every((id) => id !== null)

  const setSlot = (i: number, playerId: number | null) => {
    setSlots((prev) => {
      const next = [...prev]
      next[i] = playerId
      // Remove duplicatas: se o mesmo jogador for selecionado em outro slot, limpa o outro
      if (playerId !== null) {
        for (let j = 0; j < next.length; j++) {
          if (j !== i && next[j] === playerId) next[j] = null
        }
      }
      return next
    })
  }

  const onFormationChange = (f: Formation) => {
    setFormation(f)
    setSlots(Array(11).fill(null))
  }

  const onSubmit = () => {
    if (!complete) return
    save.mutate({ formation, playerIds: slots as number[] })
  }

  return (
    <section>
      <header className="mb-4 flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-slate-100">Escalação</h1>
          <p className="text-sm text-slate-400">
            {slots.filter((s) => s !== null).length}/11 titulares
          </p>
        </div>
        <div className="flex items-center gap-3">
          <label className="text-sm text-slate-400">Formação</label>
          <select
            value={formation}
            onChange={(e) => onFormationChange(e.target.value as Formation)}
            className="rounded-md border border-slate-700 bg-slate-900 px-2 py-1 text-sm text-slate-100"
          >
            {FORMATIONS.map((f) => (
              <option key={f} value={f}>{f}</option>
            ))}
          </select>
        </div>
      </header>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2 mb-4">
        {positions.map((pos, i) => (
          <SlotPicker
            key={i}
            slotIndex={i}
            position={pos}
            squad={club.squad}
            selectedId={slots[i] ?? null}
            otherSelected={slots.filter((_, j) => j !== i).filter((id): id is number => id !== null)}
            onChange={(id) => setSlot(i, id)}
          />
        ))}
      </div>

      <div className="flex items-center justify-between gap-3">
        <SaveFeedback save={save} />
        <button
          onClick={onSubmit}
          disabled={!complete || save.isPending}
          className="rounded-md bg-emerald-600 hover:bg-emerald-500 disabled:bg-slate-700 disabled:cursor-not-allowed text-white px-4 py-2 text-sm font-medium transition-colors"
        >
          {save.isPending ? 'Salvando…' : 'Salvar escalação'}
        </button>
      </div>
    </section>
  )
}

function SlotPicker({
  slotIndex,
  position,
  squad,
  selectedId,
  otherSelected,
  onChange,
}: {
  slotIndex: number
  position: Player['position']
  squad: Player[]
  selectedId: number | null
  otherSelected: number[]
  onChange: (id: number | null) => void
}) {
  // Lesionados ficam indisponíveis para escalação (issue #2).
  const available = squad.filter(
    (p) => p.position === position && !otherSelected.includes(p.id) && !p.injured,
  )

  return (
    <label className="flex items-center gap-2 rounded-md border border-slate-800 bg-slate-900/40 px-3 py-2">
      <span className="text-xs uppercase tracking-wide text-slate-500 w-16">
        {POSITION_ABBR[position]} {slotIndex + 1}
      </span>
      <select
        value={selectedId ?? ''}
        onChange={(e) => onChange(e.target.value ? Number(e.target.value) : null)}
        className="flex-1 bg-transparent text-sm text-slate-100 outline-none"
      >
        <option value="">— {POSITION_LABEL[position]} —</option>
        {available.map((p) => (
          <option key={p.id} value={p.id}>
            {p.name} ({p.overall})
          </option>
        ))}
      </select>
    </label>
  )
}

function SaveFeedback({ save }: { save: ReturnType<typeof useSaveLineup> }) {
  if (save.isError)   return <span className="text-sm text-red-400">Erro: {String(save.error)}</span>
  if (save.isSuccess) return <span className="text-sm text-emerald-400">Escalação salva ✓</span>
  return <span />
}
