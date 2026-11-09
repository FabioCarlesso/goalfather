import { describe, expect, it, vi } from 'vitest'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { RoundSummary, type RoundSummaryProps } from './RoundSummary'
import { renderWithProviders } from '../../test/render'
import { server } from '../../test/setup'
import type { BoardReview, Round, RoundMatch, StandingRow, Standings } from '../../domain/types'
import type { RoundOutcome, SeasonFinishedEvent } from '../../api/queries/useRoundStream'

const MY_CLUB = 1

const myMatch: RoundMatch = {
  matchId: 10,
  homeClubId: 7,
  awayClubId: MY_CLUB,
  homeClubName: 'Rival FC',
  awayClubName: 'Goal Father FC',
  status: 'Finished',
  homeGoals: 1,
  awayGoals: 2,
  minute: 90,
  division: 1,
}

function row(position: number, clubId: number, clubName: string, points: number): StandingRow {
  return {
    position, clubId, clubName, points,
    played: 5, wins: 0, draws: 0, losses: 0, goalsFor: 0, goalsAgainst: 0, goalDifference: 0,
  }
}

const table: Standings = {
  season: 2026,
  division: 1,
  round: 5,
  promotionSpots: 0,
  relegationSpots: 2,
  rows: [row(1, 3, 'Líder', 13), row(2, 4, 'Vice', 11), row(3, 5, 'Terceiro', 10), row(4, MY_CLUB, 'Goal Father FC', 9)],
}

const played: Round = {
  number: 6,
  season: 2026,
  status: 'Finished',
  stage: { type: 'League', matchday: 5 },
  matches: [myMatch],
}

const outcome: RoundOutcome = {
  played,
  standings: [table],
  finance: { clubId: MY_CLUB, ticketRevenue: 0, salariesPaid: 2_000_000, deficit: 0, ticketPrice: 4000, attendance: 0, cupPrize: 0 },
  training: { clubId: MY_CLUB, focus: 'ATAQUE', events: [] },
  season: null,
}

const review = (verdict: BoardReview['verdict']): BoardReview => ({
  clubId: MY_CLUB,
  clubName: 'Goal Father FC',
  season: 2026,
  objective: 'TOP_HALF',
  targetPosition: 5,
  finalPosition: 4,
  confidence: 60,
  verdict,
})

function seasonEnd(boardReview: BoardReview): SeasonFinishedEvent {
  return {
    type: 'SeasonFinished',
    season: 2026,
    champion: row(1, 3, 'Líder', 40),
    standings: [table],
    retirements: [
      {
        clubId: MY_CLUB,
        retired: { id: 50, name: 'Veterano', age: 37, overall: 60 },
        promoted: { id: 51, name: 'Garoto', age: 17, overall: 55 },
      },
      {
        clubId: 9,
        retired: { id: 60, name: 'De outro clube', age: 36, overall: 58 },
        promoted: { id: 61, name: 'Outro garoto', age: 17, overall: 50 },
      },
    ],
    boardReviews: [boardReview],
  } as SeasonFinishedEvent
}

function renderSummary(overrides: Partial<RoundSummaryProps> = {}) {
  const props: RoundSummaryProps = {
    outcome,
    myClubId: MY_CLUB,
    myMatch,
    myMatchState: { homeGoals: 1, awayGoals: 2, minute: 90, status: 'Finished' },
    canStart: true,
    onNextRound: vi.fn(),
    onShowDetails: vi.fn(),
    ...overrides,
  }
  return { props, ...renderWithProviders(<RoundSummary {...props} />) }
}

