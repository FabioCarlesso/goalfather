// Testes da copa do mock (issue #61) — garantem que o mock espelha
// CupRules.kt / SeasonCalendar.kt / PenaltyRules.kt do backend: mesmas
// propriedades (folgas, avanço pela chave, prêmio, calendário, pênaltis com
// vencedor único), não os mesmos números — o RNG é outro.

import { describe, expect, it } from 'vitest'
import {
  CUP_PRIZE_CENTS,
  cupRound,
  currentStage,
  drawCup,
  recordCupRound,
  seasonCalendar,
  tieWinner,
} from './cup'
import { MulberryRng, penaltyShootout, simulateMatch, type SquadMember } from './engine'
import { applyRoundToStandings, calendar, initialStandings, roundTrainingReports, state } from './seed'
import type { Cup, CupClub, Round } from '../domain/types'

const clubs: CupClub[] = Array.from({ length: 12 }, (_, i) => ({ id: i + 1, name: `C${i + 1}` }))
const draw = (seed = 2026): Cup => drawCup(2026, clubs, new MulberryRng(seed))

/** Encerra a rodada de copa com o mandante vencendo 1×0 (ou empate com pênaltis). */
const finish = (round: Round, draws = false): Round => ({
  ...round,
  status: 'Finished',
  matches: round.matches.map((m) => ({
    ...m,
    status: 'Finished',
    homeGoals: 1,
    awayGoals: draws ? 1 : 0,
    ...(draws ? { penalties: { home: 3, away: 5 } } : {}),
  })),
})

describe('copa do mock (issue #61)', () => {
  it('mesma seed sorteia a mesma chave', () => {
    expect(draw()).toEqual(draw())
    expect(draw(1).stages).not.toEqual(draw(2).stages)
  })

  it('12 clubes viram oitavas com 4 folgas nos índices pares, cada clube uma vez', () => {
    const stage = draw().stages[0]!
    expect(stage.phase).toBe('ROUND_OF_16')
    expect(stage.ties.filter((t) => t.type === 'Bye').map((t) => t.index)).toEqual([0, 2, 4, 6])
    const ids = stage.ties.flatMap((t) => (t.type === 'Bye' ? [t.club.id] : [t.home.id, t.away.id]))
    expect(ids.sort((a, b) => a - b)).toEqual(clubs.map((c) => c.id))
  })

  it('a copa traz todas as fases com o prêmio de cada uma', () => {
    expect(draw().phases.map((p) => p.phase)).toEqual(['ROUND_OF_16', 'QUARTER_FINALS', 'SEMI_FINALS', 'FINAL'])
    expect(draw().currentPhase).toBe('ROUND_OF_16')
  })

  it('rodada de copa tem só os jogos em aberto, com matchId da data', () => {
    const cup = draw()
    const round = cupRound(2, 2026, cup, () => 1)
    expect(round.stage).toEqual({ type: 'Cup', phase: 'ROUND_OF_16' })
    expect(round.matches).toHaveLength(4)
    expect(round.matches.every((m) => m.matchId > 2000 && m.matchId < 2100)).toBe(true)
  })

  it('vencedores avançam (2k × 2k+1) e só quem venceu em campo leva o prêmio', () => {
    const cup = draw()
    const { cup: next, prizes } = recordCupRound(cup, finish(cupRound(2, 2026, cup, () => 1)))

    const r16 = next.stages[0]!
    const quarters = next.stages[1]!
    expect(quarters.phase).toBe('QUARTER_FINALS')
    quarters.ties.forEach((tie, k) => {
      expect(tie.type).toBe('Fixture')
      if (tie.type !== 'Fixture') return
      expect(tie.home).toEqual(tieWinner(r16.ties[2 * k]!))
      expect(tie.away).toEqual(tieWinner(r16.ties[2 * k + 1]!))
    })
    expect(prizes.size).toBe(4)
    expect([...prizes.values()].every((v) => v === CUP_PRIZE_CENTS.ROUND_OF_16)).toBe(true)
    const byeIds = r16.ties.flatMap((t) => (t.type === 'Bye' ? [t.club.id] : []))
    expect(byeIds.some((id) => prizes.has(id))).toBe(false)
  })

  it('empate com pênaltis classifica quem venceu a disputa e registra o winnerId', () => {
    const cup = draw()
    const { cup: next } = recordCupRound(cup, finish(cupRound(2, 2026, cup, () => 1), true))
    for (const tie of next.stages[0]!.ties) {
      if (tie.type !== 'Fixture') continue
      expect(tie.result?.penalties).toEqual({ home: 3, away: 5 })
      expect(tie.winnerId).toBe(tie.away.id)
    }
  })

  it('copa jogada até o fim tem campeão e nenhuma fase em disputa', () => {
    let cup = draw()
    for (let date = 2; currentStage(cup) != null; date++) {
      cup = recordCupRound(cup, finish(cupRound(date, 2026, cup, () => 1))).cup
    }
    expect(cup.stages.map((s) => s.phase)).toEqual(['ROUND_OF_16', 'QUARTER_FINALS', 'SEMI_FINALS', 'FINAL'])
    expect(cup.champion).toBeDefined()
    expect(cup.currentPhase).toBeUndefined()
  })

  it('calendário intercala uma fase depois de cada uma das 4 primeiras rodadas', () => {
    expect(seasonCalendar(5, ['ROUND_OF_16', 'QUARTER_FINALS', 'SEMI_FINALS', 'FINAL'])).toEqual([
      { type: 'League', matchday: 1 }, { type: 'Cup', phase: 'ROUND_OF_16' },
      { type: 'League', matchday: 2 }, { type: 'Cup', phase: 'QUARTER_FINALS' },
      { type: 'League', matchday: 3 }, { type: 'Cup', phase: 'SEMI_FINALS' },
      { type: 'League', matchday: 4 }, { type: 'Cup', phase: 'FINAL' },
      { type: 'League', matchday: 5 },
    ])
    expect(seasonCalendar(15, ['ROUND_OF_16', 'QUARTER_FINALS', 'SEMI_FINALS', 'FINAL'])
      .flatMap((s, i, all) => (s.type === 'Cup' ? [all.slice(0, i).filter((x) => x.type === 'League').length] : [])))
      .toEqual([3, 6, 9, 12])
  })

  it('o seed nasce com a copa sorteada e a data 2 é a primeira fase', () => {
    expect(state.cup?.phases).toHaveLength(4)
    expect(calendar()).toHaveLength(9)
    expect(state.currentRound.stage).toEqual({ type: 'League', matchday: 1 })
    expect(state.nextRound().stage).toEqual({ type: 'Cup', phase: 'ROUND_OF_16' })
  })

  it('extrato de treino cobre a liga inteira, mesmo quem não jogou a data de copa', () => {
    const mine = { clubId: 1, focus: 'FISICO' as const, events: [] }
    const reports = roundTrainingReports(mine)
    // 12 clubes, embora uma data de oitavas só tenha 4 jogos (8 clubes).
    expect(reports).toHaveLength(12)
    expect(reports.find((r) => r.clubId === 1)).toBe(mine)
    expect(reports.filter((r) => r.clubId !== 1).every((r) => r.focus === 'DESCANSO')).toBe(true)
  })

  it('rodada de copa não mexe na tabela', () => {
    const table = initialStandings[0]!
    const cupDate = finish(cupRound(2, 2026, draw(), () => 1))
    expect(applyRoundToStandings(cupDate, table)).toBe(table)
  })
})

