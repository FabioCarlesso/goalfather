import { useState } from 'react'
import { useClub } from '../api/queries/useClub'
import { useSellPlayer } from '../api/queries/useSellPlayer'
import { useExpandStadium, COST_PER_SEAT_CENTS } from '../api/queries/useExpandStadium'
import { useTreatSquad, MEDICAL_COST_CENTS } from '../api/queries/useTreatSquad'
import { useSetTrainingFocus } from '../api/queries/useSetTrainingFocus'
import {
  useSetTicketPrice,
  MIN_TICKET_PRICE_CENTS,
  MAX_TICKET_PRICE_CENTS,
} from '../api/queries/useSetTicketPrice'
import { ApiError } from '../api/client'
import { errorMessage } from '../api/errorMessages'
import { useMyClubId } from '../auth/useMyClubId'
import { formatMoney, formatSeats } from '../domain/formatters'
import { TRAINING_FOCUSES, TRAINING_FOCUS_HINT, TRAINING_FOCUS_LABEL } from '../domain/training'
import { isInjured, injuryRoundsOut, staminaLevel } from '../domain/players'
import type { Availability, Club, TransferResult } from '../domain/types'

export function DashboardPage() {
  const myClubId = useMyClubId()
  const { data: club, isLoading, error } = useClub(myClubId)
  const sell = useSellPlayer()
  const [lastResult, setLastResult] = useState<TransferResult | null>(null)

  if (isLoading) return <p className="text-slate-400">Carregando clube…</p>
  if (error)     return <p className="text-red-400">{errorMessage(error)}</p>
  if (!club)     return null

  const onSell = (playerId: number, name: string) => {
    if (!confirm(`Vender ${name}?`)) return
    sell.mutate(
      { clubId: myClubId, playerId },
      { onSuccess: (result) => setLastResult(result) },
    )
  }

  return (
    <section className="space-y-6">
      <header>
        <h1 className="text-3xl font-bold text-slate-100">{club.name}</h1>
        <p className="text-sm text-slate-400">ID #{club.id}</p>
      </header>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <Stat label="Caixa"              value={formatMoney(club.cash)} />
        <Stat label="Capacidade estádio" value={formatSeats(club.stadiumCapacity)} />
        <Stat label="Ingresso"           value={formatMoney(club.ticketPriceCents)} />
        <Stat label="Elenco"             value={`${club.squad.length} jogadores`} />
      </div>

      {lastResult && (
        <SaleFeedback result={lastResult} onDismiss={() => setLastResult(null)} />
      )}

      <TrainingPanel club={club} />

      <TicketPricePanel club={club} />

      <StadiumExpandPanel club={club} />

      <MedicalDepartmentPanel club={club} />

      <div>
        <h2 className="text-xl font-semibold text-slate-100 mb-3">Elenco</h2>
        <ul className="divide-y divide-slate-800 rounded-lg border border-slate-800">
          {club.squad.map((p) => (
            <li key={p.id} className="flex items-center justify-between px-4 py-2">
              <div className="min-w-0">
                <span className="font-medium text-slate-100">{p.name}</span>
                <span className="ml-2 text-xs text-slate-500">{p.position} · {p.age}a</span>
                <InjuryBadge availability={p.availability} />
              </div>
              <div className="flex items-center gap-3">
                <StaminaBar stamina={p.stamina} />
                <PlayerStats player={p} />
                {p.star && <span className="text-yellow-400" title="Estrela">★</span>}
                <span className="text-slate-300 font-mono w-8 text-right">{p.overall}</span>
                <button
                  onClick={() => onSell(p.id, p.name)}
                  disabled={sell.isPending}
                  className="rounded-md border border-slate-700 hover:border-red-600 hover:text-red-400 disabled:opacity-50 px-2 py-1 text-xs text-slate-400 transition-colors"
                >
                  {sell.isPending && sell.variables?.playerId === p.id ? '…' : 'Vender'}
                </button>
              </div>
            </li>
          ))}
        </ul>
      </div>
    </section>
  )
}

