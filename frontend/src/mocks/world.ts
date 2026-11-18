// Mundo PEQUENO do mock (issue #131) — mesma forma do pack E2E que o backend
// lê de `data/world/v1` no profile `e2e`: as ligas e os NOMES dos clubes são os
// do pack (os de maior `reputation` de cada liga, empate pelo id), os ids são
// inventados aqui e os elencos são sintéticos. Dado de mock, não regra: quando
// o backend assumir, esta pasta inteira (mocks/) sai.

import type { Country, Player, Position } from '../domain/types'
import { MulberryRng, initialContractOf, marketValueOf } from './engine'

/** Bandeira da Inglaterra: bandeira preta + "tag characters" de `gbeng` (como o `flagEmoji` do backend). */
const ENGLAND_FLAG = '\u{1F3F4}\u{E0067}\u{E0062}\u{E0065}\u{E006E}\u{E0067}\u{E007F}'

/** Países do mundo do mock — mesmos códigos, nomes e bandeiras do catálogo do backend. */
export const WORLD_COUNTRIES = {
  BRA: { code: 'BRA', name: 'Brasil', confederation: 'CONMEBOL', flag: '🇧🇷' },
  ENG: { code: 'ENG', name: 'Inglaterra', confederation: 'UEFA', flag: ENGLAND_FLAG },
} as const satisfies Record<string, Country>

/** Uma liga de pontos corridos do mundo do mock. */
export interface MockLeague {
  id: string
  name: string
  country: Country
  /** Divisão no país (1 = elite). */
  tier: number
  /** A temporada atravessa a virada do ano (Europa: `2026/27`) ou cabe nele (Brasil: `2026`). */
  crossesYear: boolean
  /** Força da liga, a mesma do pack (issue #135): o multiplicador de liga do valor de mercado. */
  strength: number
}

/**
 * Ligas na ordem do contrato de `GET /api/league/standings`: país do mundo
 * (Brasil primeiro), depois a divisão. A posição na lista também entra no
 * `matchId` (ver `generateRound` em `./seed`), por isso a ordem é estável.
 */
export const WORLD_LEAGUES: readonly MockLeague[] = [
  { id: 'bra-serie-a', name: 'Campeonato Brasileiro Série A', country: WORLD_COUNTRIES.BRA, tier: 1, crossesYear: false, strength: 70 },
  { id: 'bra-serie-b', name: 'Campeonato Brasileiro Série B', country: WORLD_COUNTRIES.BRA, tier: 2, crossesYear: false, strength: 62 },
  { id: 'eng-premier-league', name: 'Premier League', country: WORLD_COUNTRIES.ENG, tier: 1, crossesYear: true, strength: 78 },
]

export function leagueById(id: string): MockLeague {
  const league = WORLD_LEAGUES.find((l) => l.id === id)
  if (!league) throw new Error(`Liga ${id} fora do mundo do mock`)
  return league
}

/** `2026` no Brasil, `2026/27` na Europa — o rótulo que o backend manda pronto. */
export const seasonLabelOf = (league: MockLeague, season: number): string =>
  league.crossesYear ? `${season}/${String((season + 1) % 100).padStart(2, '0')}` : String(season)

/** Um clube do mundo do mock: a liga INICIAL (a corrente muda com acesso/rebaixamento). */
export interface MockWorldClub {
  id: number
  name: string
  leagueId: string
  /** Overall médio do elenco sintético. */
  strength: number
  stadiumCapacity: number
  cash: number
}

/**
 * Os 16 clubes. Força proporcional à `reputation` do pack (a ordem dentro de
 * cada liga é a mesma); estádio é o do pack. O clube 1 tem elenco escrito à
 * mão em `./seed` (é o que os testes de página usam); a força dele aqui é a
 * média daquele elenco.
 */
