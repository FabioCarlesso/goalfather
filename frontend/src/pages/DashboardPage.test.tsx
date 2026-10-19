import { describe, expect, it } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { DashboardPage } from './DashboardPage'
import { renderWithProviders } from '../test/render'
import { server } from '../test/setup'
import { http, HttpResponse } from 'msw'
import type { TransferOffer, TransferOfferResult } from '../domain/types'

describe('DashboardPage', () => {
  it('renderiza o clube e elenco do mock', async () => {
    renderWithProviders(<DashboardPage />)

    expect(screen.getByText(/carregando clube/i)).toBeInTheDocument()

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Goal Father FC' })).toBeInTheDocument()
    })

    // Stat cards
    expect(screen.getByText('Caixa')).toBeInTheDocument()
    expect(screen.getByText('Capacidade estádio')).toBeInTheDocument()
    expect(screen.getByText('11 jogadores')).toBeInTheDocument()
    // "Elenco" aparece tanto no stat card quanto como heading da secao
    expect(screen.getAllByText('Elenco')).toHaveLength(2)

    // Alguns jogadores conhecidos do seed
    expect(screen.getByText('Marcos Figueiredo')).toBeInTheDocument()
    expect(screen.getByText('Renato Silva')).toBeInTheDocument()
  })

  it('mostra forma física do elenco e o painel do departamento médico (issue #54)', async () => {
    renderWithProviders(<DashboardPage />)

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Goal Father FC' })).toBeInTheDocument()
    })

    // Elenco do seed começa inteiro em 100% de forma.
    expect(screen.getAllByTitle('Forma física: 100%')).toHaveLength(11)

    expect(screen.getByRole('heading', { name: 'Departamento médico' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Tratar elenco' })).toBeEnabled()
    expect(screen.getByText(/0 lesionados · 0 desgastados/)).toBeInTheDocument()
  })

  it('mostra o painel da diretoria com a meta da temporada (issue #62)', async () => {
    renderWithProviders(<DashboardPage />)

    expect(await screen.findByRole('heading', { name: 'Diretoria' })).toBeInTheDocument()
    // Clube 1 tem o elenco mais forte da elite no seed: a diretoria cobra o título.
    expect(screen.getByText('Conquistar o título')).toBeInTheDocument()
    expect(screen.getByText('temporada ainda não começou')).toBeInTheDocument()
    expect(screen.getByRole('meter', { name: 'Confiança da diretoria' })).toHaveAttribute('aria-valuenow', '50')
  })

  it('escolhe o foco de treino da semana (issue #58)', async () => {
    const user = userEvent.setup()
    renderWithProviders(<DashboardPage />)

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Goal Father FC' })).toBeInTheDocument()
    })

    // Clube do seed começa poupando o elenco — o default do backend.
    const rest = screen.getByRole('button', { name: 'Descanso' })
    expect(rest).toHaveAttribute('aria-pressed', 'true')

    await user.click(screen.getByRole('button', { name: 'Ataque' }))

    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Ataque' })).toHaveAttribute('aria-pressed', 'true')
    })
    expect(screen.getByRole('button', { name: 'Descanso' })).toHaveAttribute('aria-pressed', 'false')
    expect(screen.getByText(/evoluir a finalização/i)).toBeInTheDocument()
  })

  it('define o preço do ingresso (issue #59)', async () => {
    const user = userEvent.setup()
    renderWithProviders(<DashboardPage />)

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Goal Father FC' })).toBeInTheDocument()
    })

    // Clube do seed começa no default do backend (R$ 50).
    expect(screen.getByText(/em vigor: R\$\s?50/)).toBeInTheDocument()

    const input = screen.getByLabelText(/preço \(r\$\)/i)
    await user.clear(input)
    await user.type(input, '90')
    await user.click(screen.getByRole('button', { name: 'Definir preço' }))

    await waitFor(() => {
      expect(screen.getByText(/em vigor: R\$\s?90/)).toBeInTheDocument()
    })
    expect(screen.getByText('Preço atualizado ✓')).toBeInTheDocument()
  })

  it('recusa preço fora da faixa antes de mandar (issue #59)', async () => {
    const user = userEvent.setup()
    renderWithProviders(<DashboardPage />)

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Goal Father FC' })).toBeInTheDocument()
    })

    const input = screen.getByLabelText(/preço \(r\$\)/i)
    await user.clear(input)
    await user.type(input, '500')

    expect(screen.getByText(/informe um valor entre/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Definir preço' })).toBeDisabled()
  })

  it('trata o elenco e debita o caixa via endpoint do mock', async () => {
    const user = userEvent.setup()
    renderWithProviders(<DashboardPage />)

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Goal Father FC' })).toBeInTheDocument()
    })

    await user.click(screen.getByRole('button', { name: 'Tratar elenco' }))

    await waitFor(() => {
      expect(screen.getByText('Elenco tratado ✓')).toBeInTheDocument()
    })
  })

  it('lista a proposta recebida e a recusa (issue #63)', async () => {
    const user = userEvent.setup()
    const offer: TransferOffer = {
      id: 41,
      buyerClubId: 2,
      buyerClubName: 'Atlético Bonsucesso',
      sellerClubId: 1,
      sellerClubName: 'Goal Father FC',
      playerId: 10,
      playerName: 'Renato Silva',
      amount: 900_000_00,
      roundsLeft: 3,
      status: { type: 'Pending' },
    }
    let current = offer
    server.use(
      http.get('/api/clubs/1/offers', () => HttpResponse.json([current])),
      http.post('/api/offers/41/reject', () => {
        current = { ...offer, status: { type: 'Rejected' } }
        return HttpResponse.json({ type: 'Success', offer: current } satisfies TransferOfferResult)
      }),
    )
    renderWithProviders(<DashboardPage />)

    expect(await screen.findByRole('heading', { name: /Propostas/ })).toHaveTextContent('1 nova')
    expect(screen.getByText('Atlético Bonsucesso')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Recusar' }))

    expect(await screen.findByText('Recusada')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Aceitar' })).not.toBeInTheDocument()
  })
})
