import { Link } from 'react-router-dom'
import { POSITION_ABBR, POSITION_LABEL } from '../domain/formations'
import { calledUpCount, windowRange } from '../domain/nationalTeams'
import type { ClubCallUps } from '../domain/types'
import { CountryFlag } from './CountryFlag'

/**
 * Aviso de convocação (issue #134): quem do clube vai para a seleção na data
 * FIFA corrente ou na próxima. Antes da janela é a PRÉVIA (o backend calcula
 * com os elencos de agora); durante, a convocação de fato. Sem convocados,
 * não desenha nada.
 *
 * Componente isolado de propósito: hoje mora na Dashboard; o orquestrador da
 * onda pode realocá-lo (cabeçalho, calendário) sem mexer na regra.
 */
export function CallUpNotice({ callUps }: { callUps: ClubCallUps }) {
  const { window, players } = callUps
  if (!window || players.length === 0 || window.status === 'PLAYED') return null

  const upcoming = window.status === 'UPCOMING'
  return (
    <section
      aria-labelledby="call-up-notice-title"
      className="rounded-lg border border-sky-700/60 bg-sky-950/40 p-4 space-y-2"
    >
      <h2 id="call-up-notice-title" className="text-base font-semibold text-sky-100">
        {calledUpCount(players.length)}
        {upcoming ? ' para a próxima data FIFA' : ' na data FIFA'}
      </h2>
      <p className="text-sm text-sky-200/80">
        {windowRange(window)} ·{' '}
        {upcoming
          ? 'previsão com os elencos de hoje — eles desfalcam o clube durante a janela.'
          : 'fora de todos os jogos do clube até o fim da janela.'}
      </p>
      <ul className="flex flex-wrap gap-2" aria-label="Convocados do clube">
        {players.map((p) => (
          <li key={p.playerId} className="rounded bg-slate-900/60 px-2 py-1 text-sm text-slate-200">
            <span className="mr-1"><CountryFlag country={p.country} /></span>
            {p.name}{' '}
            <abbr title={POSITION_LABEL[p.position]} className="text-xs text-slate-400 no-underline">
              {POSITION_ABBR[p.position]}
            </abbr>
          </li>
        ))}
      </ul>
      <Link to="/national-teams" className="inline-block text-sm text-sky-300 underline hover:text-sky-200">
        Ver seleções
      </Link>
    </section>
  )
}
