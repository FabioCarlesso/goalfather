import { afterEach, describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { SelectClubPage } from './SelectClubPage'
import { renderWithProviders } from '../test/render'
import { server } from '../test/setup'
import { auth, clubOwners, persistMockAuth } from '../mocks/seed'
import { clearToken, setToken } from '../api/token'
import type { WorldCountry } from '../domain/types'

const noClubAuth = {
  user: { id: 9, username: 'novato', clubId: null },
  status: 'authenticated' as const,
}

const render = (initialEntries = ['/select-club']) =>
  renderWithProviders(<SelectClubPage />, { auth: noClubAuth, initialEntries })

const currentStep = () =>
  within(screen.getByRole('list', { name: 'Etapas da escolha' })).getByRole('listitem', { current: 'step' })

describe('SelectClubPage — onboarding país → liga → clube (issue #131)', () => {
  afterEach(() => {
    // O claim grava a posse no estado do mock; devolve os clubes ao mercado.
    for (const id of Object.keys(clubOwners)) clubOwners[Number(id)] = null
    persistMockAuth()
  })

  it('passo 1 lista os países com bandeira, nº de ligas e de clubes livres', async () => {
    render()

    expect(screen.getByRole('heading', { level: 1, name: 'Escolha seu clube' })).toBeInTheDocument()
    expect(screen.getByText(/carregando países/i)).toBeInTheDocument()

    const brasil = await screen.findByRole('button', { name: /Brasil/ })
    expect(brasil).toHaveTextContent('🇧🇷Brasil2 ligas · 12 clubes livres')
    expect(screen.getByRole('button', { name: /Inglaterra/ })).toHaveTextContent('1 liga · 4 clubes livres')
    expect(screen.getByRole('heading', { level: 2, name: 'Escolha o país' })).toBeInTheDocument()
    expect(currentStep()).toHaveTextContent('1. País')
  })

  it('país → liga → clube, com volta entre os passos', async () => {
    const user = userEvent.setup()
    render()

    await user.click(await screen.findByRole('button', { name: /Brasil/ }))
    expect(currentStep()).toHaveTextContent('2. Liga')
    const heading = screen.getByRole('heading', { level: 2, name: /Escolha a liga/ })
    expect(heading).toHaveTextContent('Brasil')
    // O foco vai para o título do passo novo (leitor de tela anuncia a troca).
    expect(heading).toHaveFocus()

    const leagues = within(screen.getByRole('list', { name: 'Ligas' })).getAllByRole('button')
    expect(leagues.map((b) => b.textContent)).toEqual([
      'Campeonato Brasileiro Série A1ª divisão · Temporada 20266 de 6 clubes livres',
      'Campeonato Brasileiro Série B2ª divisão · Temporada 20266 de 6 clubes livres',
    ])

    await user.click(leagues[1]!)
    expect(currentStep()).toHaveTextContent('3. Clube')
    expect(screen.getByRole('heading', { level: 2 })).toHaveTextContent('Escolha o clube — Campeonato Brasileiro Série B')
    const clubs = await screen.findByRole('list', { name: /Clubes livres/ })
    const cards = within(clubs).getAllByRole('article')
    expect(cards).toHaveLength(6)
    // Ordem do servidor: força média decrescente.
    expect(cards[0]).toHaveAccessibleName('Fortaleza')
    expect(within(cards[0]!).getByText(/Brasil · Campeonato Brasileiro Série B/)).toBeInTheDocument()
    expect(within(cards[0]!).getByRole('button', { name: 'Treinar este clube' })).toBeEnabled()

    await user.click(screen.getByRole('button', { name: /Voltar às ligas/ }))
    expect(currentStep()).toHaveTextContent('2. Liga')
    await user.click(screen.getByRole('button', { name: /Voltar aos países/ }))
    expect(currentStep()).toHaveTextContent('1. País')
  })

  it('a escolha vive na URL: abrir com país e liga cai direto nos clubes', async () => {
    render(['/select-club?country=ENG&league=eng-premier-league'])

    expect(await screen.findByRole('heading', { level: 2 })).toHaveTextContent('Escolha o clube — Premier League')
    expect(screen.getByText(/Inglaterra · 1ª divisão · Temporada 2026\/27/)).toBeInTheDocument()
    const cards = within(await screen.findByRole('list', { name: /Clubes livres/ })).getAllByRole('article')
    expect(cards.map((c) => c.getAttribute('aria-label'))).toEqual(['Liverpool', 'Manchester City', 'Arsenal', 'Chelsea'])
  })

  it('liga inexistente na URL volta para as ligas do país', async () => {
    render(['/select-club?country=BRA&league=nao-existe'])

    expect(await screen.findByRole('heading', { level: 2, name: /Escolha a liga/ })).toBeInTheDocument()
  })

  it('reivindica um clube da Premier League', async () => {
    const user = userEvent.setup()
    // O claim do mock exige o usuário cadastrado e o token dele no header.
    auth.users.push({ id: 9, username: 'novato', password: 'secret123', clubId: null })
    setToken('mock-token-9')
    render(['/select-club?country=ENG&league=eng-premier-league'])

    const arsenal = await screen.findByRole('article', { name: 'Arsenal' })
    await user.click(within(arsenal).getByRole('button', { name: 'Treinar este clube' }))

    await waitFor(() => expect(clubOwners[15]).toBe(9))
    auth.users.splice(auth.users.findIndex((u) => u.id === 9), 1)
    clearToken()
  })

  it('liga sem clube livre explica e oferece outra liga', async () => {
    const user = userEvent.setup()
    server.use(http.get('/api/clubs/available', () => HttpResponse.json([])))
    render(['/select-club?country=BRA&league=bra-serie-a'])

    expect(await screen.findByText(/Todos os clubes desta liga já têm técnico/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Escolher outra liga' }))
    expect(screen.getByRole('heading', { level: 2, name: /Escolha a liga/ })).toBeInTheDocument()
  })

  it('liga e país sem clube livre são sinalizados nos cards', async () => {
    const full: WorldCountry[] = [{
      country: { code: 'ENG', name: 'Inglaterra', confederation: 'UEFA', flag: '🏴' },
      leagues: [{ id: 'eng-premier-league', name: 'Premier League', tier: 1, clubCount: 4, availableClubCount: 0, seasonLabel: '2026/27' }],
    }]
    server.use(http.get('/api/world/countries', () => HttpResponse.json(full)))
    const user = userEvent.setup()
    render()

    await user.click(await screen.findByRole('button', { name: /Inglaterra.*nenhum clube livre/ }))
    expect(screen.getByRole('button', { name: /Premier League/ })).toHaveTextContent('Nenhum clube livre')
  })

  it('mundo sem país jogável mostra o estado vazio', async () => {
    server.use(http.get('/api/world/countries', () => HttpResponse.json([])))
    render()

    expect(await screen.findByText(/Nenhum país com liga jogável/)).toBeInTheDocument()
  })

  it('409 de clube já reivindicado aparece na tela', async () => {
    const user = userEvent.setup()
    server.use(
      http.post('/api/clubs/:id/claim', () =>
        HttpResponse.json({ code: 'CLUB_ALREADY_CLAIMED', message: 'Clube 1 já foi reivindicado' }, { status: 409 }),
      ),
    )
    render(['/select-club?country=BRA&league=bra-serie-a'])

    const flamengo = await screen.findByRole('article', { name: 'Flamengo' })
    await user.click(within(flamengo).getByRole('button', { name: 'Treinar este clube' }))

    expect(await screen.findByRole('alert')).toBeInTheDocument()
  })
})
