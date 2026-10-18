import { useMemo } from 'react'
import { useClub } from './useClub'

/**
 * Lookup id → nome dos jogadores dos DOIS clubes de uma partida, para o feed
 * mostrar nomes em vez de "#N" (issues #7 e #84). Busca mandante e visitante
 * via `GET /api/clubs/{id}` (o do usuário já costuma estar no cache).
 *
 * Enquanto um elenco carrega, ou se a busca falhar, os ids dele ficam fora do
 * mapa e o `MatchEventFeed` cai no fallback "#N".
 */
export function useMatchPlayerLookup(
  homeClubId: number | null | undefined,
  awayClubId: number | null | undefined,
): Map<number, string> {
  // Mesmo id dos dois lados não gera requisição duplicada: a queryKey é a mesma.
  const { data: home } = useClub(homeClubId)
  const { data: away } = useClub(awayClubId)

  return useMemo(
    () =>
      new Map<number, string>(
        [...(home?.squad ?? []), ...(away?.squad ?? [])].map((p) => [p.id, p.name]),
      ),
    [home, away],
  )
}
