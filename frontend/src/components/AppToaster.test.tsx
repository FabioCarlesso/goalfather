import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import toast from 'react-hot-toast'
import { AppToaster } from './AppToaster'

describe('AppToaster', () => {
  afterEach(() => {
    toast.remove()
    vi.unstubAllGlobals()
  })

  it('erro persistente pode ser fechado pelo botão', async () => {
    // O react-hot-toast consulta `matchMedia`, que o jsdom não implementa.
    vi.stubGlobal('matchMedia', () => ({ matches: false }))
    const user = userEvent.setup()
    render(<AppToaster />)
    act(() => {
      toast.error('Caixa insuficiente')
    })

    await screen.findByText('Caixa insuficiente')
    await user.click(screen.getByRole('button', { name: 'Fechar aviso' }))

    await waitFor(() =>
      expect(screen.getByText('Caixa insuficiente').closest('[style]')).toHaveStyle({ opacity: '0' }),
    )
  })
})
