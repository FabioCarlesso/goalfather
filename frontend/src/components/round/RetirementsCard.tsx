import type { Retirement } from '../../domain/types'

/**
 * Quem pendurou as chuteiras no clube do usuário e qual garoto da base assumiu
 * (issue #55). Sem isso a troca só apareceria abrindo o elenco e comparando com
 * a memória — o backend manda a notícia pronta no `SeasonFinished`.
 */
export function RetirementsCard({ retirements }: { retirements: Retirement[] }) {
  if (retirements.length === 0) return null
  return (
    <div className="rounded-lg border border-slate-700 bg-slate-800/60 p-3">
      <h3 className="text-sm font-semibold text-slate-200">Fim de linha</h3>
      <ul className="mt-2 space-y-1 text-sm text-slate-300">
        {retirements.map((r) => (
          <li key={r.retired.id}>
            <span className="text-slate-100">{r.retired.name}</span>
            <span className="text-slate-400"> ({r.retired.age}a, OVR {r.retired.overall}) se aposentou · </span>
            <span className="text-emerald-300">
              sobe {r.promoted.name} ({r.promoted.age}a, OVR {r.promoted.overall})
            </span>
          </li>
        ))}
      </ul>
    </div>
  )
}
