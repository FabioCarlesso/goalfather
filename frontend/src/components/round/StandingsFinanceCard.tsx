import { formatMoney, formatSeats } from '../../domain/formatters'
import { KNOCKOUT_PHASE_LABEL } from '../../domain/cup'
import { ordinal } from '../../domain/board'
import type { RoundFinance, RoundStage, Standings } from '../../domain/types'

/**
 * Posição do técnico na divisão e o caixa da rodada (antigo `FinalBanner`,
 * issues #4, #59 e #61). A tabela chega pronta no `RoundFinished`: o card só
 * localiza a linha do clube e soma o extrato para exibir o saldo.
 */
export function StandingsFinanceCard({
  table,
  myClubId,
  stage,
  finance,
}: { table: Standings; myClubId: number; stage: RoundStage; finance: RoundFinance | null }) {
  // Data de copa não mexe na tabela (issue #61): o card fala da fase e do
  // caixa, e o top-3 só aparece depois de uma rodada de liga.
  const top3 = stage.type === 'League' ? table.rows.slice(0, 3) : []
  const title = stage.type === 'League'
    ? `Pontuações atualizadas após a rodada ${stage.matchday}`
    : `Copa — ${KNOCKOUT_PHASE_LABEL[stage.phase]} encerrada`
  const mine = table.rows.find((r) => r.clubId === myClubId)
  const income = finance ? finance.ticketRevenue + finance.cupPrize : 0

  return (
    <div className="rounded-lg border border-emerald-700/50 bg-emerald-900/20 p-3">
      <h3 className="text-sm font-semibold text-emerald-200">{title}</h3>
      {mine && (
        <p className="mt-1 text-sm text-slate-100">
          Você está em <span className="font-semibold">{ordinal(mine.position)}</span> na Divisão{' '}
          {table.division} · <span className="font-mono">{mine.points} pts</span>
        </p>
      )}
      {finance && (
        <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-sm font-mono">
          <span className="text-emerald-300">+{formatMoney(finance.ticketRevenue)} bilheteria</span>
          {finance.cupPrize > 0 && (
            <span className="text-amber-300">+{formatMoney(finance.cupPrize)} prêmio da copa</span>
          )}
          {/* Detalhe da bilheteria (issue #59): sem público × preço, o total
              não diz se rendeu por estádio cheio ou por ingresso caro. */}
          {finance.attendance > 0 && (
            <span className="text-slate-400">
              ({formatSeats(finance.attendance)} pagantes × {formatMoney(finance.ticketPrice)})
            </span>
          )}
          <span className="text-red-300">−{formatMoney(finance.salariesPaid)} salários</span>
          <span className="text-slate-200">
            saldo {income - finance.salariesPaid >= 0 ? '+' : '−'}
            {formatMoney(Math.abs(income - finance.salariesPaid))}
          </span>
          {finance.deficit > 0 && (
            <span className="font-semibold text-red-400">
              ⚠ no vermelho — {formatMoney(finance.deficit)} de salários sem cobertura
            </span>
          )}
        </div>
      )}
      {top3.length > 0 && (
        <ul className="mt-2 text-sm space-y-0.5" aria-label="Três primeiros da divisão">
          {top3.map((r) => (
            <li key={r.clubId} className={`flex justify-between ${r.clubId === myClubId ? 'text-emerald-200 font-semibold' : 'text-slate-300'}`}>
              <span>{r.position}. {r.clubName}</span>
              <span className="font-mono">{r.points} pts · SG {r.goalDifference >= 0 ? '+' : ''}{r.goalDifference}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
