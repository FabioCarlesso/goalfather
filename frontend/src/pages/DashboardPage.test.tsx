import { afterEach, describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderDashboard } from '../test/renderDashboard'
import { server } from '../test/setup'
import { http, HttpResponse } from 'msw'
import { state } from '../mocks/seed'
import type { TransferOffer, TransferOfferResult } from '../domain/types'

const clubHeading = () => screen.findByRole('heading', { level: 1, name: 'Goal Father FC' })
const tabs = () => within(screen.getByRole('navigation', { name: 'Seções do clube' }))

describe('DashboardPage', () => {
  // A venda troca o clube e o mercado no estado do mock (por substituição, não
  // mutação): devolver as referências originais isola os testes seguintes.
  const seedClub = state.clubs[1]
  const seedMarket = state.market
  afterEach(() => {
    if (seedClub) state.clubs[1] = seedClub
    state.market = seedMarket
    server.events.removeAllListeners()
  })

  it('/dashboard abre a visão geral, com os stats e sem o ID do clube (issue #107)', async () => {
    renderDashboard('/dashboard')

    expect(screen.getByText(/carregando clube/i)).toBeInTheDocument()
    await clubHeading()

    // O `<Navigate>` da rota índice redireciona num efeito, logo após o render.
    await waitFor(() =>
      expect(tabs().getByRole('link', { name: 'Visão geral' })).toHaveAttribute('aria-current', 'page'),
    )
    expect(screen.getByText('Caixa')).toBeInTheDocument()
    expect(screen.getByText('Capacidade estádio')).toBeInTheDocument()
    expect(screen.getByText('11 jogadores')).toBeInTheDocument()
    expect(screen.queryByText(/ID #/)).not.toBeInTheDocument()
  })

  it('as 4 abas levam ao elenco, finanças e departamento (issue #107)', async () => {
    const user = userEvent.setup()
    renderDashboard('/dashboard/visao-geral')
    await clubHeading()

    expect(tabs().getAllByRole('link').map((l) => l.textContent)).toEqual([
      'Visão geral', 'Elenco', 'Finanças', 'Departamento',
    ])

    await user.click(tabs().getByRole('link', { name: 'Elenco' }))
    expect(await screen.findByRole('heading', { name: 'Elenco' })).toBeInTheDocument()
    expect(screen.queryByText('Caixa')).not.toBeInTheDocument()

    await user.click(tabs().getByRole('link', { name: 'Finanças' }))
    expect(screen.getByRole('heading', { name: 'Preço do ingresso' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Ampliar estádio' })).toBeInTheDocument()

    await user.click(tabs().getByRole('link', { name: 'Departamento' }))
    expect(screen.getByRole('heading', { name: 'Treino da semana' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Departamento médico' })).toBeInTheDocument()
  })

  it('aba desconhecida cai na visão geral', async () => {
    renderDashboard('/dashboard/nao-existe')
    await clubHeading()
    await waitFor(() =>
      expect(tabs().getByRole('link', { name: 'Visão geral' })).toHaveAttribute('aria-current', 'page'),
    )
  })

  it('lista o elenco com posições abreviadas e forma física (issues #54, #107)', async () => {
    renderDashboard('/dashboard/elenco')
    await clubHeading()

    expect(screen.getByText('Marcos Figueiredo')).toBeInTheDocument()
    expect(screen.getByText('Renato Silva')).toBeInTheDocument()
    // Elenco do seed começa inteiro em 100% de forma.
    expect(screen.getAllByTitle('Forma física: 100%')).toHaveLength(11)
    // Posição em português, abreviada, com o nome por extenso no title.
    expect(screen.getAllByTitle('Goleiro')).toHaveLength(1)
    expect(screen.getAllByTitle('Goleiro')[0]).toHaveTextContent('GL')
    expect(screen.queryByText(/^GK$/)).not.toBeInTheDocument()
  })

  it('a venda abre um modal; Cancelar não vende (issue #107)', async () => {
    const user = userEvent.setup()
    let sellCalls = 0
    server.events.on('request:start', ({ request }) => {
      if (request.url.endsWith('/api/market/sell')) sellCalls++
    })
    renderDashboard('/dashboard/elenco')
    await clubHeading()

    await user.click(screen.getByRole('button', { name: 'Vender Marcos Figueiredo' }))

    const dialog = screen.getByRole('dialog', { name: 'Vender Marcos Figueiredo?' })
    expect(within(dialog).getByText('Goleiro (GL)')).toBeInTheDocument()
    expect(within(dialog).getByText('32 anos')).toBeInTheDocument()
    expect(within(dialog).getByText('78')).toBeInTheDocument()

    await user.click(within(dialog).getByRole('button', { name: 'Cancelar' }))

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByText('Marcos Figueiredo')).toBeInTheDocument()
    expect(sellCalls).toBe(0)
  })

  it('confirmar a venda tira o jogador do elenco e mostra o resultado', async () => {
    const user = userEvent.setup()
    renderDashboard('/dashboard/elenco')
    await clubHeading()

    await user.click(screen.getByRole('button', { name: 'Vender Renato Silva' }))
    await user.click(screen.getByRole('button', { name: 'Confirmar venda' }))

    expect(await screen.findByText('Venda concluída')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByText('Renato Silva')).not.toBeInTheDocument())

    await user.click(screen.getByRole('button', { name: 'Dispensar aviso' }))
    expect(screen.queryByText('Venda concluída')).not.toBeInTheDocument()
  })

  it('mostra o painel do departamento médico (issue #54)', async () => {
    renderDashboard('/dashboard/departamento')
    await clubHeading()

    expect(screen.getByRole('heading', { name: 'Departamento médico' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Tratar elenco' })).toBeEnabled()
    expect(screen.getByText(/0 lesionados · 0 desgastados/)).toBeInTheDocument()
  })

  it('mostra o painel da diretoria com a meta da temporada (issue #62)', async () => {
    renderDashboard('/dashboard/visao-geral')

    expect(await screen.findByRole('heading', { name: 'Diretoria' })).toBeInTheDocument()
    // Clube 1 tem o elenco mais forte da elite no seed: a diretoria cobra o título.
    expect(screen.getByText('Conquistar o título')).toBeInTheDocument()
    expect(screen.getByText('temporada ainda não começou')).toBeInTheDocument()
    expect(screen.getByRole('meter', { name: 'Confiança da diretoria' })).toHaveAttribute('aria-valuenow', '50')
  })

  it('erro na diretoria não derruba a visão geral (issue #62)', async () => {
    server.use(http.get('/api/clubs/1/board', () => HttpResponse.json({}, { status: 500 })))
    renderDashboard('/dashboard/visao-geral')
    await clubHeading()

    expect(screen.getByText('11 jogadores')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Diretoria' })).not.toBeInTheDocument()
  })

  it('escolhe o foco de treino da semana (issue #58)', async () => {
    const user = userEvent.setup()
    renderDashboard('/dashboard/departamento')
    await clubHeading()

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
    renderDashboard('/dashboard/financas')
    await clubHeading()

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
    renderDashboard('/dashboard/financas')
    await clubHeading()

    const input = screen.getByLabelText(/preço \(r\$\)/i)
    await user.clear(input)
    await user.type(input, '500')

    expect(screen.getByText(/informe um valor entre/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Definir preço' })).toBeDisabled()
  })

  it('trata o elenco e debita o caixa via endpoint do mock', async () => {
    const user = userEvent.setup()
    renderDashboard('/dashboard/departamento')
    await clubHeading()

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
    // `/dashboard` puro: é onde o badge de propostas do cabeçalho leva.
    renderDashboard('/dashboard')

    expect(await screen.findByRole('heading', { name: /Propostas/ })).toHaveTextContent('1 nova')
    expect(screen.getByText('Atlético Bonsucesso')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Recusar' }))

    expect(await screen.findByText('Recusada')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Aceitar' })).not.toBeInTheDocument()
  })
})
