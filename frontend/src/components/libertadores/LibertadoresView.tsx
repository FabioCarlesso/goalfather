import type { Libertadores } from '../../domain/types'
import { EmptyState } from '../ui/EmptyState'
import { GroupCard } from './GroupCard'
import { KnockoutBracket } from './KnockoutBracket'

interface LibertadoresViewProps {
  edition: Libertadores
  myClubId: number
}

/**
 * Grupos e chave de uma edição da Libertadores (issue #132), só por props. A
 * página própria (`/libertadores`) e a aba "Grupos e chave" da navegação pelo
 * mundo (`/competitions/:id`, issue #133) mostram a mesma coisa; cada uma põe
 * o seu cabeçalho em volta.
 */
export function LibertadoresView({ edition, myClubId }: LibertadoresViewProps) {
  return (
    <div className="space-y-6">
      <p className="text-sm text-slate-400">
        8 grupos de 4, ida e volta: os {edition.qualifiersPerGroup} primeiros vão às oitavas. Mata-mata em ida e volta e
        final em jogo único{edition.finalVenue ? <> no <strong className="text-slate-200">{edition.finalVenue.name}</strong></> : null}.
      </p>

      {edition.champion && (
        <div className="rounded-lg border border-amber-500/60 bg-gradient-to-r from-amber-900/30 to-amber-700/20 p-4 text-center">
          <div className="text-3xl" aria-hidden="true">🏆</div>
          <div className="text-lg font-bold text-amber-100">{edition.champion.name} é campeão da Libertadores!</div>
        </div>
      )}

      <div className="space-y-2">
        <h2 className="text-lg font-semibold text-slate-100">Fase de grupos</h2>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
          {edition.groups.map((group) => <GroupCard key={group.name} group={group} myClubId={myClubId} />)}
        </div>
      </div>

      <div className="space-y-2">
        <h2 className="text-lg font-semibold text-slate-100">Mata-mata</h2>
        {edition.knockout
          ? <KnockoutBracket cup={edition.knockout} myClubId={myClubId} finalVenue={edition.finalVenue?.name} label="Chave da Libertadores" />
          : <EmptyState message="O mata-mata é sorteado quando a fase de grupos termina: 1º contra 2º de outro grupo." />}
      </div>
    </div>
  )
}