/**
 * Foco de treino da semana (issue #58). O card só escolhe: o efeito acontece
 * na virada da rodada e é reportado na página da rodada.
 */
function TrainingPanel({ club }: { club: Club }) {
  const setFocus = useSetTrainingFocus(club.id)
  // Otimismo local só para o feedback do clique: a fonte de verdade continua
  // sendo `club.trainingFocus`, que o mutation atualiza no cache.
  const selected = setFocus.isPending ? setFocus.variables : club.trainingFocus

  return (
    <div className="rounded-lg border border-slate-800 bg-slate-900/60 p-4 space-y-3">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold text-slate-100">Treino da semana</h2>
        <span className="text-xs text-slate-500">aplicado na próxima rodada</span>
      </div>
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
        {TRAINING_FOCUSES.map((focus) => (
          <button
            key={focus}
            onClick={() => setFocus.mutate(focus)}
            disabled={setFocus.isPending}
            aria-pressed={selected === focus}
            className={`rounded-md border px-3 py-2 text-sm font-medium transition-colors disabled:opacity-60 ${
              selected === focus
                ? 'border-emerald-500 bg-emerald-900/30 text-emerald-200'
                : 'border-slate-700 text-slate-300 hover:border-emerald-600/60'
            }`}
          >
            {TRAINING_FOCUS_LABEL[focus]}
          </button>
        ))}
      </div>
      <p className="text-sm text-slate-400">{TRAINING_FOCUS_HINT[selected]}</p>
      {setFocus.isError && (
        <p className="text-sm text-red-400">{errorMessage(setFocus.error)}</p>
      )}
    </div>
  )
}

/**
 * Preço do ingresso (issue #59). O painel só ESCOLHE o preço: quanta gente
 * aparece e quanto isso rende é curva de demanda do backend, reportada no
 * extrato da rodada. Fica ao lado da ampliação de propósito — as duas decisões
 * mexem na mesma conta (mais assentos × quanto cada assento rende).
 */
function TicketPricePanel({ club }: { club: Club }) {
  const setPrice = useSetTicketPrice(club.id)
  const [reais, setReais] = useState(club.ticketPriceCents / 100)

  const cents = Math.round(reais * 100)
  const valid = Number.isFinite(reais) &&
    cents >= MIN_TICKET_PRICE_CENTS &&
    cents <= MAX_TICKET_PRICE_CENTS
  const changed = cents !== club.ticketPriceCents

  return (
    <div className="rounded-lg border border-slate-800 bg-slate-900/60 p-4 space-y-3">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold text-slate-100">Preço do ingresso</h2>
        <span className="text-xs text-slate-500">
          em vigor: {formatMoney(club.ticketPriceCents)}
        </span>
      </div>
      <p className="text-sm text-slate-400">
        Ingresso caro rende mais por torcedor e esvazia o estádio; barato lota
        mas rende pouco. Time melhor suporta preço maior — vale entre{' '}
        {formatMoney(MIN_TICKET_PRICE_CENTS)} e {formatMoney(MAX_TICKET_PRICE_CENTS)}.
      </p>
      <div className="flex flex-wrap items-end gap-3">
        <label className="text-sm text-slate-400">
          Preço (R$)
          <input
            type="number"
            min={MIN_TICKET_PRICE_CENTS / 100}
            max={MAX_TICKET_PRICE_CENTS / 100}
            step={5}
            value={reais}
            onChange={(e) => setReais(Number(e.target.value))}
            className="mt-1 block w-32 rounded-md border border-slate-700 bg-slate-900 px-2 py-1 text-sm text-slate-100"
          />
        </label>
        <button
          onClick={() => valid && setPrice.mutate(cents)}
          disabled={!valid || !changed || setPrice.isPending}
          className="rounded-md bg-emerald-600 hover:bg-emerald-500 disabled:bg-slate-700 disabled:cursor-not-allowed text-white px-4 py-2 text-sm font-medium transition-colors"
        >
          {setPrice.isPending ? 'Salvando…' : 'Definir preço'}
        </button>
      </div>
      {!valid && (
        <p className="text-xs text-amber-300">
          Informe um valor entre {formatMoney(MIN_TICKET_PRICE_CENTS)} e{' '}
          {formatMoney(MAX_TICKET_PRICE_CENTS)}.
        </p>
      )}
      {setPrice.isError && (
        <p className="text-sm text-red-400">{errorMessage(setPrice.error)}</p>
      )}
      {setPrice.isSuccess && !changed && (
        <p className="text-sm text-emerald-400">Preço atualizado ✓</p>
      )}
    </div>
  )
}

