// Dados iniciais para os handlers MSW.
// Espelham o protótipo (../../../prototype/goalfather-web.jsx) de forma simplificada.
// Quando o backend Kotlin estiver pronto, esta pasta inteira (mocks/) é removida.

import type {
  Club,
  Player,
  MarketEntry,
  Standings,
  Round,
  RoundMatch,
} from '../domain/types'

const player = (
  id: number,
  name: string,
  pos: Player['position'],
  ovr: number,
  salary: number,
  age: number,
): Player => ({
  id, name, position: pos, age,
  overall: ovr,
  pace: ovr + Math.round(Math.random() * 6 - 3),
  shooting: ovr + Math.round(Math.random() * 6 - 3),
  passing: ovr + Math.round(Math.random() * 6 - 3),
  defending: ovr + Math.round(Math.random() * 6 - 3),
  stamina: 100,
  salary,
  goals: 0,
  yellowCards: 0,
  redCards: 0,
  injured: false,
  star: ovr >= 82,
})

const initialSquad: Player[] = [
  player(1,  'Marcos Figueiredo', 'GK', 78, 25_000_00, 32),
  player(2,  'Rodrigo Alves',     'CB', 75, 18_000_00, 27),
  player(3,  'Túlio Mendes',      'CB', 72, 15_000_00, 24),
  player(4,  'Caio Bernardes',    'CB', 70, 13_000_00, 22),
  player(5,  'Pedro Henrique',    'CB', 73, 16_000_00, 26),
  player(6,  'Felipe Costa',      'MF', 80, 28_000_00, 28),
  player(7,  'Diego Lobato',      'MF', 76, 22_000_00, 25),
  player(8,  'Igor Vasques',      'MF', 74, 19_000_00, 23),
  player(9,  'Carlos Almeida',    'MF', 71, 14_000_00, 21),
  player(10, 'Renato Silva',      'FW', 88, 55_000_00, 29),
  player(11, 'João Faria',        'FW', 79, 26_000_00, 26),
]

export const myClub: Club = {
  id: 1,
  name: 'Goal Father FC',
  cash: 800_000_00,
  stadiumCapacity: 15_000,
  squad: initialSquad,
  ownerId: null,
}

export const marketEntries: MarketEntry[] = [
  { player: player(101, 'Lucas Vidigal',  'FW', 81, 32_000_00, 25), price:  450_000_00 },
  { player: player(102, 'André Pacheco',  'MF', 77, 23_000_00, 24), price:  220_000_00 },
  { player: player(103, 'Bruno Aparício', 'CB', 79, 25_000_00, 27), price:  300_000_00 },
  { player: player(104, 'Eduardo Ramires','GK', 76, 21_000_00, 28), price:  180_000_00 },
]

// ─── Registro de clubes da liga ──────────────────────────────────────────
// Para clubes da IA não precisamos de elenco completo (só ID + nome + força
// média). Elenco real só existe para o clube do usuário (state.clubs[1]).
export interface ClubMeta {
  id: number
  name: string
  strength: number    // overall agregado (mock)
  squad: number[]     // IDs sintéticos para autoria de gols/cartões
  division: number    // divisão INICIAL (a corrente vive em state.divisions)
}

const aiSquad = (offset: number): number[] =>
  Array.from({ length: 11 }, (_, i) => offset + i + 1)

const myClubStrength =
  initialSquad.reduce((sum, p) => sum + p.overall, 0) / initialSquad.length

