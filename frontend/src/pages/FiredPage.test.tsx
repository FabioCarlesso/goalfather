import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { FiredPage } from './FiredPage'
import { renderWithProviders } from '../test/render'
import type { AuthUser } from '../domain/types'

const fired: AuthUser = {
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

function renderFired(user: AuthUser) {
  return renderWithProviders(
    <Routes>
      <Route path="/fired" element={<FiredPage />} />
      <Route path="/select-club" element={<h1>Escolha seu clube</h1>} />
      <Route path="/" element={<h1>Início</h1>} />
    </Routes>,
    { initialEntries: ['/fired'], auth: { user, status: 'authenticated' } },
  )
}

describe('FiredPage (issue #62)', () => {
  it('conta a demissão e oferece recomeçar em outro clube', async () => {
    const user = userEvent.setup()
    renderFired(fired)

    expect(screen.getByRole('heading', { name: 'Você foi demitido' })).toBeInTheDocument()
    expect(screen.getByText('Goal Father FC')).toBeInTheDocument()
    expect(screen.getByText('Conquistar o título')).toBeInTheDocument()
    expect(screen.getByText('6º')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Recomeçar em outro clube' }))

    expect(await screen.findByRole('heading', { name: 'Escolha seu clube' })).toBeInTheDocument()
  })

  it('sem demissão não há game over: volta para o início', () => {
    renderFired({ id: 1, username: 'tester', clubId: 1 })

    expect(screen.getByRole('heading', { name: 'Início' })).toBeInTheDocument()
  })
})
