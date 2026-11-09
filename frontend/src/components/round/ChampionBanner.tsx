import type { CupClub, StandingRow } from '../../domain/types'

/** Campeão da temporada que acabou (issue #11) e, se houve, o da copa (issue #61). */
export function ChampionBanner({
  season,
  champion,
  cupChampion,
}: { season: number; champion: StandingRow; cupChampion?: CupClub }) {
  return (
    <div className="rounded-lg border border-amber-500/60 bg-gradient-to-r from-amber-900/30 to-amber-700/20 p-3 text-center">
      <div className="text-xs uppercase tracking-widest text-amber-300/80">
        <span aria-hidden="true">🏆 </span>Fim da temporada {season}
      </div>
      <h3 className="mt-1 text-lg font-bold text-amber-100">{champion.clubName} é o campeão!</h3>
      <div className="mt-1 text-sm font-mono text-amber-200/90">
        {champion.points} pts · {champion.wins}V {champion.draws}E {champion.losses}D · SG{' '}
        {champion.goalDifference >= 0 ? '+' : ''}{champion.goalDifference}
      </div>
      {cupChampion && (
        <div className="mt-1 text-sm text-amber-200">🥇 Copa: {cupChampion.name}</div>
      )}
      <div className="mt-1 text-xs text-slate-400">Uma nova temporada começou — boa sorte!</div>
    </div>
  )
}