describe('RoundSummary (issue #112)', () => {
  it('rodada comum: resultado, posição, finanças e treino, nessa ordem', () => {
    renderSummary()

    const summary = screen.getByRole('region', { name: 'Resumo da rodada' })
    expect(within(summary).getByText('Rodada 5 · Temporada 2026')).toBeInTheDocument()
    // Vitória fora de casa: o rótulo é do ponto de vista do técnico.
    expect(within(summary).getByText('Vitória')).toBeInTheDocument()
    expect(screen.getByTestId('my-match-score')).toHaveTextContent('Rival FC 1 × 2 Goal Father FC')
    expect(within(summary).getByText('Fora contra Rival FC')).toBeInTheDocument()
    expect(within(summary).getByText(/Você está em/)).toHaveTextContent('Você está em 4º na Divisão 1 · 9 pts')
    expect(within(summary).getByText(/salários$/)).toBeInTheDocument()

    const headings = within(summary).getAllByRole('heading', { level: 3 }).map((h) => h.textContent)
    expect(headings).toEqual([
      'Sua partida',
      'Pontuações atualizadas após a rodada 5',
      'Treino da semana — Ataque',
    ])
    // Sem virada de temporada, nada de campeão nem diretoria.
    expect(screen.queryByText(/é o campeão/)).not.toBeInTheDocument()
    expect(screen.queryByTestId('board-review')).not.toBeInTheDocument()
  })

  it('recebe o foco ao surgir, para quem estava rolando o feed', () => {
    renderSummary()
    expect(screen.getByRole('heading', { name: 'Resumo da rodada' })).toHaveFocus()
  })

  it('botões levam aos detalhes e à próxima rodada', async () => {
    const user = userEvent.setup()
    const { props } = renderSummary()

    await user.click(screen.getByRole('button', { name: 'Ver detalhes da partida' }))
    await user.click(screen.getByRole('button', { name: 'Próxima rodada' }))

    expect(props.onShowDetails).toHaveBeenCalledOnce()
    expect(props.onNextRound).toHaveBeenCalledOnce()
  })

  it('próxima rodada espera a prontidão da liga', () => {
    renderSummary({ canStart: false })
    expect(screen.getByRole('button', { name: 'Próxima rodada' })).toBeDisabled()
    expect(screen.getByText(/Sinalize que está pronto/)).toBeInTheDocument()
  })

  it('data de copa decidida nos pênaltis', () => {
    renderSummary({
      outcome: { ...outcome, played: { ...played, stage: { type: 'Cup', phase: 'ROUND_OF_16' } }, training: null },
      myMatchState: { homeGoals: 1, awayGoals: 1, minute: 90, status: 'Finished', penalties: { home: 5, away: 4 } },
    })
    expect(screen.getByText('Derrota nos pênaltis')).toBeInTheDocument()
    expect(screen.getByText('Copa — Oitavas de final encerrada')).toBeInTheDocument()
    expect(screen.queryByRole('list', { name: 'Três primeiros da divisão' })).not.toBeInTheDocument()
  })

  it('fim de temporada: campeão, veredito da diretoria e aposentadorias do clube', () => {
    renderSummary({ outcome: { ...outcome, season: seasonEnd(review({ type: 'TargetMet', bonusCents: 100_000_00 })) } })

    expect(screen.getByText('Líder é o campeão!')).toBeInTheDocument()
    expect(screen.getByTestId('board-review')).toHaveTextContent(/Meta cumprida/)
    expect(screen.getByText('Veterano')).toBeInTheDocument()
    expect(screen.queryByText('De outro clube')).not.toBeInTheDocument()
    // Sem demissão, o veredito fica depois do resultado da partida.
    const order = within(screen.getByTestId('round-summary'))
      .getAllByRole('heading', { level: 3 })
      .map((h) => h.textContent)
    expect(order.indexOf('Sua partida')).toBeLessThan(order.findIndex((t) => t?.startsWith('Diretoria')))
  })

  it('demissão sobe para o topo do resumo com destaque de alerta', async () => {
    let meCalls = 0
    server.use(
      http.get('/api/auth/me', () => {
        meCalls += 1
        return HttpResponse.json({ id: 1, username: 'tester', clubId: null })
      }),
    )
    renderSummary({ outcome: { ...outcome, season: seasonEnd(review({ type: 'Fired' })) } })

    const summary = screen.getByTestId('round-summary')
    expect(summary).toHaveClass('border-red-500')
    const verdict = screen.getByTestId('board-review')
    expect(verdict).toHaveTextContent('Diretoria: Você foi demitido pela diretoria')
    expect(verdict).toHaveClass('border-red-500')
    // Uma vez só, e antes do resultado da partida.
    expect(screen.getAllByTestId('board-review')).toHaveLength(1)
    const order = within(summary).getAllByRole('heading', { level: 3 }).map((h) => h.textContent)
    expect(order[0]).toMatch(/Você foi demitido/)
    // O card segue disparando o fluxo de demissão (rebusca /me → guarda leva a /fired).
    await vi.waitFor(() => expect(meCalls).toBe(1))
  })
})