describe('pênaltis do mock (issue #61)', () => {
  const eleven = (offset: number): SquadMember[] =>
    (['GK', 'CB', 'CB', 'CB', 'CB', 'MF', 'MF', 'MF', 'MF', 'FW', 'FW'] as const)
      .map((pos, i) => ({ id: offset + i, pos }))

  it('toda disputa tem vencedor único e o placar resume as cobranças', () => {
    for (let seed = 0; seed < 300; seed++) {
      const s = penaltyShootout(eleven(0), eleven(100), 75, 75, new MulberryRng(seed))
      expect(s.homeScore).not.toBe(s.awayScore)
      expect(s.kicks.filter((k) => k.home && k.scored)).toHaveLength(s.homeScore)
      expect(s.kicks.filter((k) => !k.home && k.scored)).toHaveLength(s.awayScore)
    }
  })

  it('jogo de copa empatado sai com a disputa antes do FullTime; o de liga, não', () => {
    let draws = 0
    for (let matchId = 1; matchId <= 200; matchId++) {
      const setup = {
        matchId, homeName: 'A', awayName: 'B', homeStrength: 75, awayStrength: 75,
        homeSquad: eleven(0), awaySquad: eleven(100),
      }
      const league = [...simulateMatch(setup)]
      const cup = [...simulateMatch({ ...setup, knockout: true })]
      expect(league.some((e) => e.type === 'PenaltyShootout')).toBe(false)
      // O tempo normal é idêntico: a disputa consome o RNG depois dos 90'.
      expect(cup.filter((e) => e.type !== 'PenaltyShootout')).toEqual(league)

      const ft = cup[cup.length - 1]!
      expect(ft.type).toBe('FullTime')
      if (ft.type === 'FullTime' && ft.homeGoals === ft.awayGoals) {
        draws++
        expect(cup[cup.length - 2]!.type).toBe('PenaltyShootout')
      }
    }
    expect(draws).toBeGreaterThan(0)
  })
})
