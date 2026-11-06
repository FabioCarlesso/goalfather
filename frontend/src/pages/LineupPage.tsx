import { useEffect, useState } from 'react'
import { useBlocker } from 'react-router-dom'
import { PageTitle } from '../components/PageTitle'
import { useClub } from '../api/queries/useClub'
import { useSaveLineup } from '../api/queries/useSaveLineup'
import { errorMessage } from '../api/errorMessages'
import { useMyClubId } from '../auth/useMyClubId'
import {
  FORMATIONS,
  FORMATION_SLOTS,
  POSITION_ABBR,
  POSITION_LABEL,
  POSTURES,
  POSTURE_HINT,
  POSTURE_LABEL,
  remapSlots,
} from '../domain/formations'
import { isInjured } from '../domain/players'
import type { Club, Formation, Player, Posture } from '../domain/types'
import { Button } from '../components/ui/Button'
import { ErrorState } from '../components/ui/ErrorState'
import { LoadingState } from '../components/ui/LoadingState'
import { Modal } from '../components/ui/Modal'

/** O que o técnico edita nesta tela. */
interface LineupDraft {
  formation: Formation
  posture: Posture
  slots: (number | null)[]
}

const draftFrom = (lineup: Club['effectiveLineup']): LineupDraft => ({
  formation: lineup.formation,
  // A postura é opcional no schema `Lineup` — ausente, EQUILIBRADA, o mesmo
  // default do backend.
  posture: lineup.posture ?? 'BALANCED',
  slots: Array.from({ length: 11 }, (_, i) => lineup.playerIds[i] ?? null),
})

const sameDraft = (a: LineupDraft, b: LineupDraft) =>
  a.formation === b.formation &&
  a.posture === b.posture &&
  a.slots.every((id, i) => id === b.slots[i])

/**
 * Carrega o clube e só então monta o formulário. O `key={club.id}` remonta
 * `LineupForm` ao trocar de clube, fazendo o estado reiniciar a partir da
 * escalação EFETIVA via inicializadores de `useState` — sem `useEffect`.
 */
export function LineupPage() {
  const myClubId = useMyClubId()
  const club = useClub(myClubId)

  if (club.isPending) {
    return (
      <>
        <PageTitle>Escalação</PageTitle>
        <LoadingState label="Carregando escalação…" shape="list" />
      </>
    )
  }
  if (club.isLoadingError) {
    return (
      <>
        <PageTitle>Escalação</PageTitle>
        <ErrorState error={club.error} onRetry={club.refetch} />
      </>
    )
  }

  // Durante o refetch pós-salvamento, ou se ele falhar, `effectiveLineup` ainda é a
  // anterior: restaurá-la mostraria como limpo um time diferente do que está salvo.
  const serverStale = club.isFetching || club.isRefetchError

  return <LineupForm key={club.data.id} club={club.data} serverStale={serverStale} />
}