// 2 divisões (issue #47) — espelha o seed do backend (DataInitializer).
export const clubMeta: Record<number, ClubMeta> = {
  1:  { id: 1,  name: 'Goal Father FC',        strength: myClubStrength, squad: initialSquad.map((p) => p.id), division: 1 },
  2:  { id: 2,  name: 'Atlético Bonsucesso',   strength: 76, squad: aiSquad(2000),  division: 1 },
  3:  { id: 3,  name: 'Esporte Clube Vargem',  strength: 72, squad: aiSquad(3000),  division: 1 },
  4:  { id: 4,  name: 'Tupinambás FC',         strength: 70, squad: aiSquad(4000),  division: 1 },
  5:  { id: 5,  name: 'Independente Sul',      strength: 74, squad: aiSquad(5000),  division: 1 },
  6:  { id: 6,  name: 'Real Capela',           strength: 73, squad: aiSquad(6000),  division: 1 },
  7:  { id: 7,  name: 'Ferroviária da Serra',  strength: 68, squad: aiSquad(7000),  division: 2 },
  8:  { id: 8,  name: 'Operário do Vale',      strength: 66, squad: aiSquad(8000),  division: 2 },
  9:  { id: 9,  name: 'Náutico Boa Vista',     strength: 67, squad: aiSquad(9000),  division: 2 },
  10: { id: 10, name: 'Comercial de Ouro Fino', strength: 64, squad: aiSquad(10000), division: 2 },
  11: { id: 11, name: 'Juventude Alvorada',    strength: 65, squad: aiSquad(11000), division: 2 },
  12: { id: 12, name: 'União Primavera',       strength: 63, squad: aiSquad(12000), division: 2 },
}

const allClubIds = Object.values(clubMeta).map((c) => c.id)

// ─── Divisões (issue #47) ────────────────────────────────────────────────
// Divisão corrente de cada clube — muda na virada de temporada via
// promoção/rebaixamento. Espelha PromotionRelegationRules.kt do backend
// (regra duplicada APENAS aqui em mocks/, que é descartável — CLAUDE.md).

export const PROMOTION_RELEGATION_SPOTS = 2

const initialDivisions: Record<number, number> =
  Object.fromEntries(allClubIds.map((id) => [id, clubMeta[id]!.division]))

// Estado mutável da divisão corrente (sessão) — a virada de temporada troca
// as entradas de quem subiu/desceu.
export const divisionAssignments: Record<number, number> = { ...initialDivisions }

function divisionsInPlay(divisions: Record<number, number>): number[] {
  return [...new Set(Object.values(divisions))].sort((a, b) => a - b)
}

function clubsOfDivision(divisions: Record<number, number>, division: number): number[] {
  return allClubIds.filter((id) => divisions[id] === division)
}

const promotionSpotsOf = (division: number): number =>
  division === 1 ? 0 : PROMOTION_RELEGATION_SPOTS

const relegationSpotsOf = (division: number, divisionCount: number): number =>
  division >= divisionCount ? 0 : PROMOTION_RELEGATION_SPOTS

// Tabelas zeradas de uma temporada, uma por divisão — espelha freshStandings
// do PlayRoundService.
function freshStandings(season: number, divisions: Record<number, number>): Standings[] {
  const tiers = divisionsInPlay(divisions)
  return tiers.map((division) => ({
    season,
    division,
    round: 0,
    promotionSpots: promotionSpotsOf(division),
    relegationSpots: relegationSpotsOf(division, tiers.length),
    rows: clubsOfDivision(divisions, division).map((id, i) => ({
      position: i + 1,
      clubId: id,
      clubName: clubMeta[id]!.name,
      played: 0, wins: 0, draws: 0, losses: 0,
      goalsFor: 0, goalsAgainst: 0, goalDifference: 0, points: 0,
    })),
  }))
}

export const initialStandings: Standings[] = freshStandings(2026, initialDivisions)

/**
 * Promoção/rebaixamento sobre as tabelas finais: em cada par de divisões
 * adjacentes, os últimos N da de cima trocam com os primeiros N da de
 * baixo. Devolve a divisão da próxima temporada por clube — espelha
 * applyPromotionRelegation do backend.
 */
export function applyPromotionRelegation(tables: Standings[]): Record<number, number> {
  const ordered = [...tables].sort((a, b) => a.division - b.division)
  const next: Record<number, number> = {}
  for (const table of ordered) {
    for (const row of table.rows) next[row.clubId] = table.division
  }
  for (let i = 0; i + 1 < ordered.length; i++) {
    const upper = ordered[i]!
    const lower = ordered[i + 1]!
    const spots = Math.min(PROMOTION_RELEGATION_SPOTS, upper.rows.length, lower.rows.length)
    const byPosition = (t: Standings) => [...t.rows].sort((a, b) => a.position - b.position)
    for (const row of byPosition(upper).slice(-spots)) next[row.clubId] = lower.division
    for (const row of byPosition(lower).slice(0, spots)) next[row.clubId] = upper.division
  }
  return next
}