export const WORLD_CLUBS: readonly MockWorldClub[] = [
  // Série A — 6 de maior reputação.
  { id: 1, name: 'Flamengo', leagueId: 'bra-serie-a', strength: 76, stadiumCapacity: 78_838, cash: 160_000_00 },
  { id: 2, name: 'Palmeiras', leagueId: 'bra-serie-a', strength: 75, stadiumCapacity: 43_713, cash: 100_000_00 },
  { id: 3, name: 'Corinthians', leagueId: 'bra-serie-a', strength: 73, stadiumCapacity: 47_252, cash: 100_000_00 },
  { id: 4, name: 'Atlético Mineiro', leagueId: 'bra-serie-a', strength: 72, stadiumCapacity: 44_892, cash: 100_000_00 },
  { id: 5, name: 'Botafogo', leagueId: 'bra-serie-a', strength: 72, stadiumCapacity: 46_931, cash: 100_000_00 },
  { id: 6, name: 'São Paulo', leagueId: 'bra-serie-a', strength: 71, stadiumCapacity: 120_000, cash: 100_000_00 },
  // Série B — 6 de maior reputação.
  { id: 7, name: 'Fortaleza', leagueId: 'bra-serie-b', strength: 68, stadiumCapacity: 57_876, cash: 80_000_00 },
  { id: 8, name: 'Sport', leagueId: 'bra-serie-b', strength: 67, stadiumCapacity: 35_020, cash: 80_000_00 },
  { id: 9, name: 'Ceará', leagueId: 'bra-serie-b', strength: 66, stadiumCapacity: 20_600, cash: 80_000_00 },
  { id: 10, name: 'Goiás', leagueId: 'bra-serie-b', strength: 65, stadiumCapacity: 54_084, cash: 80_000_00 },
  { id: 11, name: 'América Mineiro', leagueId: 'bra-serie-b', strength: 64, stadiumCapacity: 23_018, cash: 80_000_00 },
  { id: 12, name: 'Atlético Goianiense', leagueId: 'bra-serie-b', strength: 63, stadiumCapacity: 54_084, cash: 80_000_00 },
  // Premier League — 4 de maior reputação.
  { id: 13, name: 'Liverpool', leagueId: 'eng-premier-league', strength: 81, stadiumCapacity: 61_276, cash: 200_000_00 },
  { id: 14, name: 'Manchester City', leagueId: 'eng-premier-league', strength: 81, stadiumCapacity: 52_900, cash: 200_000_00 },
  { id: 15, name: 'Arsenal', leagueId: 'eng-premier-league', strength: 80, stadiumCapacity: 60_338, cash: 200_000_00 },
  { id: 16, name: 'Chelsea', leagueId: 'eng-premier-league', strength: 78, stadiumCapacity: 41_875, cash: 200_000_00 },
]

// ─── Elencos sintéticos ──────────────────────────────────────────────────
// Porta simplificada do `SyntheticSquad.kt` do backend: nomes plausíveis do
// país do clube, overall em torno da força com média exata, posição e idade
// pela vaga. Determinístico pelo id do clube (`MulberryRng(id)`).

const FIRST_NAMES: Record<string, readonly string[]> = {
  BRA: ['Adriano', 'Anderson', 'Arthur', 'Breno', 'Bruno', 'Caio', 'Cássio', 'Daniel', 'Danilo', 'Davi',
    'Douglas', 'Elias', 'Everton', 'Fábio', 'Gabriel', 'Gustavo', 'Hugo', 'Jefferson', 'Jonas', 'Leandro',
    'Luan', 'Marcelo', 'Mateus', 'Murilo', 'Otávio', 'Paulo', 'Rafael', 'Raul', 'Thiago', 'Vinícius', 'Wesley'],
  ENG: ['Harry', 'Jack', 'Oliver', 'George', 'James', 'Thomas', 'William', 'Charlie', 'Samuel', 'Joseph',
    'Luke', 'Ben', 'Callum', 'Mason', 'Jordan', 'Kyle', 'Reece', 'Declan', 'Marcus', 'Jamie'],
}

