import { describe, expect, it } from 'vitest'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { CompetitionPage } from './CompetitionPage'
import { WorldPage } from './WorldPage'
import { renderWithProviders } from '../test/render'

const renderAt = (path: string) =>
  renderWithProviders(
    <Routes>
      <Route path="/world" element={<WorldPage />} />
      <Route path="/competitions/:id/:tab?" element={<CompetitionPage />} />
    </Routes>,
    { initialEntries: [path] },
  )

const tabs = () => screen.getByRole('navigation', { name: 'Seções da competição' })

describe('WorldPage (issue #133)', () => {
  it('lista países e competições, com a liga do técnico marcada', async () => {
    renderAt('/world')

    const brazil = await screen.findByTestId('world-country-BRA')
    expect(within(brazil).getByRole('heading', { name: /Brasil/ })).toBeInTheDocument()
    const serieA = within(brazil).getByRole('link', { name: /Campeonato Brasileiro Série A/ })
    expect(serieA).toHaveAttribute('href', '/competitions/bra-serie-a')
    expect(within(serieA).getByText('sua liga')).toBeInTheDocument()
    expect(within(brazil).getByRole('link', { name: /Copa do Brasil/ })).toHaveTextContent(/Mata-mata/)
    expect(screen.getByTestId('world-country-ENG')).toHaveTextContent('Premier League')
  })
})

describe('CompetitionPage (issue #133)', () => {
  it('liga abre na Tabela, com faixas e critérios de desempate do backend', async () => {
    renderAt('/competitions/bra-serie-a')

    expect(await screen.findByRole('heading', { level: 1, name: 'Campeonato Brasileiro Série A' })).toBeInTheDocument()
    expect(within(tabs()).getByRole('link', { name: 'Tabela' })).toHaveAttribute('aria-current', 'page')
    expect(within(tabs()).queryByRole('link', { name: 'Chave' })).not.toBeInTheDocument()
    expect(await screen.findByRole('link', { name: 'Flamengo' })).toBeInTheDocument()
    expect(screen.getByTestId('tiebreakers')).toHaveTextContent('Pontos › Vitórias › Saldo de gols')
  })

  it('Copa do Brasil (mata-mata) abre na Chave', async () => {
    renderAt('/competitions/bra-copa-do-brasil')

    expect(await screen.findByRole('heading', { level: 1, name: 'Copa do Brasil' })).toBeInTheDocument()
    expect(within(tabs()).getByRole('link', { name: 'Chave' })).toHaveAttribute('aria-current', 'page')
    expect(within(tabs()).queryByRole('link', { name: 'Tabela' })).not.toBeInTheDocument()
    expect(await screen.findByRole('region', { name: 'Chave da copa' })).toBeInTheDocument()
    expect(screen.getByTestId('cup-phase-primeira-fase')).toBeInTheDocument()
  })

  it('aba Jogos mostra o encaixe em aberto e anda pelos demais', async () => {
    const user = userEvent.setup()
    renderAt('/competitions/bra-serie-a/jogos')

    expect(await screen.findByRole('heading', { level: 2, name: 'Rodada 1' })).toBeInTheDocument()
    const list = screen.getByTestId('competition-matches')
    expect(within(list).getAllByRole('listitem')).toHaveLength(3)
    expect(within(list).getByRole('listitem', { current: true })).toHaveTextContent('Flamengo')
    expect(screen.getByRole('button', { name: 'Encaixe anterior' })).toBeDisabled()

    await user.click(screen.getByRole('button', { name: 'Próximo encaixe' }))
    expect(screen.getByRole('heading', { level: 2, name: 'Rodada 2' })).toBeInTheDocument()
  })

  it('aba Artilharia sem gols mostra o estado vazio', async () => {
    renderAt('/competitions/bra-serie-a/artilharia')
    expect(await screen.findByText(/Ninguém marcou ainda/)).toBeInTheDocument()
  })

  it('competição fora do mundo explica e leva ao Mundo', async () => {
    renderAt('/competitions/xyz-liga')
    expect(await screen.findByText(/não existe no seu mundo/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /competições do mundo/ })).toHaveAttribute('href', '/world')
  })
})