// ─── Geração de rodadas ──────────────────────────────────────────────────
// Round-robin (Berger) POR DIVISÃO, mesclado numa rodada global. Para 6
// clubes por divisão teremos 5 rodadas de 3 partidas por divisão.

function generateRound(roundNumber: number, season: number): Round {
  const divisions = divisionAssignments
  const matches: RoundMatch[] = []

  for (const division of divisionsInPlay(divisions)) {
    const ids = clubsOfDivision(divisions, division).sort((a, b) => a - b)
    const n = ids.length
    if (roundNumber > n - 1) continue // turno desta divisão já terminou

    // Algoritmo: clube de menor id fixo, os outros rotacionam (estilo Berger).
    const fixed = ids[0]!
    const rotating = ids.slice(1)
    const offset = (roundNumber - 1) % (n - 1)
    const rotated = [...rotating.slice(offset), ...rotating.slice(0, offset)]
    const order = [fixed, ...rotated]

    for (let i = 0; i < n / 2; i++) {
      const homeId = order[i]!
      const awayId = order[n - 1 - i]!
      matches.push({
        // rodada × 1000 + divisão × 100 + índice — fórmula do backend
        // (RoundFixturesGenerator), única entre divisões da mesma rodada.
        matchId: roundNumber * 1000 + division * 100 + i + 1,
        homeClubId: homeId,
        awayClubId: awayId,
        homeClubName: clubMeta[homeId]!.name,
        awayClubName: clubMeta[awayId]!.name,
        status: 'Scheduled',
        homeGoals: 0,
        awayGoals: 0,
        minute: 0,
        division,
      })
    }
  }
  return { number: roundNumber, season, status: 'Scheduled', matches }
}

// ─── Atualização da tabela após uma rodada ───────────────────────────────
// Aplica a rodada à tabela de UMA divisão: partidas de clubes fora de
// `current.rows` são ignoradas, então a MESMA rodada global pode ser
// aplicada a cada tabela (mesma semântica do backend, issue #47).
export function applyRoundToStandings(round: Round, current: Standings): Standings {
  const byId = new Map(current.rows.map((r) => [r.clubId, { ...r }]))

  for (const m of round.matches) {
    if (m.status !== 'Finished') continue
    const home = byId.get(m.homeClubId)
    const away = byId.get(m.awayClubId)
    if (!home || !away) continue

    home.played++; away.played++
    home.goalsFor += m.homeGoals
    home.goalsAgainst += m.awayGoals
    away.goalsFor += m.awayGoals
    away.goalsAgainst += m.homeGoals
    home.goalDifference = home.goalsFor - home.goalsAgainst
    away.goalDifference = away.goalsFor - away.goalsAgainst

    if (m.homeGoals > m.awayGoals) {
      home.wins++; home.points += 3
      away.losses++
    } else if (m.homeGoals < m.awayGoals) {
      away.wins++; away.points += 3
      home.losses++
    } else {
      home.draws++; home.points++
      away.draws++; away.points++
    }
  }

  const sorted = [...byId.values()].sort(
    (a, b) =>
      b.points - a.points ||
      b.goalDifference - a.goalDifference ||
      b.goalsFor - a.goalsFor ||
      a.clubName.localeCompare(b.clubName),
  )

  return {
    // Spread preserva divisão e vagas de promoção/rebaixamento da tabela.
    ...current,
    round: round.number,
    rows: sorted.map((r, i) => ({ ...r, position: i + 1 })),
  }
}

// Turno único (Berger): a temporada dura o turno da MAIOR divisão (N-1
// rodadas para N clubes). Constante vale porque as trocas de promoção/
// rebaixamento são balanceadas — os tamanhos das divisões não mudam.
export const SEASON_ROUNDS =
  Math.max(...divisionsInPlay(initialDivisions).map((d) => clubsOfDivision(initialDivisions, d).length)) - 1

// Estado mutável — mocks alteram aqui para simular persistência durante a sessão.
export const state = {
  clubs: { [myClub.id]: myClub } as Record<number, Club>,
  market: [...marketEntries] as MarketEntry[],
  standings: initialStandings.map((s) => ({ ...s, rows: [...s.rows] })) as Standings[],
  currentRound: generateRound(1, 2026) as Round,
  nextRound: () => generateRound(state.currentRound.number + 1, state.currentRound.season),
}

