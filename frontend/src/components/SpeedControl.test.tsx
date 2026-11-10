import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { SpeedControl } from './SpeedControl'

describe('SpeedControl', () => {
  it('mostra a velocidade atual e avisa a troca', async () => {
    const onSpeedChange = vi.fn()
    render(<SpeedControl speed="NORMAL" onSpeedChange={onSpeedChange} canSkip={false} onSkip={() => {}} />)

    const select = screen.getByLabelText('Velocidade')
    expect(select).toHaveValue('NORMAL')
    expect(screen.getAllByRole('option').map((o) => o.textContent)).toEqual([
      'Lenta',
      'Normal',
      'Rápida',
      'Instantânea',
    ])

    await userEvent.selectOptions(select, 'INSTANT')
    expect(onSpeedChange).toHaveBeenCalledWith('INSTANT')
  })

  it('"Pular para o fim" só aparece quando dá para pular', async () => {
    const onSkip = vi.fn()
    const { rerender } = render(
      <SpeedControl speed="NORMAL" onSpeedChange={() => {}} canSkip={false} onSkip={onSkip} />,
    )
    expect(screen.queryByRole('button', { name: 'Pular para o fim' })).not.toBeInTheDocument()

    rerender(<SpeedControl speed="NORMAL" onSpeedChange={() => {}} canSkip onSkip={onSkip} live />)
    await userEvent.click(screen.getByRole('button', { name: 'Pular para o fim' }))
    expect(onSkip).toHaveBeenCalledOnce()
    expect(screen.getByText(/vale a partir da próxima transmissão/)).toBeInTheDocument()
  })
})
