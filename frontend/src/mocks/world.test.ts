// Testes do mundo do mock (issues #47 e #131) — garantem que a engine de mock
// tem a forma do pack E2E do backend (ligas, clubes, temporadas) e espelha as
// regras de QualificationRules/RoundFixturesGenerator até o backend assumir.

import { describe, expect, it } from 'vitest'
import {
  COUNTRIES,
  applyPromotionRelegation,
  applyRoundToStandings,
  brazilianClubIds,
  clubOwners,
  initialStandings,
  PROMOTION_RELEGATION_SPOTS,
  SEASON_ROUNDS,
  state,
} from './seed'
import { WORLD_CLUBS, seasonLabelOf, leagueById } from './world'
import type { Standings } from '../domain/types'

const table = (competitionId: string, clubIds: number[]): Standings => {
  const league = leagueById(competitionId)
  return {
    season: 2026,
    seasonLabel: seasonLabelOf(league, 2026),
    competitionId,
    competitionName: league.name,
    country: league.country,
    division: league.tier,
    round: 5,
    promotionSpots: 0,
    relegationSpots: 0,
    rows: clubIds.map((clubId, i) => ({
      position: i + 1,
      clubId,
      clubName: `Club ${clubId}`,
      played: 5, wins: 0, draws: 0, losses: 0,
      goalsFor: 0, goalsAgainst: 0, goalDifference: 0, points: 0, zone: 'NONE',
    })),
  }
}

const namesOf = (competitionId: string): string[] =>
  initialStandings.find((t) => t.competitionId === competitionId)!.rows.map((r) => r.clubName)

describe('mundo do mock (issue #131)', () => {
  it('tem as ligas do pack E2E, na ordem do contrato: Brasil primeiro, depois a divisão', () => {
    expect(initialStandings.map((t) => [t.competitionId, t.division, t.country.code])).toEqual([
      ['bra-serie-a', 1, 'BRA'],
      ['bra-serie-b', 2, 'BRA'],
      ['eng-premier-league', 1, 'ENG'],
    ])
    expect(initialStandings.map((t) => t.competitionName)).toEqual([
      'Campeonato Brasileiro Série A',
      'Campeonato Brasileiro Série B',
      'Premier League',
    ])
  })

  it('usa os clubes de maior reputação de cada liga do pack, com os nomes reais', () => {
    expect(namesOf('bra-serie-a')).toEqual(['Flamengo', 'Palmeiras', 'Corinthians', 'Atlético Mineiro', 'Botafogo', 'São Paulo'])
    expect(namesOf('bra-serie-b')).toEqual(['Fortaleza', 'Sport', 'Ceará', 'Goiás', 'América Mineiro', 'Atlético Goianiense'])
    expect(namesOf('eng-premier-league')).toEqual(['Liverpool', 'Manchester City', 'Arsenal', 'Chelsea'])
  })

  it('cada liga tem a sua temporada: 2026 no Brasil, 2026/27 na Europa', () => {
    expect(initialStandings.map((t) => t.seasonLabel)).toEqual(['2026', '2026', '2026/27'])
    expect(initialStandings.every((t) => t.season === 2026)).toBe(true)
  })

  it('nenhum clube começa com dono', () => {
    expect(Object.keys(clubOwners).map(Number).sort((a, b) => a - b)).toEqual(WORLD_CLUBS.map((c) => c.id))
    expect(Object.values(clubOwners).every((owner) => owner == null)).toBe(true)
  })

  it('todo clube tem elenco completo, com nomes únicos e a nacionalidade do país do clube', () => {
    const squads = WORLD_CLUBS.map((c) => state.clubs[c.id]!)
    expect(squads.every((club) => club.squad.length === 11)).toBe(true)
    const names = squads.flatMap((club) => club.squad.map((p) => p.name))
    expect(new Set(names).size).toBe(names.length)
    const liverpool = squads.find((c) => c.name === 'Liverpool')!
    expect(liverpool.country).toEqual(COUNTRIES.ENG)
    expect(liverpool.squad.every((p) => p.nationality.code === 'ENG')).toBe(true)
  })

  it('o elenco sintético tem média exata na força do clube', () => {
    for (const world of WORLD_CLUBS) {
      const squad = state.clubs[world.id]!.squad
      expect(squad.reduce((s, p) => s + p.overall, 0) / squad.length).toBe(world.strength)
    }
  })

  it('a copa nacional é só dos clubes do Brasil', () => {
    expect(brazilianClubIds()).toHaveLength(12)
    const drawn = state.cup!.stages[0]!.ties.flatMap((t) => (t.type === 'Bye' ? [t.club.id] : [t.home.id, t.away.id]))
    expect(drawn.sort((a, b) => a - b)).toEqual(brazilianClubIds().sort((a, b) => a - b))
  })
})

