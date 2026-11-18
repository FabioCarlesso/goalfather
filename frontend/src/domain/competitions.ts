// Rótulos e composição das telas do mundo (issue #133).
//
// SÓ apresentação: a ordem da tabela, as faixas, quem passou na chave e em que
// semana cai cada jogo vêm prontos do backend. Aqui se decide o que a TELA
// mostra para cada formato e como se escreve cada critério.

import type {
  CompetitionFormatType,
  CompetitionMatchday,
  CompetitionSummary,
  Confederation,
  Country,
  Tiebreaker,
} from './types'

export const COMPETITION_FORMAT_LABEL: Record<CompetitionFormatType, string> = {
  ROUND_ROBIN: 'Pontos corridos',
  KNOCKOUT: 'Mata-mata',
  GROUPS_THEN_KNOCKOUT: 'Grupos e mata-mata',
  SWISS_LEAGUE: 'Fase de liga e mata-mata',
}

/** `Record<Tiebreaker, …>`: um critério novo no contrato quebra a compilação até ganhar rótulo. */
export const TIEBREAKER_LABEL: Record<Tiebreaker, string> = {
  POINTS: 'Pontos',
  WINS: 'Vitórias',
  GOAL_DIFFERENCE: 'Saldo de gols',
  GOALS_FOR: 'Gols marcados',
  HEAD_TO_HEAD: 'Confronto direto',
  HEAD_TO_HEAD_GOAL_DIFFERENCE: 'Saldo no confronto direto',
  FEWER_RED_CARDS: 'Menos cartões vermelhos',
  FEWER_YELLOW_CARDS: 'Menos cartões amarelos',
  DRAWING_OF_LOTS: 'Sorteio',
}

/**
 * As visões de uma competição. `bracket` é a chave de mata-mata; `groups` é a
 * visão de grupos + chave (Libertadores, #132).
 */
export type CompetitionView = 'table' | 'groups' | 'bracket' | 'matches' | 'scorers'

export interface CompetitionTab {
  view: CompetitionView
  /** Segmento da URL: `/competitions/:id/:tab`. */
  slug: string
  label: string
}

const TAB: Record<CompetitionView, CompetitionTab> = {
  table: { view: 'table', slug: 'tabela', label: 'Tabela' },
  groups: { view: 'groups', slug: 'grupos', label: 'Grupos e chave' },
  bracket: { view: 'bracket', slug: 'chave', label: 'Chave' },
  matches: { view: 'matches', slug: 'jogos', label: 'Jogos' },
  scorers: { view: 'scorers', slug: 'artilharia', label: 'Artilharia' },
}

/**
 * As abas de cada formato, a primeira é a padrão. `switch` exaustivo sobre o
 * enum do contrato: um formato novo no YAML vira erro de compilação aqui até
 * ganhar as suas abas. O mata-mata abre na Chave; pontos corridos, na Tabela.
 */
export function competitionTabs(format: CompetitionFormatType): CompetitionTab[] {
  switch (format) {
    case 'ROUND_ROBIN':
      return [TAB.table, TAB.matches, TAB.scorers]
    case 'KNOCKOUT':
      return [TAB.bracket, TAB.matches, TAB.scorers]
    case 'GROUPS_THEN_KNOCKOUT':
      return [TAB.groups, TAB.matches, TAB.scorers]
    case 'SWISS_LEAGUE':
      return [TAB.table, TAB.bracket, TAB.matches, TAB.scorers]
  }
}

/** A aba do segmento da URL, ou a padrão do formato. */
export function tabOf(format: CompetitionFormatType, slug: string | undefined): CompetitionTab {
  const tabs = competitionTabs(format)
  return tabs.find((t) => t.slug === slug) ?? tabs[0]!
}

/** Um grupo da tela do mundo: um país, ou as continentais de uma confederação. */
export interface CompetitionGroup {
  key: string
  country?: Country
  confederation: Confederation
  competitions: CompetitionSummary[]
}

/**
 * Competições agrupadas por país (as continentais, sem país, por
 * confederação), na ordem em que o backend as mandou.
 */
export function groupByCountry(competitions: CompetitionSummary[]): CompetitionGroup[] {
  const groups: CompetitionGroup[] = []
  for (const competition of competitions) {
    const key = competition.country?.code ?? competition.confederation
    const group = groups.find((g) => g.key === key)
    if (group) group.competitions.push(competition)
    else groups.push({ key, country: competition.country, confederation: competition.confederation, competitions: [competition] })
  }
  return groups
}

/**
 * O encaixe que a aba Jogos abre: o primeiro com jogo ainda não encerrado (o
 * corrente ou o próximo da competição); com a temporada toda jogada, o último.
 */
export function openMatchdayIndex(days: CompetitionMatchday[]): number {
  const open = days.findIndex((d) => d.matches.length === 0 || d.matches.some((m) => m.status !== 'Finished'))
  return open >= 0 ? open : Math.max(0, days.length - 1)
}
