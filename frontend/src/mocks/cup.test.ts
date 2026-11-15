// Testes da copa do mock (issues #61 e #131) — garantem que o mock espelha
// CupRules.kt / PenaltyRules.kt do backend: mesmas propriedades (entrantes por
// força, ida e volta pelo agregado, pênaltis só no jogo único ou no agregado
// empatado, prêmio, calendário), não os mesmos números — o RNG é outro.

import { describe, expect, it } from 'vitest'
import {
  E2E_CUP_FORMAT,
  cupLegSlots,
  cupPhasePrize,
  cupRound,
  currentStage,
  decidesOnPenalties,
  drawCup,
  recordCupRound,
  seasonCalendar,
} from './cup'
import { MulberryRng, penaltyShootout, simulateMatch, type SquadMember } from './engine'
import { matchdayOf, matchdaySerial } from './calendar'
import { applyRoundToStandings, calendar, initialStandings, nationalCupClubs, roundTrainingReports, state } from './seed'
import type { Cup, CupClub, Round, RoundMatch } from '../domain/types'

/** 12 clubes já em ordem de força: o id 1 é o mais forte. */
const clubs: CupClub[] = Array.from({ length: 12 }, (_, i) => ({ id: i + 1, name: `C${i + 1}` }))
const draw = (season = 2026): Cup => drawCup(season, E2E_CUP_FORMAT, clubs)

/**
 * Encerra o encaixe de copa: cada jogo termina com o placar de `score`; se a
 * perna for aos pênaltis, o mandante vence a disputa 4 × 3.
 */
const finish = (round: Round, score: (m: RoundMatch) => [number, number]): Round => ({
  ...round,
  status: 'Finished',
  matches: round.matches.map((m) => {
    const [homeGoals, awayGoals] = score(m)
    return {
      ...m, status: 'Finished', homeGoals, awayGoals,
      ...(decidesOnPenalties(m.stage, homeGoals, awayGoals) ? { penalties: { home: 4, away: 3 } } : {}),
    }
  }),
})

/** O mais forte (menor id) vence cada jogo por 1 × 0, em casa ou fora. */
const strongerWins = (m: RoundMatch): [number, number] => (m.homeClubId < m.awayClubId ? [1, 0] : [0, 1])

const play = (cup: Cup, week: number, score: (m: RoundMatch) => [number, number]) =>
  recordCupRound(cup, finish(cupRound(matchdayOf(2026, week, 'MIDWEEK'), 2026, cup, () => 1), score))

/** Copa com a 1ª fase jogada: quartas (ida e volta) em disputa. */
const quarterFinals = (): Cup => play(draw(), 2, strongerWins).cup

