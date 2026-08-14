import { describe, expect, it } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { DashboardPage } from './DashboardPage'
import { renderWithProviders } from '../test/render'

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
})
