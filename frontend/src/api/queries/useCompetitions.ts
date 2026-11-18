import { useQuery } from '@tanstack/react-query'
import { api, ApiError } from '../client'
import type {
  CompetitionDetail,
  CompetitionMatchday,
  CompetitionScorer,
  CompetitionSummary,
  Cup,
  Standings,
} from '../../domain/types'

// Navegação pelo mundo (issue #133). Família `['competitions', …]`: o
// `useRoundStream` invalida a família inteira a cada encaixe jogado — tabela,
// jogos, artilharia e chave mudam juntos.
export const competitionsKey = ['competitions'] as const
const competitionKey = (id: string) => ['competitions', id] as const

/** Todas as competições do mundo do save, na ordem do backend (Brasil primeiro). */
export function useCompetitions() {
  return useQuery({
    queryKey: competitionsKey,
    queryFn: () => api.get<CompetitionSummary[]>('/api/competitions'),
  })
}

/** Uma competição com formato e critérios de desempate. */
export function useCompetition(id: string) {
  return useQuery({
    queryKey: competitionKey(id),
    queryFn: () => api.get<CompetitionDetail>(`/api/competitions/${encodeURIComponent(id)}`),
  })
}

/**
 * GET que trata o 404 de "recurso ausente" (tabela de um mata-mata, chave de
 * uma liga, copa ainda não sorteada) como `null`: é estado normal do jogo, como
 * em `useCup`. A competição inexistente (`COMPETITION_NOT_FOUND`) continua
 * erro — a página já a recusou no detalhe.
 */
async function getOrNull<T>(path: string): Promise<T | null> {
  try {
    return await api.get<T>(path)
  } catch (err) {
    if (err instanceof ApiError && err.status === 404 && err.body?.code !== 'COMPETITION_NOT_FOUND') return null
    throw err
  }
}

/** Tabela corrente — já ordenada pelos desempates e com as faixas do backend. */
export function useCompetitionStandings(id: string) {
  return useQuery({
    queryKey: [...competitionKey(id), 'standings'],
    queryFn: () => getOrNull<Standings>(`/api/competitions/${encodeURIComponent(id)}/standings`),
  })
}

/** Encaixes da temporada corrente com os jogos da competição. */
export function useCompetitionMatches(id: string) {
  return useQuery({
    queryKey: [...competitionKey(id), 'matches'],
    queryFn: () => api.get<CompetitionMatchday[]>(`/api/competitions/${encodeURIComponent(id)}/matches`),
  })
}

/** Artilharia dos clubes da competição (gols da temporada — corte do MVP). */
export function useCompetitionScorers(id: string) {
  return useQuery({
    queryKey: [...competitionKey(id), 'scorers'],
    queryFn: () => api.get<CompetitionScorer[]>(`/api/competitions/${encodeURIComponent(id)}/scorers`),
  })
}

/** Chave de mata-mata; `null` quando a competição não tem (ou ainda não sorteou). */
export function useCompetitionBracket(id: string) {
  return useQuery({
    queryKey: [...competitionKey(id), 'bracket'],
    queryFn: () => getOrNull<Cup>(`/api/competitions/${encodeURIComponent(id)}/bracket`),
  })
}