function LineupForm({ club, serverStale }: { club: Club; serverStale: boolean }) {
  const save = useSaveLineup(club.id)

  // Estado inicializado a partir da escalação EFETIVA (issue #87) — o time que
  // a engine põe em campo se a rodada começar agora, já com o 4-4-2 automático
  // e os lesionados trocados. Quem decide os titulares é o servidor; aqui só
  // se lê. Edições subsequentes ficam locais ao formulário.
  const [draft, setDraft] = useState(() => draftFrom(club.effectiveLineup))
  // Referência de "sem alterações" (issue #109): uma foto tirada ao abrir, ao
  // salvar e ao restaurar. Não é derivada de `club`, para que um refetch em
  // segundo plano não marque como alterado o que o técnico não tocou.
  const [baseline, setBaseline] = useState(draft)
  const { formation, posture, slots } = draft
  const dirty = !sameDraft(draft, baseline)
  const serverDraft = draftFrom(club.effectiveLineup)

  const autoNotice = club.lineupIsDefault
    ? 'Escalação automática — é este o time que entra em campo se você não salvar.'
    : club.lineupAdjusted
      ? 'Escalação ajustada — titulares lesionados ou vendidos foram trocados por reservas. É este o time que entra em campo se você não salvar.'
      : null

  const positions = FORMATION_SLOTS[formation]
  const complete = slots.every((id) => id !== null)

  const setSlot = (i: number, playerId: number | null) => {
    setDraft((prev) => {
      const next = [...prev.slots]
      next[i] = playerId
      // Remove duplicatas: se o mesmo jogador for selecionado em outro slot, limpa o outro
      if (playerId !== null) {
        for (let j = 0; j < next.length; j++) {
          if (j !== i && next[j] === playerId) next[j] = null
        }
      }
      return { ...prev, slots: next }
    })
  }

  const onFormationChange = (f: Formation) => {
    const positionOf = (id: number) => club.squad.find((p) => p.id === id)?.position
    setDraft((prev) => ({ ...prev, formation: f, slots: remapSlots(prev.slots, f, positionOf) }))
  }

  const onSubmit = () => {
    if (!complete) return
    // A foto é o que foi ENVIADO: uma edição feita durante o POST segue pendente.
    save.mutate(
      { formation, playerIds: slots as number[], posture },
      { onSuccess: () => setBaseline(draft) },
    )
  }

  const onRestore = () => {
    setDraft(serverDraft)
    setBaseline(serverDraft)
    save.reset()
  }

  // Navegação interna: só mudança de rota conta, não query string nem hash.
  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) => dirty && currentLocation.pathname !== nextLocation.pathname,
  )

  // O salvamento terminou com o diálogo aberto: nada mais se perde, e a saída que
  // o técnico pediu segue. Sem isso, o diálogo diria que o time salvo será perdido.
  useEffect(() => {
    if (!dirty && blocker.state === 'blocked') blocker.proceed()
  }, [dirty, blocker])

  // Fechar a aba ou recarregar: o texto do aviso é sempre o do navegador.
  useEffect(() => {
    if (!dirty) return
    const warn = (e: BeforeUnloadEvent) => e.preventDefault()
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [dirty])

  return (
    <section>
      <PageTitle>Escalação</PageTitle>
      <header className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-3xl font-bold text-slate-100">Escalação</h1>
          <p className="text-sm text-slate-400">
            {slots.filter((s) => s !== null).length}/11 titulares
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <label className="text-sm text-slate-400" htmlFor="formation">Formação</label>
          <select
            id="formation"
            value={formation}
            onChange={(e) => onFormationChange(e.target.value as Formation)}
            className="rounded-md border border-slate-700 bg-slate-900 px-2 py-1 text-sm text-slate-100"
          >
            {FORMATIONS.map((f) => (
              <option key={f} value={f}>{f}</option>
            ))}
          </select>

          <label className="text-sm text-slate-400" htmlFor="posture">Postura</label>
          <select
            id="posture"
            value={posture}
            onChange={(e) => setDraft((prev) => ({ ...prev, posture: e.target.value as Posture }))}
            className="rounded-md border border-slate-700 bg-slate-900 px-2 py-1 text-sm text-slate-100"
          >
            {POSTURES.map((p) => (
              <option key={p} value={p}>{POSTURE_LABEL[p]}</option>
            ))}
          </select>
        </div>
      </header>

      {autoNotice && (
        <p
          role="status"
          className="mb-4 rounded-md border border-amber-700/60 bg-amber-900/20 px-3 py-2 text-sm text-amber-300"
        >
          {autoNotice}
        </p>
      )}

      <p className="mb-4 rounded-md border border-slate-800 bg-slate-900/40 px-3 py-2 text-sm text-slate-400">
        {POSTURE_HINT[posture]}
      </p>

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

      <div className="flex flex-wrap items-center justify-between gap-3">
        <SaveFeedback save={save} dirty={dirty} />
        <div className="flex flex-wrap items-center gap-3">
          {dirty && <span className="text-sm text-amber-400">Alterações não salvas</span>}
          <Button
            variant="secondary"
            onClick={onRestore}
            disabled={sameDraft(draft, serverDraft) || save.isPending || serverStale}
          >
            Restaurar escalação automática
          </Button>
          <Button
            onClick={onSubmit}
            disabled={!complete || save.isPending}
          >
            {save.isPending ? 'Salvando…' : 'Salvar escalação'}
          </Button>
        </div>
      </div>

      {/* `reset`/`proceed` só existem com o blocker em 'blocked', daí o `?.`. */}
      <Modal
        open={blocker.state === 'blocked'}
        onClose={() => blocker.reset?.()}
        title="Sair sem salvar a escalação?"
        actions={
          <>
            <Button variant="secondary" onClick={() => blocker.reset?.()}>Ficar</Button>
            <Button variant="danger" onClick={() => blocker.proceed?.()}>Sair sem salvar</Button>
          </>
        }
      >
        Suas alterações na escalação ainda não foram salvas e serão perdidas.
      </Modal>
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
  // Lesionados ficam indisponíveis para escalação (issues #2/#54) — o backend
  // recusa com INJURED_PLAYERS, aqui a opção nem aparece.
  const available = squad.filter(
    (p) => p.position === position && !otherSelected.includes(p.id) && !isInjured(p),
  )
  // A escalação efetiva (issue #87) não casa posição: o reserva que cobre um
  // lesionado pode ser de outra posição. Mantém o selecionado visível como
  // opção, marcado, em vez de o select mostrar o slot como vazio.
  const selected = squad.find((p) => p.id === selectedId)
  const offPosition = selected && !available.includes(selected) ? selected : null

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
        {offPosition && (
          <option value={offPosition.id}>
            {offPosition.name} ({offPosition.overall}) · {POSITION_ABBR[offPosition.position]} fora de posição
          </option>
        )}
        {available.map((p) => (
          <option key={p.id} value={p.id}>
            {p.name} ({p.overall}) · {p.stamina}%
          </option>
        ))}
      </select>
    </label>
  )
}

function SaveFeedback({ save, dirty }: { save: ReturnType<typeof useSaveLineup>; dirty: boolean }) {
  if (save.isError) return <span className="text-sm text-red-400">Erro: {errorMessage(save.error)}</span>
  if (save.isSuccess && !dirty) return <span className="text-sm text-emerald-400">Escalação salva ✓</span>
  return <span />
}
