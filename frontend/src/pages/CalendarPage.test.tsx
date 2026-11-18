import { describe, expect, it } from 'vitest'
import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { CalendarPage } from './CalendarPage'
import { GameClock } from '../components/layout/GameClock'
import { renderWithProviders } from '../test/render'
import { server } from '../test/setup'
import { state } from '../mocks/seed'
import type { ClubSchedule } from '../domain/types'

describe('CalendarPage (issue #133)', () => {
  it('mostra as semanas do clube com a competição de cada jogo', async () => {
    renderWithProviders(<CalendarPage />)

    expect(await screen.findByRole('heading', { level: 1, name: 'Calendário' })).toBeInTheDocument()
    const weeks = await screen.findByRole('list', { name: 'Semanas do calendário' })
    expect(within(weeks).getAllByRole('heading', { level: 2 })).toHaveLength(8)
    const first = screen.getByTestId('schedule-week-2026-1')
    expect(first).toHaveAttribute('aria-current', 'date')
    expect(within(first).getByRole('link', { name: /Série A · Rodada 1/ })).toHaveAttribute('href', '/competitions/bra-serie-a')
    expect(screen.getByTestId('schedule-current')).toHaveTextContent('Semana 1')
  })

  it('destaca a data FIFA vinda do backend', async () => {
    const schedule: ClubSchedule = {
      clubId: 1,
      current: state.currentRound.matchday,
      weeks: [{ year: 2026, week: 13, start: '2026-03-23', end: '2026-03-29', fifaWindow: true, matches: [] }],
    }
    server.use(http.get('/api/clubs/:id/schedule', () => HttpResponse.json(schedule)))
    renderWithProviders(<CalendarPage />)

    const week = await screen.findByTestId('schedule-week-2026-13')
    expect(within(week).getByText('Data FIFA')).toBeInTheDocument()
    expect(within(week).getByText('Sem jogo do clube.')).toBeInTheDocument()
  })
})

describe('GameClock (issue #133)', () => {
  it('mostra a data do jogo e o próximo compromisso', () => {
    const match = state.currentRound.matches.find((m) => m.homeClubId === 1 || m.awayClubId === 1)!
    renderWithProviders(
      <GameClock
        clubId={1}
        calendar={{ current: state.currentRound.matchday, season: 2026, nextMatch: { matchday: state.currentRound.matchday, match } }}
      />,
    )
    expect(screen.getByTestId('game-clock')).toHaveTextContent('Semana 1 · Fim de semana')
    expect(screen.getByTestId('game-clock-next')).toHaveTextContent(/Rodada 1 — contra/)
    expect(screen.getByRole('link', { name: 'Ver calendário' })).toHaveAttribute('href', '/calendar')
  })
})