/** Painel de ampliação de estádio (issue #5). */
function StadiumExpandPanel({ club }: { club: Club }) {
  const expand = useExpandStadium(club.id)
  const [seats, setSeats] = useState(1000)

  const valid = Number.isInteger(seats) && seats >= 1000
  const cost = seats * COST_PER_SEAT_CENTS

  return (
    <div className="rounded-lg border border-slate-800 bg-slate-900/60 p-4 space-y-3">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold text-slate-100">Ampliar estádio</h2>
        <span className="text-xs text-slate-500">{formatMoney(COST_PER_SEAT_CENTS)} / assento</span>
      </div>
      <div className="flex flex-wrap items-end gap-3">
        <label className="text-sm text-slate-400">
          Novos assentos
          <input
            type="number"
            min={1000}
            step={1000}
            value={seats}
            onChange={(e) => setSeats(Math.floor(Number(e.target.value)))}
            className="mt-1 block w-32 rounded-md border border-slate-700 bg-slate-900 px-2 py-1 text-sm text-slate-100"
          />
        </label>
        <div className="text-sm text-slate-400">
          Custo total: <span className="font-mono text-slate-200">{formatMoney(cost)}</span>
        </div>
        <button
          onClick={() => valid && expand.mutate({ additionalSeats: seats })}
          disabled={!valid || expand.isPending}
          className="rounded-md bg-emerald-600 hover:bg-emerald-500 disabled:bg-slate-700 disabled:cursor-not-allowed text-white px-4 py-2 text-sm font-medium transition-colors"
        >
          {expand.isPending ? 'Ampliando…' : 'Ampliar'}
        </button>
      </div>
      {!valid && <p className="text-xs text-amber-300">Informe um número inteiro de pelo menos 1000 assentos.</p>}
      {expand.isError && (
        <p className="text-sm text-red-400">
          {expand.error instanceof ApiError && expand.error.status === 402
            ? `Caixa insuficiente — custo ${formatMoney(cost)}, disponível ${formatMoney(club.cash)}.`
            : `Erro ao ampliar: ${String(expand.error)}`}
        </p>
      )}
      {expand.isSuccess && <p className="text-sm text-emerald-400">Capacidade ampliada ✓</p>}
    </div>
  )
}

/**
 * Departamento médico (issue #54): taxa fixa que devolve stamina e encurta
 * lesões. Custo e efeitos vêm do backend — o painel só dispara e reporta.
 */
