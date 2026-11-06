import { afterEach, describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { Link, useLocation } from 'react-router-dom'
import { LineupPage } from './LineupPage'
import { renderWithProviders } from '../test/render'
import { server } from '../test/setup'
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

// Troca de formação e alterações não salvas (issue #109).
describe('LineupPage: alterações não salvas', () => {
  const original = state.clubs[1]!
  afterEach(() => {
    state.clubs[1] = original
  })

  const UNSAVED = 'Alterações não salvas'

  function Harness() {
    const { pathname } = useLocation()
    return (
      <>
        <Link to="/round">Rodada</Link>
        <Link to="/lineup?aba=tatica">Mesma rota</Link>
        <span data-testid="path">{pathname}</span>
        {pathname === '/lineup' && <LineupPage />}
      </>
    )
  }

  async function open() {
    const user = userEvent.setup()
    const view = renderWithProviders(<Harness />, { initialEntries: ['/lineup'] })
    await screen.findByText('11/11 titulares')
    return { user, ...view }
  }

  // Os dois primeiros combobox são formação e postura; o resto são os 11 slots.
  const slotValues = () =>
    screen.getAllByRole<HTMLSelectElement>('combobox').slice(2).map((s) => s.value)

  const editPosture = (user: ReturnType<typeof userEvent.setup>) =>
    user.selectOptions(screen.getByLabelText('Postura'), 'OFFENSIVE')

  const leave = (user: ReturnType<typeof userEvent.setup>) =>
    user.click(screen.getByRole('link', { name: 'Rodada' }))

  const path = () => screen.getByTestId('path').textContent

  it('4-4-2 → 4-3-3 mantém GL, 4 ZG, 3 MC e 2 AT; só o terceiro AT fica vazio', async () => {
    const { user } = await open()
    const before = slotValues()

    await user.selectOptions(screen.getByLabelText('Formação'), '4-3-3')

    expect(screen.getByText('10/11 titulares')).toBeInTheDocument()
    expect(slotValues()).toEqual([...before.slice(0, 8), before[9], before[10], ''])
    expect(screen.getByText(UNSAVED)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Salvar escalação' })).toBeDisabled()
  })

  it('abrir a tela não marca alteração nem bloqueia a saída', async () => {
    const { user } = await open()
    expect(screen.queryByText(UNSAVED)).not.toBeInTheDocument()

    await leave(user)

    await waitFor(() => expect(path()).toBe('/round'))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('desfazer a edição volta ao estado limpo', async () => {
    const { user } = await open()
    await editPosture(user)
    expect(screen.getByText(UNSAVED)).toBeInTheDocument()

    await user.selectOptions(screen.getByLabelText('Postura'), 'BALANCED')

    expect(screen.queryByText(UNSAVED)).not.toBeInTheDocument()
  })

  it('restaurar volta à effectiveLineup recebida do servidor', async () => {
    const { user } = await open()
    const before = slotValues()
    await user.selectOptions(screen.getByLabelText('Formação'), '4-3-3')
    await editPosture(user)

    await user.click(screen.getByRole('button', { name: 'Restaurar escalação automática' }))

    expect(screen.getByLabelText('Formação')).toHaveValue('4-4-2')
    expect(screen.getByLabelText('Postura')).toHaveValue('BALANCED')
    expect(slotValues()).toEqual(before)
    expect(screen.queryByText(UNSAVED)).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Restaurar escalação automática' })).toBeDisabled()
  })

  it('com alterações, sair pede confirmação e "Ficar" mantém as edições', async () => {
    const { user } = await open()
    await editPosture(user)

    await leave(user)

    const dialog = await screen.findByRole('dialog')
    expect(path()).toBe('/lineup')

    await user.click(within(dialog).getByRole('button', { name: 'Ficar' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(path()).toBe('/lineup')
    expect(screen.getByLabelText('Postura')).toHaveValue('OFFENSIVE')
    expect(screen.getByText(UNSAVED)).toBeInTheDocument()
  })

  it('Esc no diálogo equivale a "Ficar"', async () => {
    const { user } = await open()
    await editPosture(user)
    await leave(user)
    await screen.findByRole('dialog')

    await user.keyboard('{Escape}')

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(path()).toBe('/lineup')
    expect(screen.getByLabelText('Postura')).toHaveValue('OFFENSIVE')
  })

  it('"Sair sem salvar" conclui a navegação', async () => {
    const { user } = await open()
    await editPosture(user)
    await leave(user)
    const dialog = await screen.findByRole('dialog')

    await user.click(within(dialog).getByRole('button', { name: 'Sair sem salvar' }))

    await waitFor(() => expect(path()).toBe('/round'))
  })

  it('mudar só a query string não pede confirmação', async () => {
    const { user } = await open()
    await editPosture(user)

    await user.click(screen.getByRole('link', { name: 'Mesma rota' }))

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Postura')).toHaveValue('OFFENSIVE')
  })

  it('salvar com sucesso limpa o estado e libera a saída', async () => {
    const { user } = await open()
    await editPosture(user)

    await user.click(screen.getByRole('button', { name: 'Salvar escalação' }))

    await screen.findByText('Escalação salva ✓')
    expect(screen.queryByText(UNSAVED)).not.toBeInTheDocument()

    await leave(user)
    await waitFor(() => expect(path()).toBe('/round'))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('falha ao salvar mostra a mensagem amigável e mantém as alterações pendentes', async () => {
    server.use(http.post('/api/clubs/:id/lineup', () => new HttpResponse(null, { status: 500 })))
    const { user } = await open()
    await editPosture(user)

    await user.click(screen.getByRole('button', { name: 'Salvar escalação' }))

    await screen.findByText(/Erro no servidor/)
    expect(screen.getByText(UNSAVED)).toBeInTheDocument()
  })

  it('refetch em segundo plano não marca alteração nem sobrescreve edições', async () => {
    const { user, queryClient } = await open()
    const before = slotValues()
    state.clubs[1] = {
      ...original,
      lineup: { formation: '4-3-3', playerIds: original.squad.map((p) => p.id), posture: 'DEFENSIVE' },
    }

    await queryClient.refetchQueries({ queryKey: ['club', 1] })
    expect(screen.queryByText(UNSAVED)).not.toBeInTheDocument()

    await user.selectOptions(screen.getByLabelText('Postura'), 'OFFENSIVE')
    await queryClient.refetchQueries({ queryKey: ['club', 1] })

    expect(screen.getByLabelText('Postura')).toHaveValue('OFFENSIVE')
    expect(screen.getByLabelText('Formação')).toHaveValue('4-4-2')
    expect(slotValues()).toEqual(before)
  })

  it('beforeunload só avisa enquanto há alterações', async () => {
    const { user } = await open()
    const unload = () => {
      const event = new Event('beforeunload', { cancelable: true })
      window.dispatchEvent(event)
      return event.defaultPrevented
    }
    expect(unload()).toBe(false)

    await editPosture(user)
    expect(unload()).toBe(true)

    await user.click(screen.getByRole('button', { name: 'Restaurar escalação automática' }))
    expect(unload()).toBe(false)
  })
})
