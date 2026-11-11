import { describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { Route, Routes } from 'react-router-dom'
import { AppLayout } from './AppLayout'
import type { TransferOffer } from '../../domain/types'
import { server } from '../../test/setup'
import { renderWithProviders } from '../../test/render'

const pendingOffer: TransferOffer = {
  id: 1,
  buyerClubId: 2,
  buyerClubName: 'Atlético Bonsucesso',
  sellerClubId: 1,
  sellerClubName: 'Goal Father FC',
  playerId: 10,
  playerName: 'Renato Silva',
  amount: 900_000_00,
  slotsLeft: 2,
  status: { type: 'Pending' },
}

function renderLayout() {
  return renderWithProviders(
    <Routes>
      <Route element={<AppLayout />}>
        <Route path="/dashboard" element={<p>tela clube</p>} />
        <Route path="/lineup" element={<p>tela escalação</p>} />
        <Route path="/welcome" element={<p>tela tutorial</p>} />
      </Route>
      <Route path="/login" element={<p>tela login</p>} />
    </Routes>,
    { initialEntries: ['/dashboard'] },
  )
}

const menuButton = () => screen.getByRole('button', { name: /Menu de navegação/ })
const userButton = () => screen.getByRole('button', { name: /Menu do usuário/ })

/**
 * Painel apontado pelo `aria-controls` do botão. O jsdom não carrega o Tailwind,
 * então a barra de desktop (`hidden lg:flex`) continua "visível" aqui: as buscas
 * de link precisam ficar restritas ao painel.
 */
function panelOf(button: HTMLElement): HTMLElement {
  const panel = document.getElementById(button.getAttribute('aria-controls') ?? '')
  expect(panel).not.toBeNull()
  return panel as HTMLElement
}

describe('AppLayout (issue #106)', () => {
  describe('menu mobile', () => {
    it('começa fechado e abre com aria-expanded e aria-controls corretos', async () => {
      const user = userEvent.setup()
      renderLayout()

      expect(menuButton()).toHaveAttribute('aria-expanded', 'false')
      expect(panelOf(menuButton())).not.toBeVisible()

      await user.click(menuButton())

      expect(menuButton()).toHaveAttribute('aria-expanded', 'true')
      const links = within(panelOf(menuButton())).getAllByRole('link').map((link) => link.textContent)
      expect(links).toEqual(['Clube', 'Escalação', 'Rodada', 'Mercado', 'Tabela', 'Copa', 'Histórico'])
    })

    it('fecha e navega ao escolher um link', async () => {
      const user = userEvent.setup()
      renderLayout()

      await user.click(menuButton())
      await user.click(within(panelOf(menuButton())).getByRole('link', { name: 'Escalação' }))

      expect(screen.getByText('tela escalação')).toBeInTheDocument()
      expect(menuButton()).toHaveAttribute('aria-expanded', 'false')
      expect(within(panelOf(menuButton())).queryByRole('link', { hidden: true })).toBeNull()
    })

    it('fecha com Esc e devolve o foco ao botão', async () => {
      const user = userEvent.setup()
      renderLayout()

      await user.click(menuButton())
      within(panelOf(menuButton())).getByRole('link', { name: 'Tabela' }).focus()
      await user.keyboard('{Escape}')

      expect(menuButton()).toHaveAttribute('aria-expanded', 'false')
      expect(menuButton()).toHaveFocus()
    })

    it('fecha ao clicar fora', async () => {
      const user = userEvent.setup()
      renderLayout()

      await user.click(menuButton())
      await user.click(screen.getByText('tela clube'))

      expect(menuButton()).toHaveAttribute('aria-expanded', 'false')
    })
  })

  describe('badge de propostas', () => {
    it('aparece no botão de menu, com o menu fechado, e no item Clube', async () => {
      server.use(http.get('/api/clubs/:id/offers', () => HttpResponse.json([pendingOffer])))
      const user = userEvent.setup()
      renderLayout()

      await waitFor(() => expect(menuButton()).toHaveAccessibleName(/1 proposta/))
      expect(within(menuButton()).getByText('1')).toBeInTheDocument()

      await user.click(menuButton())
      const clube = within(panelOf(menuButton())).getByRole('link', { name: /^Clube/ })
      expect(within(clube).getByTitle(/1 proposta/)).toHaveTextContent('1')
    })

    it('não aparece sem propostas pendentes', async () => {
      server.use(http.get('/api/clubs/:id/offers', () => HttpResponse.json([])))
      renderLayout()

      await screen.findByText('tela clube')
      expect(menuButton()).toHaveAccessibleName('Menu de navegação')
      expect(screen.queryByTitle(/proposta/)).toBeNull()
    })
  })

  describe('menu de usuário', () => {
    it('agrupa nome, Tutorial e Sair', async () => {
      const user = userEvent.setup()
      renderLayout()

      expect(userButton()).toHaveAttribute('aria-expanded', 'false')
      await user.click(userButton())

      expect(userButton()).toHaveAttribute('aria-expanded', 'true')
      const panel = within(panelOf(userButton()))
      expect(panel.getByText('tester')).toBeInTheDocument()
      expect(panel.getByRole('link', { name: 'Tutorial' })).toHaveAttribute('href', '/welcome')
      expect(panel.getByRole('button', { name: 'Sair' })).toBeInTheDocument()
    })

    it('Tutorial navega e fecha o menu', async () => {
      const user = userEvent.setup()
      renderLayout()

      await user.click(userButton())
      await user.click(within(panelOf(userButton())).getByRole('link', { name: 'Tutorial' }))

      expect(screen.getByText('tela tutorial')).toBeInTheDocument()
      expect(userButton()).toHaveAttribute('aria-expanded', 'false')
    })

    it('Sair encerra a sessão e leva ao login', async () => {
      const user = userEvent.setup()
      renderLayout()

      await user.click(userButton())
      await user.click(within(panelOf(userButton())).getByRole('button', { name: 'Sair' }))

      expect(screen.getByText('tela login')).toBeInTheDocument()
    })
  })

  it('abrir um menu fecha o outro', async () => {
    const user = userEvent.setup()
    renderLayout()

    await user.click(menuButton())
    await user.click(userButton())

    expect(menuButton()).toHaveAttribute('aria-expanded', 'false')
    expect(userButton()).toHaveAttribute('aria-expanded', 'true')
  })
})
