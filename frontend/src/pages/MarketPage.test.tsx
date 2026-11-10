import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { MutationCache, QueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { useLocation } from 'react-router-dom'
import { MarketPage } from './MarketPage'
import { errorMessage } from '../api/errorMessages'
import { marketEntries, state } from '../mocks/seed'
import { formatMoney } from '../domain/formatters'
import { renderWithProviders } from '../test/render'
import { server } from '../test/setup'
import type { TransferResult } from '../domain/types'

// O <Toaster /> vive no App, fora desta árvore; o que interessa aqui é o que foi
// disparado. Mock (e não Toaster real) porque a store de toasts é global ao módulo
// e vazaria mensagens de um teste para o outro.
vi.mock('react-hot-toast', () => ({
  default: { success: vi.fn(), error: vi.fn() },
}))

const VIDIGAL = 'Lucas Vidigal'   // AT, OVR 81, 25a, R$ 450 mil, salário 32 mil
const PACHECO = 'André Pacheco'   // MC, OVR 77, 24a, R$ 220 mil, salário 23 mil
const APARICIO = 'Bruno Aparício' // ZG, OVR 79, 27a, R$ 300 mil, salário 25 mil
const RAMIRES = 'Eduardo Ramires' // GL, OVR 76, 28a, R$ 180 mil, salário 21 mil

function LocationProbe() {
  const { search } = useLocation()
  return <output data-testid="search">{search}</output>
}

const renderMarket = (initialEntry = '/market', queryClient?: QueryClient) =>
  renderWithProviders(
    <>
      <MarketPage />
      <LocationProbe />
    </>,
    { initialEntries: [initialEntry], queryClient },
  )

const shownNames = () =>
  screen
    .getAllByRole('button', { name: /^Comprar / })
    .map((button) => button.getAttribute('aria-label')?.replace('Comprar ', ''))

const buyButton = (name: string) => screen.getByRole('button', { name: `Comprar ${name}` })

const rowOf = (name: string) => {
  const row = buyButton(name).closest('li')
  if (!row) throw new Error(`linha de ${name} não encontrada`)
  return row
}

const search = () => screen.getByTestId('search').textContent

// O RTL normaliza o espaço não separável do texto da tela, mas não o do texto buscado.
const money = (cents: number) => formatMoney(cents).replace(/\s/g, ' ')

describe('MarketPage (issue #111)', () => {
  const originalClub = state.clubs[1]!

  beforeEach(() => {
    vi.mocked(toast.success).mockClear()
    vi.mocked(toast.error).mockClear()
  })

  afterEach(() => {
    state.market = [...marketEntries]
    state.clubs[1] = originalClub
  })

  describe('filtro por posição', () => {
    it('mostra só os jogadores da posição, com a posição abreviada', async () => {
      const user = userEvent.setup()
      renderMarket()
      await screen.findByText(VIDIGAL)

      await user.click(screen.getByRole('button', { name: 'AT' }))

      await waitFor(() => expect(shownNames()).toEqual([VIDIGAL]))
      expect(screen.getByRole('button', { name: 'AT' })).toHaveAttribute('aria-pressed', 'true')
      expect(screen.getByRole('button', { name: 'Todos' })).toHaveAttribute('aria-pressed', 'false')
      expect(within(rowOf(VIDIGAL)).getByText('AT · 25a · OVR 81')).toBeInTheDocument()
      expect(search()).toBe('?pos=FW')

      await user.click(screen.getByRole('button', { name: 'Todos' }))
      await waitFor(() => expect(shownNames()).toHaveLength(4))
      expect(search()).toBe('')
    })
  })

  describe('ordenação', () => {
    it.each([
      ['Preço', [RAMIRES, PACHECO, APARICIO, VIDIGAL]],
      ['OVR', [RAMIRES, PACHECO, APARICIO, VIDIGAL]],
      ['Idade', [PACHECO, VIDIGAL, APARICIO, RAMIRES]],
      ['Salário', [RAMIRES, PACHECO, APARICIO, VIDIGAL]],
    ])('ordena por %s nos dois sentidos', async (label, ascending) => {
      const user = userEvent.setup()
      renderMarket()
      await screen.findByText(VIDIGAL)

      await user.selectOptions(screen.getByLabelText('Ordenar por'), label)
      await waitFor(() => expect(shownNames()).toEqual(ascending))

      await user.click(screen.getByRole('button', { name: 'Ordem: crescente' }))
      await screen.findByRole('button', { name: 'Ordem: decrescente' })
      expect(shownNames()).toEqual([...ascending].reverse())
    })

    it('grava ordenação e sentido na URL, omitindo os padrões', async () => {
      const user = userEvent.setup()
      renderMarket()
      await screen.findByText(VIDIGAL)

      await user.selectOptions(screen.getByLabelText('Ordenar por'), 'Idade')
      await waitFor(() => expect(search()).toBe('?sort=age'))
      await user.click(screen.getByRole('button', { name: 'Ordem: crescente' }))
      await waitFor(() => expect(search()).toBe('?sort=age&dir=desc'))
    })
  })

  describe('busca', () => {
    it.each(['APARICIO', 'aparício', 'runo apar'])(
      '"%s" encontra pelo nome sem diferenciar maiúsculas e acentos',
      async (typed) => {
        const user = userEvent.setup()
        renderMarket()
        await screen.findByText(VIDIGAL)

        await user.type(screen.getByLabelText('Buscar por nome'), typed)

        await waitFor(() => expect(shownNames()).toEqual([APARICIO]))
        expect(screen.getByLabelText('Buscar por nome')).toHaveValue(typed)
      },
    )
  })

  describe('query string', () => {
    it('restaura ordenação, sentido e busca a partir da URL', async () => {
      renderMarket('/market?sort=age&dir=desc&q=a')

      await screen.findByText(VIDIGAL)
      expect(shownNames()).toEqual([RAMIRES, APARICIO, VIDIGAL, PACHECO])
      expect(screen.getByLabelText('Ordenar por')).toHaveValue('age')
      expect(screen.getByRole('button', { name: 'Ordem: decrescente' })).toBeInTheDocument()
      expect(screen.getByLabelText('Buscar por nome')).toHaveValue('a')
    })

    it('restaura a posição a partir da URL', async () => {
      renderMarket('/market?pos=GK')

      await screen.findByText(RAMIRES)
      expect(shownNames()).toEqual([RAMIRES])
      expect(screen.getByRole('button', { name: 'GL' })).toHaveAttribute('aria-pressed', 'true')
    })

    it('valores inválidos caem no padrão sem quebrar a tela', async () => {
      renderMarket('/market?pos=XX&sort=altura&dir=lateral')

      await screen.findByText(VIDIGAL)
      expect(shownNames()).toEqual([RAMIRES, PACHECO, APARICIO, VIDIGAL])
      expect(screen.getByRole('button', { name: 'Todos' })).toHaveAttribute('aria-pressed', 'true')
      expect(screen.getByLabelText('Ordenar por')).toHaveValue('price')
      expect(screen.getByRole('button', { name: 'Ordem: crescente' })).toBeInTheDocument()
    })
  })

  describe('mudanças antes de a URL anterior ser aplicada', () => {
    // O router aplica cada navegação numa transição, então `searchParams` pode
    // estar atrás da última mudança pedida. Foi o que derrubou o E2E no CI.
    it('digitar logo depois de mudar a ordem mantém os dois', async () => {
      renderMarket()
      await screen.findByText(VIDIGAL)

      act(() => {
        fireEvent.click(screen.getByRole('button', { name: 'Ordem: crescente' }))
        fireEvent.change(screen.getByLabelText('Buscar por nome'), { target: { value: 'vi' } })
      })

      await waitFor(() => expect(search()).toContain('q=vi'))
      expect(search()).toContain('dir=desc')
    })

    it('escolher uma posição logo depois de digitar não deixa o campo fora de sincronia', async () => {
      const user = userEvent.setup()
      renderMarket()
      await screen.findByText(VIDIGAL)
      const field = screen.getByLabelText('Buscar por nome')

      act(() => {
        fireEvent.change(field, { target: { value: 'vi' } })
        fireEvent.click(screen.getByRole('button', { name: 'AT', exact: true }))
      })

      await waitFor(() => expect(search()).toContain('pos=FW'))
      expect(search()).toContain('q=vi')
      expect(shownNames()).toEqual([VIDIGAL])

      await user.clear(field)
      await waitFor(() => expect(search()).not.toContain('q='))
      expect(field).toHaveValue('')
    })
  })

  describe('lista vazia', () => {
    it('sem resultado, explica e limpa os filtros mantendo a ordenação', async () => {
      const user = userEvent.setup()
      renderMarket('/market?pos=GK&q=zzz&sort=age')

      expect(await screen.findByText('Nenhum jogador encontrado.')).toBeInTheDocument()

      await user.click(screen.getByRole('button', { name: 'Limpar filtros' }))

      await waitFor(() => expect(shownNames()).toEqual([PACHECO, VIDIGAL, APARICIO, RAMIRES]))
      expect(search()).toBe('?sort=age')
      expect(screen.getByLabelText('Buscar por nome')).toHaveValue('')
    })

    it('mercado vazio mantém a mensagem própria, sem controles', async () => {
      server.use(http.get('/api/market', () => HttpResponse.json([])))
      renderMarket()

      expect(await screen.findByText('Nenhum jogador disponível no momento.')).toBeInTheDocument()
      expect(screen.queryByLabelText('Buscar por nome')).not.toBeInTheDocument()
    })
  })

  describe('caixa', () => {
    it('aparece no topo e atualiza depois de uma compra', async () => {
      const user = userEvent.setup()
      renderMarket()
      await screen.findByText(RAMIRES)

      const cash = await screen.findByText(money(800_000_00))
      expect(cash.closest('p')).toHaveTextContent(/^Caixa:/)

      await user.click(buyButton(RAMIRES))

      expect(await screen.findByText(money(620_000_00))).toBeInTheDocument()
      await waitFor(() => expect(screen.queryByText(RAMIRES)).not.toBeInTheDocument())
    })

    it('marca quem está acima do caixa e mantém a compra possível; o erro vem do backend', async () => {
      const user = userEvent.setup()
      state.clubs[1] = { ...originalClub, cash: 200_000_00 }
      renderMarket()
      await screen.findByText(money(200_000_00))

      expect(within(rowOf(RAMIRES)).queryByText('acima do seu caixa')).not.toBeInTheDocument()
      expect(within(rowOf(VIDIGAL)).getByText('acima do seu caixa')).toBeInTheDocument()

      expect(buyButton(VIDIGAL)).toBeEnabled()
      await user.click(buyButton(VIDIGAL))

      await waitFor(() =>
        expect(toast.error).toHaveBeenCalledWith(
          `Caixa insuficiente: necessário ${formatMoney(450_000_00)}, disponível ${formatMoney(200_000_00)}.`,
          { id: 'buy-result' },
        ),
      )
      expect(screen.getByText(VIDIGAL)).toBeInTheDocument()
    })
  })

  describe('resultado da compra', () => {
    it('sucesso vira toast verde, sem banner no topo', async () => {
      const user = userEvent.setup()
      renderMarket()
      await screen.findByText(RAMIRES)

      await user.click(buyButton(RAMIRES))

      await waitFor(() => expect(toast.success).toHaveBeenCalledWith(`${RAMIRES} contratado!`, { id: 'buy-result' }))
      expect(toast.error).not.toHaveBeenCalled()
      expect(screen.queryByText('Contratação concluída')).not.toBeInTheDocument()
    })

    it.each<[TransferResult['type'], TransferResult, RegExp]>([
      ['InsufficientFunds', { type: 'InsufficientFunds', required: 100_00, available: 50_00 }, /^Caixa insuficiente/],
      ['SquadFull', { type: 'SquadFull', maxSize: 25 }, /^Elenco cheio/],
      ['PlayerNotAvailable', { type: 'PlayerNotAvailable', playerId: 104 }, /indisponível/],
      ['Conflict', { type: 'Conflict' }, /alterado por outra operação/],
    ])('%s vira toast vermelho com mensagem própria', async (_type, result, message) => {
      const user = userEvent.setup()
      server.use(http.post('/api/market/buy', () => HttpResponse.json(result)))
      renderMarket()
      await screen.findByText(RAMIRES)

      await user.click(buyButton(RAMIRES))

      await waitFor(() => expect(toast.error).toHaveBeenCalledTimes(1))
      expect(toast.error).toHaveBeenCalledWith(expect.stringMatching(message), { id: 'buy-result' })
      expect(toast.success).not.toHaveBeenCalled()
    })

    it('erro HTTP notifica uma vez só, pelo MutationCache global', async () => {
      const user = userEvent.setup()
      server.use(
        http.post('/api/market/buy', () =>
          HttpResponse.json({ code: 'INTERNAL_ERROR', message: 'boom' }, { status: 500 }),
        ),
      )
      // Mesma ligação de `main.tsx`: é ela quem transforma erro de mutation em toast.
      const queryClient = new QueryClient({
        defaultOptions: { queries: { retry: false, staleTime: 0, gcTime: 0 }, mutations: { retry: false } },
        mutationCache: new MutationCache({
          onError: (error) => {
            toast.error(errorMessage(error))
          },
        }),
      })
      renderMarket('/market', queryClient)
      await screen.findByText(RAMIRES)

      await user.click(buyButton(RAMIRES))

      await waitFor(() =>
        expect(toast.error).toHaveBeenCalledWith('Erro no servidor. Tente novamente em instantes.'),
      )
      expect(toast.error).toHaveBeenCalledTimes(1)
    })
  })

  it('falha ao carregar mostra mensagem amigável, sem o erro cru', async () => {
    server.use(
      http.get('/api/market', () =>
        HttpResponse.json({ code: 'INTERNAL_ERROR', message: 'boom' }, { status: 500 }),
      ),
    )
    renderMarket()

    expect(await screen.findByText('Erro no servidor. Tente novamente em instantes.')).toBeInTheDocument()
    expect(screen.queryByText(/ApiError|boom/)).not.toBeInTheDocument()
  })
})

describe('MarketPage — nacionalidade (issue #123)', () => {
  it('mostra a bandeira com o nome do país de cada jogador', async () => {
    renderMarket()
    await screen.findByRole('button', { name: `Comprar ${APARICIO}` })

    expect(within(rowOf(APARICIO)).getByRole('img', { name: 'Argentina' })).toHaveTextContent('🇦🇷')
    expect(within(rowOf(VIDIGAL)).getByRole('img', { name: 'Brasil' })).toBeInTheDocument()
  })
})