describe('copa do mock (issues #61 e #131)', () => {
  it('mesma seed sorteia a mesma chave', () => {
    expect(draw()).toEqual(draw())
    expect(draw(2026).stages).not.toEqual(draw(2027).stages)
  })

  it('os 4 mais fortes entram nas quartas e os 8 mais fracos jogam a primeira fase', () => {
    const cup = draw()
    expect(cup.phases.map((p) => p.name)).toEqual(['Primeira fase', 'Quartas de final', 'Semifinal', 'Final'])
    expect(cup.phases.map((p) => p.entrants.map((c) => c.id))).toEqual([[5, 6, 7, 8, 9, 10, 11, 12], [1, 2, 3, 4], [], []])
    const opening = cup.stages[0]!
    expect(opening.ties).toHaveLength(4)
    expect(opening.ties.every((t) => t.type === 'Single')).toBe(true)
    expect(opening.ties.flatMap((t) => [t.home.id, t.away.id]).sort((a, b) => a - b)).toEqual([5, 6, 7, 8, 9, 10, 11, 12])
    expect(cup.currentStage).toBe('Primeira fase')
    expect(cup.competitionName).toBe('Copa do Brasil')
  })

  it('prêmio dobra a cada fase até a semifinal e a final paga o maior cheque', () => {
    expect(draw().phases.map((p) => p.prizeCents)).toEqual([cupPhasePrize(3), cupPhasePrize(2), cupPhasePrize(1), cupPhasePrize(0)])
    expect([cupPhasePrize(3), cupPhasePrize(2), cupPhasePrize(1), cupPhasePrize(0)])
      .toEqual([100_000_00, 200_000_00, 400_000_00, 1_000_000_00])
    expect(cupPhasePrize(30)).toBe(10_000_00)
  })

  it('rodada de copa tem só as pernas em aberto, com matchId do encaixe', () => {
    const cup = draw()
    const matchday = matchdayOf(2026, 2, 'MIDWEEK')
    const round = cupRound(matchday, 2026, cup, () => 1)
    expect(round.matches).toHaveLength(4)
    expect(round.matches.every((m) => m.stage.type === 'Cup' && m.stage.stageName === 'Primeira fase' && m.stage.leg === 'SINGLE')).toBe(true)
    expect(round.matches.every((m) => m.competitionId === 'bra-copa-do-brasil')).toBe(true)
    const base = matchdaySerial(matchday) * 1000
    expect(round.matches.every((m) => m.matchId > base && m.matchId < base + 100)).toBe(true)
  })

  it('fase fechada sorteia a seguinte com os vencedores + os entrantes, e só quem passou leva o prêmio', () => {
    const { cup, prizes } = play(draw(), 2, strongerWins)
    const winners = cup.stages[0]!.ties.map((t) => t.winnerId!)
    const quarters = cup.stages[1]!
    expect(quarters.name).toBe('Quartas de final')
    expect(quarters.ties.every((t) => t.type === 'TwoLegged')).toBe(true)
    expect(quarters.ties.flatMap((t) => [t.home.id, t.away.id]).sort((a, b) => a - b))
      .toEqual([...winners, 1, 2, 3, 4].sort((a, b) => a - b))
    expect([...prizes.keys()].sort()).toEqual([...winners].sort())
    expect([...prizes.values()].every((v) => v === cup.phases[0]!.prizeCents)).toBe(true)
  })

  it('ida empatada não vai aos pênaltis e só guarda o placar', () => {
    const cup = quarterFinals()
    const firstLeg = cupRound(matchdayOf(2026, 3, 'MIDWEEK'), 2026, cup, () => 1)
    expect(firstLeg.matches.every((m) => m.stage.type === 'Cup' && m.stage.leg === 'FIRST')).toBe(true)
    expect(firstLeg.matches.some((m) => decidesOnPenalties(m.stage, 1, 1))).toBe(false)

    const { cup: after, prizes } = play(cup, 3, () => [1, 1])
    expect(prizes.size).toBe(0)
    for (const tie of currentStage(after)!.ties) {
      expect(tie.type === 'TwoLegged' && tie.firstLeg).toEqual({ homeGoals: 1, awayGoals: 1 })
      expect(tie.winnerId).toBeUndefined()
    }
  })

  it('volta inverte o mando, carrega o placar da ida e decide pelo agregado', () => {
    const afterFirst = play(quarterFinals(), 3, () => [3, 0]).cup
    const secondLeg = cupRound(matchdayOf(2026, 4, 'MIDWEEK'), 2026, afterFirst, () => 1)
    for (const tie of currentStage(afterFirst)!.ties) {
      const match = secondLeg.matches.find((m) => m.homeClubId === tie.away.id)!
      expect(match.awayClubId).toBe(tie.home.id)
      expect(match.stage).toEqual({ type: 'Cup', stageName: 'Quartas de final', leg: 'SECOND', firstLeg: { homeGoals: 3, awayGoals: 0 } })
    }
    // Volta 1 × 0 para o outro: agregado 3 × 1, passa o mandante da ida, sem pênaltis.
    const { cup } = play(afterFirst, 4, () => [1, 0])
    for (const tie of cup.stages[1]!.ties) {
      expect(tie.winnerId).toBe(tie.home.id)
      expect(tie.type === 'TwoLegged' && tie.aggregate).toEqual({ home: 3, away: 1 })
      expect(tie.type === 'TwoLegged' && tie.penalties).toBeUndefined()
    }
  })

  it('volta com agregado empatado vai aos pênaltis, na orientação do confronto', () => {
    const afterFirst = play(quarterFinals(), 3, () => [2, 1]).cup
    const secondLeg = cupRound(matchdayOf(2026, 4, 'MIDWEEK'), 2026, afterFirst, () => 1)
    expect(secondLeg.matches.every((m) => decidesOnPenalties(m.stage, 1, 0))).toBe(true)
    expect(secondLeg.matches.some((m) => decidesOnPenalties(m.stage, 0, 0) || decidesOnPenalties(m.stage, 2, 0))).toBe(false)

    // Volta 1 × 0 para o mandante da volta (o `away` do confronto), que vence os pênaltis 4 × 3.
    const { cup } = play(afterFirst, 4, () => [1, 0])
    for (const tie of cup.stages[1]!.ties) {
      expect(tie.type === 'TwoLegged' && tie.aggregate).toEqual({ home: 2, away: 2 })
      expect(tie.type === 'TwoLegged' && tie.penalties).toEqual({ home: 3, away: 4 })
      expect(tie.winnerId).toBe(tie.away.id)
    }
    expect(cup.currentStage).toBe('Semifinal')
  })

  it('volta empatada no agregado sem pênaltis deixa o confronto em aberto', () => {
    const afterFirst = play(quarterFinals(), 3, () => [2, 1]).cup
    const round = cupRound(matchdayOf(2026, 4, 'MIDWEEK'), 2026, afterFirst, () => 1)
    const noShootout: Round = {
      ...round,
      matches: round.matches.map((m) => ({ ...m, status: 'Finished', homeGoals: 1, awayGoals: 0 })),
    }
    expect(recordCupRound(afterFirst, noShootout).cup).toEqual(afterFirst)
  })

  it('copa jogada até o fim tem 17 jogos em 6 encaixes e campeão', () => {
    let cup = draw()
    let games = 0
    let week = 2
    for (; currentStage(cup) != null; week++) {
      games += cupRound(matchdayOf(2026, week, 'MIDWEEK'), 2026, cup, () => 1).matches.length
      cup = play(cup, week, strongerWins).cup
    }
    expect(games).toBe(4 + 8 + 4 + 1)
    expect(week - 2).toBe(cupLegSlots(cup).length)
    expect(cup.stages.map((s) => s.name)).toEqual(['Primeira fase', 'Quartas de final', 'Semifinal', 'Final'])
    expect(cup.champion?.id).toBe(1)
    expect(cup.currentStage).toBeUndefined()
  })

  it('final é jogo único e o empate dela vai aos pênaltis', () => {
    let cup = draw()
    for (let week = 2; cup.currentStage !== 'Final'; week++) cup = play(cup, week, strongerWins).cup
    const final = cupRound(matchdayOf(2026, 20, 'MIDWEEK'), 2026, cup, () => 1).matches
    expect(final).toHaveLength(1)
    expect(final[0]!.stage).toEqual({ type: 'Cup', stageName: 'Final', leg: 'SINGLE' })
    expect(decidesOnPenalties(final[0]!.stage, 0, 0)).toBe(true)
    const { cup: done, prizes } = play(cup, 20, () => [0, 0])
    expect(done.champion?.id).toBe(final[0]!.homeClubId)
    expect(prizes.get(final[0]!.homeClubId)).toBe(cupPhasePrize(0))
  })

  it('liga nunca vai aos pênaltis', () => {
    expect(decidesOnPenalties({ type: 'League', matchday: 1 }, 0, 0)).toBe(false)
  })

  it('calendário intercala uma perna de copa por encaixe e a final antes da última rodada', () => {
    expect(seasonCalendar(5, cupLegSlots(draw())).map((s) => (s.type === 'League' ? `L${s.matchday}` : `${s.stageName}/${s.leg}`)))
      .toEqual([
        'L1', 'Primeira fase/SINGLE', 'Quartas de final/FIRST',
        'L2', 'Quartas de final/SECOND', 'Semifinal/FIRST',
        'L3', 'Semifinal/SECOND',
        'L4', 'Final/SINGLE',
        'L5',
      ])
  })

  it('o seed nasce com a Copa do Brasil dos clubes do Brasil e o 2º encaixe é a primeira fase, numa quarta', () => {
    expect(nationalCupClubs()).toHaveLength(12)
    expect(state.cup?.phases).toHaveLength(4)
    expect(calendar()).toHaveLength(11)
    expect(state.currentRound.matches.every((m) => m.stage.type === 'League' && m.stage.matchday === 1)).toBe(true)
    const next = state.nextRound()
    expect(next.matchday.slot).toBe('MIDWEEK')
    expect(next.matches.every((m) => m.stage.type === 'Cup' && m.stage.stageName === 'Primeira fase')).toBe(true)
  })

  it('extrato de treino cobre a liga inteira, mesmo quem não jogou a data de copa', () => {
    const mine = { clubId: 1, focus: 'FISICO' as const, events: [] }
    const reports = roundTrainingReports(mine)
    // O mundo inteiro (16 clubes, issue #131), embora uma data de oitavas só tenha 4 jogos (8 clubes).
    expect(reports).toHaveLength(16)
    expect(reports.find((r) => r.clubId === 1)).toBe(mine)
    expect(reports.filter((r) => r.clubId !== 1).every((r) => r.focus === 'DESCANSO')).toBe(true)
  })

  it('rodada de copa não mexe na tabela', () => {
    const table = initialStandings[0]!
    const cupDate = finish(cupRound(matchdayOf(2026, 2, 'MIDWEEK'), 2026, draw(), () => 1), strongerWins)
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
      const cup = [...simulateMatch({ ...setup, decidesOnPenalties: (h: number, a: number) => h === a })]
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
