import type { Retirement, YouthPromotion } from '../../domain/types'

/**
 * Quem pendurou as chuteiras no clube do usuário (issue #55) e quem subiu da
 * base (issue #139). Desde a #139 as duas listas são independentes: a base
 * sobe de 2 a 4 garotos por ano, haja aposentadoria ou não. Sem o cartão a
 * troca só apareceria abrindo o elenco — o backend manda a notícia pronta no
 * `SeasonFinished`.
 */
export function RetirementsCard({
  retirements,
  youthPromotions,
}: {
  retirements: Retirement[]
  youthPromotions: YouthPromotion[]
}) {
  if (retirements.length === 0 && youthPromotions.length === 0) return null
  return (
    <div className="rounded-lg border border-slate-700 bg-slate-800/60 p-3">
      <h3 className="text-sm font-semibold text-slate-200">Renovação do elenco</h3>
      {retirements.length > 0 && (
        <ul aria-label="Aposentadorias" className="mt-2 space-y-1 text-sm text-slate-300">
          {retirements.map((r) => (
            <li key={r.retired.id}>
              <span className="text-slate-100">{r.retired.name}</span>
              <span className="text-slate-400"> ({r.retired.age}a, OVR {r.retired.overall}) se aposentou</span>
            </li>
          ))}
        </ul>
      )}
      {youthPromotions.length > 0 && (
        <ul aria-label="Garotos da base" className="mt-2 space-y-1 text-sm text-slate-300">
          {youthPromotions.map(({ player }) => (
            <li key={player.id} className="text-emerald-300">
              sobe da base: {player.name} ({player.age}a, OVR {player.overall})
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
