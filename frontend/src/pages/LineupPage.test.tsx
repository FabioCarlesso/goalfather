import { afterEach, describe, expect, it } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import { LineupPage } from './LineupPage'
import { renderWithProviders } from '../test/render'
import { state } from '../mocks/seed'

// Escalação efetiva (issue #87): a tela abre com o time que a engine põe em
// campo — vindo de `club.effectiveLineup` —, não com a escalação salva crua.
describe('LineupPage', () => {
  const original = state.clubs[1]!
  afterEach(() => {
    state.clubs[1] = original
  })

  it('clube sem escalação salva abre 11/11 com o aviso de escalação automática', async () => {
    renderWithProviders(<LineupPage />)

    await waitFor(() => {
      expect(screen.getByText('11/11 titulares')).toBeInTheDocument()
    })
    expect(screen.getByRole('status')).toHaveTextContent(/Escalação automática/)
    expect(screen.getByRole('button', { name: 'Salvar escalação' })).toBeEnabled()
    // Os titulares automáticos aparecem selecionados nos slots.
    expect(screen.getByDisplayValue(/Marcos Figueiredo/)).toBeInTheDocument()
  })

  it('escalação salva com lesionado abre com o reserva no lugar e avisa o ajuste', async () => {
    const reserve = { ...original.squad[1]!, id: 99, name: 'Reserva Apto' }
    const injuredId = original.squad[1]!.id
    state.clubs[1] = {
      ...original,
      squad: [
        ...original.squad.map((p) =>
          p.id === injuredId ? { ...p, availability: { type: 'Injured' as const, roundsOut: 2 } } : p,
        ),
        reserve,
      ],
      lineup: { formation: '4-4-2', playerIds: original.squad.map((p) => p.id), posture: 'DEFENSIVE' },
    }

    renderWithProviders(<LineupPage />)

    await waitFor(() => {
      expect(screen.getByText('11/11 titulares')).toBeInTheDocument()
    })
    expect(screen.getByRole('status')).toHaveTextContent(/Escalação ajustada/)
    expect(screen.getByDisplayValue(/Reserva Apto/)).toBeInTheDocument()
    expect(screen.getByLabelText('Postura')).toHaveValue('DEFENSIVE')
  })

  it('escalação salva intacta não mostra aviso', async () => {
    state.clubs[1] = {
      ...original,
      lineup: { formation: '4-4-2', playerIds: original.squad.map((p) => p.id), posture: 'BALANCED' },
    }

    renderWithProviders(<LineupPage />)

    await waitFor(() => {
      expect(screen.getByText('11/11 titulares')).toBeInTheDocument()
    })
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })
})
