import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { BoardReviewCard } from './RoundPage'
import { useAuth } from '../auth/AuthContext'
import { renderWithProviders } from '../test/render'
import { server } from '../test/setup'
import type { AuthUser, BoardReview } from '../domain/types'

const firedReview: BoardReview = {
  clubId: 1,
  clubName: 'Goal Father FC',
  season: 2026,
  objective: 'TITLE',
  targetPosition: 1,
  finalPosition: 6,
  confidence: 10,
  verdict: { type: 'Fired' },
}

const dismissedUser: AuthUser = {
  id: 1,
  username: 'tester',
  clubId: null,
  dismissal: {
    clubId: 1,
    clubName: 'Goal Father FC',
    season: 2026,
    objective: 'TITLE',
    targetPosition: 1,
    finalPosition: 6,
  },
}

/** Espelho do `AuthUser` corrente — é ele que os guardas de rota leem. */
function ClubProbe() {
  const { user } = useAuth()
  return <p>clube: {user?.clubId ?? 'nenhum'}</p>
}

function renderCard(review: BoardReview) {
  return renderWithProviders(
    <>
      <BoardReviewCard review={review} />
      <ClubProbe />
    </>,
  )
}

describe('BoardReviewCard (issue #62)', () => {
  it('demissão rebusca /me sozinha, sem esperar clique', async () => {
    server.use(http.get('/api/auth/me', () => HttpResponse.json(dismissedUser)))

    renderCard(firedReview)

    expect(screen.getByText('clube: 1')).toBeInTheDocument()
    expect(await screen.findByText('clube: nenhum')).toBeInTheDocument()
  })

  it('falha no /me avisa e permite tentar de novo', async () => {
    let calls = 0
    server.use(
      http.get('/api/auth/me', () => {
        calls += 1
        return calls === 1
          ? HttpResponse.json({ code: 'INTERNAL', message: 'Falhou' }, { status: 500 })
          : HttpResponse.json(dismissedUser)
      }),
    )
    const user = userEvent.setup()

    renderCard(firedReview)

    expect(await screen.findByRole('alert')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Tentar de novo' }))
    expect(await screen.findByText('clube: nenhum')).toBeInTheDocument()
  })

  it('meta perdida sem demissão não mexe no usuário', async () => {
    let calls = 0
    server.use(http.get('/api/auth/me', () => {
      calls += 1
      return HttpResponse.json(dismissedUser)
    }))

    renderCard({ ...firedReview, verdict: { type: 'TargetMissed' } })

    expect(screen.getByText(/Diretoria:/)).toBeInTheDocument()
    expect(screen.getByText('clube: 1')).toBeInTheDocument()
    expect(calls).toBe(0)
  })
})
