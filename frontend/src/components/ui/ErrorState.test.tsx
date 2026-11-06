import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ErrorState } from './ErrorState'
import { ApiError } from '../../api/client'

describe('ErrorState (issue #105)', () => {
  it('anuncia a mensagem amigável como alerta, sem expor o erro cru', () => {
    render(<ErrorState error={new ApiError(500, null)} onRetry={() => {}} />)

    const alert = screen.getByRole('alert')
    expect(alert).toHaveTextContent('Erro no servidor. Tente novamente em instantes.')
    expect(alert).not.toHaveTextContent('ApiError')
  })

  it('"Tentar novamente" chama onRetry sem repassar o evento de clique', async () => {
    const user = userEvent.setup()
    const onRetry = vi.fn()
    render(<ErrorState error={new Error('offline')} onRetry={onRetry} />)

    await user.click(screen.getByRole('button', { name: 'Tentar novamente' }))

    expect(onRetry).toHaveBeenCalledTimes(1)
    expect(onRetry).toHaveBeenCalledWith()
  })
})
