import type { MatchStats } from '../domain/types'

interface Props {
  stats: MatchStats
  homeName: string
  awayName: string
}

/**
 * Mini-sumário do fim de jogo (issue #57): finalizações, chutes no gol,
 * defesas e cartões dos dois lados.
 *
 * Componente burro de propósito — recebe o `MatchStats` que veio no evento
 * `FullTime` e só desenha. Nada é recalculado aqui: o backend deriva as
 * estatísticas dos próprios eventos, e duplicar essa contagem no cliente
 * é exatamente o tipo de regra que o CLAUDE.md manda não espalhar.
 */
export function MatchStatsSummary({ stats, homeName, awayName }: Props) {
  const rows: Array<[string, number, number]> = [
    ['Finalizações', stats.home.shots, stats.away.shots],
    ['No gol', stats.home.shotsOnTarget, stats.away.shotsOnTarget],
    ['Defesas', stats.home.saves, stats.away.saves],
    ['Amarelos', stats.home.yellowCards, stats.away.yellowCards],
    ['Vermelhos', stats.home.redCards, stats.away.redCards],
  ]

  return (
    <div className="rounded-lg border border-slate-800 bg-slate-900/40 p-4">
      <h2 className="mb-3 text-xs uppercase tracking-wide text-slate-500">Estatísticas da partida</h2>
      <div className="grid grid-cols-[1fr_auto_1fr] items-center gap-x-4 text-sm">
        <div className="truncate text-right font-medium text-slate-300">{homeName}</div>
        <div />
        <div className="truncate font-medium text-slate-300">{awayName}</div>

        {rows.map(([label, home, away]) => (
          <StatRow key={label} label={label} home={home} away={away} />
        ))}
      </div>
    </div>
  )
}

function StatRow({ label, home, away }: { label: string; home: number; away: number }) {
  // Barra proporcional: dá leitura visual de quem dominou sem precisar
  // comparar números. Placar 0–0 divide ao meio em vez de dividir por zero.
  const total = home + away
  const homePct = total === 0 ? 50 : (home / total) * 100

  return (
    <>
      <div className="flex items-center justify-end gap-2 py-1">
        <div className="h-1.5 w-full max-w-24 overflow-hidden rounded-full bg-slate-800">
          <div className="ml-auto h-full bg-emerald-500/70" style={{ width: `${homePct}%` }} />
        </div>
        <span className="w-6 text-right font-mono tabular-nums text-slate-100">{home}</span>
      </div>
      <div className="whitespace-nowrap text-center text-xs text-slate-500">{label}</div>
      <div className="flex items-center gap-2 py-1">
        <span className="w-6 font-mono tabular-nums text-slate-100">{away}</span>
        <div className="h-1.5 w-full max-w-24 overflow-hidden rounded-full bg-slate-800">
          <div className="h-full bg-sky-500/70" style={{ width: `${100 - homePct}%` }} />
        </div>
      </div>
    </>
  )
}