// ─── Auth + seleção de clube (issues #18/#19) ─────────────────────────────
// Estado de auth do mock. O token é `mock-token-<userId>` — o handler de /me
// extrai o id daí. `clubOwners` espelha o `ownerId` do backend (todos os 6
// clubes começam sem dono). A engine de mock (round/standings) continua
// centrada no clube 1; reivindicar outro clube materializa um elenco jogável
// (limitação conhecida do mock, que é descartável — ver CLAUDE.md).
export interface MockUser {
  id: number
  username: string
  password: string
  clubId: number | null
}

// Persistência do estado de auth do mock no localStorage: sobrevive ao reload
// (que reinicia os módulos JS e zeraria `auth`/`clubOwners`). É o que permite
// o E2E (Playwright) navegar para deep-links autenticado após um page.goto.
const MOCK_AUTH_KEY = 'gf:mockauth'

interface PersistedAuth {
  users: MockUser[]
  owners: Record<number, number | null>
  nextId: number
}

function loadPersistedAuth(): PersistedAuth | null {
  try {
    const raw = localStorage.getItem(MOCK_AUTH_KEY)
    return raw ? (JSON.parse(raw) as PersistedAuth) : null
  } catch {
    return null
  }
}

export function persistMockAuth(): void {
  try {
    const snapshot: PersistedAuth = { users: auth.users, owners: clubOwners, nextId: auth.nextId }
    localStorage.setItem(MOCK_AUTH_KEY, JSON.stringify(snapshot))
  } catch {
    /* ignora — sem persistência, o mock só perde estado em reloads */
  }
}

const persistedAuth = loadPersistedAuth()

export const auth = {
  users: (persistedAuth?.users ?? []) as MockUser[],
  nextId: persistedAuth?.nextId ?? 1,
}

// Merge com os defaults em vez de substituição: um localStorage persistido
// ANTES das divisões (issue #47) só conhece os clubes 1..6 — sem o merge, os
// clubes da divisão 2 sumiriam da seleção até um localStorage.clear().
export const clubOwners: Record<number, number | null> = {
  ...Object.fromEntries(allClubIds.map((id) => [id, null])),
  ...(persistedAuth?.owners ?? {}),
}

export const TOKEN_PREFIX = 'mock-token-'

// ─── Prontidão de rodada (issue #20) ──────────────────────────────────────
// Espelha a tabela `round_readiness`: ids de usuário prontos por número de
// rodada. Estado de sessão (não persistido) — o mock é descartável.
export const roundReadiness: Record<number, Set<number>> = {}

// Instante (ms) do primeiro "pronto" de cada rodada — ancora o timeout do
// escape hatch (issue #45), espelhando `MIN(ready_at)` do backend.
const roundFirstReadyAt: Record<number, number> = {}

// Timeout do escape hatch no mock (issue #45). Curto de propósito (30s) para o
// comportamento ser demonstrável/E2E-ável sem esperar os 2min do backend real;
// o valor real vem de `app.round-readiness.timeout` no servidor.
export const READINESS_TIMEOUT_MS = 30_000

/** Técnicos humanos = usuários do mock que já reivindicaram um clube. */
export function managers(): MockUser[] {
  return auth.users.filter((u) => u.clubId != null)
}

/** Status de prontidão de uma rodada (espelha ReadinessStatus do backend). */
export function readinessStatus(roundNumber: number) {
  const ready = roundReadiness[roundNumber] ?? new Set<number>()
  const mgrs = managers()
  const pending = mgrs.filter((u) => !ready.has(u.id))

  // Cronômetro só corre com pendentes E ao menos um pronto (o primeiro pronto
  // o arma) — parity com `buildStatus` do backend (issue #45). O guard
  // `ready.size > 0` é essencial: sem ele, um `firstAt` remanescente de uma
  // rodada anterior de mesmo número (rollover de temporada volta à rodada 1)
  // faria uma rodada nova, sem ninguém pronto, aparecer como `timedOut`.
  const firstAt = roundFirstReadyAt[roundNumber]
  const timerRunning = pending.length > 0 && ready.size > 0 && firstAt != null
  const secondsRemaining = timerRunning
    ? Math.max(0, Math.ceil((firstAt + READINESS_TIMEOUT_MS - Date.now()) / 1000))
    : null

  return {
    roundNumber,
    readyCount: mgrs.length - pending.length,
    totalCount: mgrs.length,
    pendingUsernames: pending.map((u) => u.username),
    secondsRemaining,
    timedOut: secondsRemaining === 0,
  }
}