function MedicalDepartmentPanel({ club }: { club: Club }) {
  const treat = useTreatSquad(club.id)

  const injuredCount = club.squad.filter(isInjured).length
  const tiredCount = club.squad.filter((p) => staminaLevel(p.stamina) !== 'fresh').length
  const affordable = club.cash >= MEDICAL_COST_CENTS

  return (
    <div className="rounded-lg border border-slate-800 bg-slate-900/60 p-4 space-y-3">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold text-slate-100">Departamento médico</h2>
        <span className="text-xs text-slate-500">{formatMoney(MEDICAL_COST_CENTS)} / sessão</span>
      </div>
      <p className="text-sm text-slate-400">
        Recupera forma física do elenco e encurta as lesões em uma rodada.
        {' '}
        <span className="text-slate-300">
          {injuredCount} lesionado{injuredCount === 1 ? '' : 's'} · {tiredCount} desgastado
          {tiredCount === 1 ? '' : 's'}
        </span>
      </p>
      <button
        onClick={() => treat.mutate()}
        disabled={!affordable || treat.isPending}
        className="rounded-md bg-sky-600 hover:bg-sky-500 disabled:bg-slate-700 disabled:cursor-not-allowed text-white px-4 py-2 text-sm font-medium transition-colors"
      >
        {treat.isPending ? 'Tratando…' : 'Tratar elenco'}
      </button>
      {!affordable && (
        <p className="text-xs text-amber-300">
          Caixa insuficiente — necessário {formatMoney(MEDICAL_COST_CENTS)}.
        </p>
      )}
      {treat.isError && (
        <p className="text-sm text-red-400">
          {treat.error instanceof ApiError && treat.error.status === 402
            ? `Caixa insuficiente — custo ${formatMoney(MEDICAL_COST_CENTS)}, disponível ${formatMoney(club.cash)}.`
            : `Erro no tratamento: ${String(treat.error)}`}
        </p>
      )}
      {treat.isSuccess && <p className="text-sm text-emerald-400">Elenco tratado ✓</p>}
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

/** Barra de forma física (issue #54). A faixa de cor vem de `staminaLevel`. */
function StaminaBar({ stamina }: { stamina: number }) {
  const level = staminaLevel(stamina)
  const color = {
    fresh:     'bg-emerald-500',
    tired:     'bg-amber-500',
    exhausted: 'bg-red-500',
  }[level]

  return (
    <span className="flex items-center gap-1.5" title={`Forma física: ${stamina}%`}>
      <span className="h-1.5 w-12 overflow-hidden rounded-full bg-slate-800">
        <span className={`block h-full ${color}`} style={{ width: `${stamina}%` }} />
      </span>
      <span className="w-8 text-right font-mono text-[10px] text-slate-500">{stamina}%</span>
    </span>
  )
}

/** Estatísticas acumuladas do jogador (gols/cartões) ao lado da linha do elenco. */
function PlayerStats({ player }: { player: import('../domain/types').Player }) {
  return (
    <span className="flex items-center gap-2 text-xs font-mono text-slate-400">
      {player.goals > 0 && <span className="text-emerald-400" title="Gols">⚽ {player.goals}</span>}
      {player.yellowCards > 0 && <span className="text-yellow-300" title="Cartões amarelos">🟨 {player.yellowCards}</span>}
      {player.redCards > 0 && <span className="text-red-400" title="Cartões vermelhos">🟥 {player.redCards}</span>}
    </span>
  )
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border border-slate-800 bg-slate-900/60 p-4">
      <div className="text-xs uppercase tracking-wide text-slate-500">{label}</div>
      <div className="text-lg font-semibold text-slate-100 mt-1">{value}</div>
    </div>
  )
}

function SaleFeedback({
  result,
  onDismiss,
}: { result: TransferResult; onDismiss: () => void }) {
  let color: string
  let title: string
  let body: string
  switch (result.type) {
    case 'Success':
      color = 'border-emerald-700/50 bg-emerald-900/20 text-emerald-200'
      title = 'Venda concluída'
      body  = `${result.player.name} saiu do clube. Caixa: ${formatMoney(result.club.cash)}.`
      break
    case 'InsufficientFunds':
      color = 'border-red-700/50 bg-red-900/20 text-red-200'
      title = 'Caixa insuficiente'
      body  = `Necessário ${formatMoney(result.required)}; disponível ${formatMoney(result.available)}.`
      break
    case 'SquadFull':
      color = 'border-amber-700/50 bg-amber-900/20 text-amber-200'
      title = 'Elenco cheio'
      body  = `Limite de ${result.maxSize} jogadores atingido.`
      break
    case 'PlayerNotAvailable':
      color = 'border-slate-700/50 bg-slate-800/40 text-slate-200'
      title = 'Jogador indisponível'
      body  = `Jogador #${result.playerId} não está no elenco.`
      break
  }

  return (
    <div className={`rounded-lg border ${color} px-4 py-3 flex items-start justify-between`}>
      <div>
        <div className="font-semibold">{title}</div>
        <div className="text-sm opacity-90">{body}</div>
      </div>
      <button onClick={onDismiss} className="text-slate-400 hover:text-slate-200 text-sm">×</button>
    </div>
  )
}