describe('ligas e acesso/rebaixamento (issues #47 e #131)', () => {
  it('Série A rebaixa e Série B dá acesso; a Premier League do mock não tem para onde ir', () => {
    const [serieA, serieB, premier] = initialStandings
    expect([serieA!.promotionSpots, serieA!.relegationSpots]).toEqual([0, PROMOTION_RELEGATION_SPOTS])
    expect([serieB!.promotionSpots, serieB!.relegationSpots]).toEqual([PROMOTION_RELEGATION_SPOTS, 0])
    expect([premier!.promotionSpots, premier!.relegationSpots]).toEqual([0, 0])
  })

  it('rodada corrente tem as partidas de cada liga, sem colisão de matchId', () => {
    const matches = state.currentRound.matches
    // 3 + 3 + 2 partidas: Série A, Série B e Premier League.
    expect(matches).toHaveLength(8)
    expect(new Set(matches.map((m) => m.matchId)).size).toBe(8)
    const byLeague = (id: string) => matches.filter((m) => m.competitionId === id)
    expect(byLeague('bra-serie-a')).toHaveLength(3)
    expect(byLeague('bra-serie-b')).toHaveLength(3)
    expect(byLeague('eng-premier-league')).toHaveLength(2)
    // Ligas nunca se cruzam dentro de uma partida.
    const premierIds = new Set(initialStandings[2]!.rows.map((r) => r.clubId))
    for (const m of byLeague('eng-premier-league')) {
      expect(premierIds.has(m.homeClubId) && premierIds.has(m.awayClubId)).toBe(true)
      expect(m.competitionName).toBe('Premier League')
      expect(m.division).toBe(1)
    }
  })

  it('temporada dura o turno da maior liga (6 clubes → 5 rodadas)', () => {
    expect(SEASON_ROUNDS).toBe(5)
  })

  it('promoção/rebaixamento troca os últimos da Série A com os primeiros da Série B, e só no Brasil', () => {
    const next = applyPromotionRelegation([
      table('bra-serie-a', [1, 2, 3, 4, 5, 6]),
      table('bra-serie-b', [7, 8, 9, 10, 11, 12]),
      table('eng-premier-league', [13, 14, 15, 16]),
    ])

    expect(next[5]).toBe('bra-serie-b')
    expect(next[6]).toBe('bra-serie-b')
    expect(next[7]).toBe('bra-serie-a')
    expect(next[8]).toBe('bra-serie-a')
    expect(next[1]).toBe('bra-serie-a')
    expect(next[12]).toBe('bra-serie-b')
    // A lanterna da Premier League (divisão 1, como a Série A) fica onde está.
    expect(next[16]).toBe('eng-premier-league')
    // Balanceado: 6 clubes em cada liga brasileira após a troca.
    expect(Object.values(next).filter((l) => l === 'bra-serie-a')).toHaveLength(6)
    expect(Object.values(next).filter((l) => l === 'bra-serie-b')).toHaveLength(6)
  })
})

describe('faixas da tabela no mock (issue #128)', () => {
  it('a faixa de cada linha espelha a pirâmide: Z-2 na Série A, acesso no topo da B, Premier toda NONE', () => {
    expect(initialStandings[0]!.rows.map((r) => r.zone)).toEqual(
      ['NONE', 'NONE', 'NONE', 'NONE', 'RELEGATION', 'RELEGATION'],
    )
    expect(initialStandings[1]!.rows.map((r) => r.zone)).toEqual(
      ['PROMOTION', 'PROMOTION', 'NONE', 'NONE', 'NONE', 'NONE'],
    )
    expect(initialStandings[2]!.rows.every((r) => r.zone === 'NONE')).toBe(true)
  })

  it('a faixa é da POSIÇÃO: reordenar a tabela mantém as faixas no lugar', () => {
    // Todos os visitantes vencem: a ordem da tabela muda.
    const round = {
      ...state.currentRound,
      matches: state.currentRound.matches.map((m) => ({ ...m, status: 'Finished' as const, homeGoals: 0, awayGoals: 3 })),
    }
    const before = initialStandings[0]!
    const after = applyRoundToStandings(round, before)

    expect(after.rows.map((r) => r.clubId)).not.toEqual(before.rows.map((r) => r.clubId))
    expect(after.rows.map((r) => r.zone)).toEqual(before.rows.map((r) => r.zone))
  })
})