export function markRoundReady(roundNumber: number, userId: number): void {
  // Só técnicos (donos de clube) sinalizam prontidão — parity com o guard do
  // backend (issue #20, ponto #6); evita marcar usuário sem clube.
  if (!managers().some((u) => u.id === userId)) return
  ;(roundReadiness[roundNumber] ??= new Set<number>()).add(userId)
  roundFirstReadyAt[roundNumber] ??= Date.now()
}

export function resetRoundReadiness(roundNumber: number): void {
  delete roundReadiness[roundNumber]
  // Limpa também a âncora do timeout: senão o timestamp vaza para uma futura
  // rodada de mesmo número (rollover de temporada reusa a rodada 1).
  delete roundFirstReadyAt[roundNumber]
}

export function userIdFromAuthHeader(header: string | null): number | null {
  if (!header || !header.startsWith(`Bearer ${TOKEN_PREFIX}`)) return null
  const id = Number(header.slice(`Bearer ${TOKEN_PREFIX}`.length))
  return Number.isFinite(id) ? id : null
}

/** Resumo de um clube disponível (espelha AvailableClubDto do backend). */
export function availableClubInfo(id: number) {
  const division = divisionAssignments[id] ?? clubMeta[id]!.division
  if (id === 1) {
    const c = state.clubs[1]!
    return {
      id,
      name: c.name,
      cash: c.cash,
      stadiumCapacity: c.stadiumCapacity,
      averageOverall: Math.round(c.squad.reduce((s, p) => s + p.overall, 0) / c.squad.length),
      division,
    }
  }
  const meta = clubMeta[id]!
  return {
    id,
    name: meta.name,
    cash: 500_000_00,
    stadiumCapacity: 12_000,
    averageOverall: meta.strength,
    division,
  }
}

/**
 * Garante um Club jogável em `state.clubs[id]` (issue #19). Chamado ao
 * reivindicar e também no getClub após reload (quando `clubOwners` foi
 * restaurado mas `state.clubs` voltou ao seed). `ownerId` vem de `clubOwners`.
 */
export function materializeClub(id: number): void {
  if (state.clubs[id]) {
    state.clubs[id] = { ...state.clubs[id]!, ownerId: clubOwners[id] ?? state.clubs[id]!.ownerId ?? null }
    return
  }
  const meta = clubMeta[id]
  if (!meta) return
  const positions: Player['position'][] = ['GK', 'CB', 'CB', 'CB', 'CB', 'MF', 'MF', 'MF', 'MF', 'FW', 'FW']
  state.clubs[id] = {
    id,
    name: meta.name,
    cash: 500_000_00,
    stadiumCapacity: 12_000,
    ownerId: clubOwners[id] ?? null,
    squad: positions.map((pos, i) =>
      player(id * 1000 + i + 1, `${meta.name} ${i + 1}`, pos, meta.strength, 10_000_00, 25),
    ),
  }
}

// Vira a temporada (issue #11): aplica promoção/rebaixamento às tabelas
// finais (issue #47), zera estatísticas do elenco do usuário, cria tabelas
// novas (uma por divisão) e gera a rodada 1 da próxima temporada. Espelha
// startNextSeason do backend, mantendo a parity mock ↔ real.
export function startNewSeason(season: number, finalTables: Standings[]): void {
  Object.assign(divisionAssignments, applyPromotionRelegation(finalTables))
  const my = state.clubs[1]
  if (my) {
    state.clubs[1] = {
      ...my,
      squad: my.squad.map((p) => ({ ...p, goals: 0, yellowCards: 0, redCards: 0, injured: false })),
    }
  }
  state.standings = freshStandings(season, divisionAssignments)
  state.currentRound = generateRound(1, season)
}