const LAST_NAMES: Record<string, readonly string[]> = {
  BRA: ['Abreu', 'Andrade', 'Araújo', 'Barbosa', 'Barros', 'Batista', 'Borges', 'Cardoso', 'Carvalho', 'Castro',
    'Coelho', 'Cunha', 'Dias', 'Duarte', 'Farias', 'Fernandes', 'Fonseca', 'Freitas', 'Gomes', 'Lima', 'Lopes',
    'Machado', 'Martins', 'Medeiros', 'Moreira', 'Nogueira', 'Pereira', 'Queiroz', 'Ramos', 'Rocha', 'Teixeira'],
  ENG: ['Smith', 'Jones', 'Taylor', 'Brown', 'Wilson', 'Evans', 'Walker', 'Wright', 'Robinson', 'Thompson',
    'White', 'Hughes', 'Edwards', 'Green', 'Hall', 'Wood', 'Harris', 'Clarke', 'Turner', 'Hill'],
}

/** 4-4-2 da vaga: 1 GK, 4 CB, 4 MF, 2 FW — as posições que a engine pondera (issue #57). */
export const SQUAD_POSITIONS: readonly Position[] =
  ['GK', 'CB', 'CB', 'CB', 'CB', 'MF', 'MF', 'MF', 'MF', 'FW', 'FW']

/** Idades espalhadas entre 21 e 32, como o `ageForSlot` do backend (issue #55). */
const ageForSlot = (slot: number): number => 21 + ((slot * 5) % 12)

/** Salário do jogador sintético, o mesmo dos clubes da IA no backend. */
export const SYNTHETIC_SALARY_CENTS = 2_000_00

/** Deltas em -3..3 que somam zero (pares +d/−d), embaralhados: a média do elenco fica exata. */
function balancedDeltas(count: number, rng: MulberryRng): number[] {
  const deltas: number[] = count % 2 === 1 ? [0] : []
  for (let d = 1; deltas.length < count; d = (d % 3) + 1) deltas.push(d, -d)
  const out = deltas.slice(0, count)
  for (let i = out.length - 1; i > 0; i--) {
    const j = Math.floor(rng.next() * (i + 1))
    ;[out[i], out[j]] = [out[j]!, out[i]!]
  }
  return out
}

const clamp = (v: number): number => Math.min(99, Math.max(1, v))

/**
 * Elenco sintético de 11 jogadores de um clube. Ids `clube × 1000 + vaga`;
 * nomes únicos no mundo (`taken` é atualizado aqui). A nacionalidade é a do
 * clube, senão o limite de estrangeiros (issue #130) barraria metade do time.
 */
export function syntheticSquad(club: MockWorldClub, country: Country, taken: Set<string>): Player[] {
  const rng = new MulberryRng(club.id * 7_919 + 131)
  const firsts = FIRST_NAMES[country.code] ?? FIRST_NAMES.BRA!
  const lasts = LAST_NAMES[country.code] ?? LAST_NAMES.BRA!
  const nextName = (): string => {
    for (;;) {
      const name = `${rng.pick(firsts)} ${rng.pick(lasts)}`
      if (!taken.has(name)) {
        taken.add(name)
        return name
      }
    }
  }
  const deltas = balancedDeltas(SQUAD_POSITIONS.length, rng)
  return SQUAD_POSITIONS.map((position, i) => {
    const slot = i + 1
    const overall = clamp(club.strength + deltas[i]!)
    const attr = () => clamp(overall + Math.round(rng.next() * 6 - 3))
    return {
      id: club.id * 1000 + slot,
      name: nextName(),
      position,
      age: ageForSlot(slot),
      overall,
      pace: attr(),
      shooting: attr(),
      passing: attr(),
      defending: attr(),
      stamina: 100,
      salary: SYNTHETIC_SALARY_CENTS,
      // Contrato inicial (issue #136), com término na temporada da liga do país.
      contract: initialContractOf({ id: club.id * 1000 + slot, age: ageForSlot(slot) }, country.code),
      goals: 0,
      yellowCards: 0,
      redCards: 0,
      availability: { type: 'Available' },
      suspensions: [],
      star: overall >= 82,
      nationality: country,
      // Valor de referência (issue #135); no elenco, o handler o recalcula com a liga do clube.
      marketValue: marketValueOf({ overall, age: ageForSlot(slot) }),
    }
  })
}
