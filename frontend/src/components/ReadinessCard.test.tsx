import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ReadinessCard } from './ReadinessCard'

describe('ReadinessCard', () => {
  it('mostra a contagem e os técnicos pendentes', () => {
    render(
      <ReadinessCard
        readyCount={1}
        totalCount={2}
        pendingUsernames={['bruno']}
        amIReady={false}
        onReady={() => {}}
      />,
    )
    expect(screen.getByText(/1\/2/)).toBeInTheDocument()
    expect(screen.getByText(/técnicos prontos/)).toBeInTheDocument()
    expect(screen.getByText(/Aguardando: bruno/)).toBeInTheDocument()
  })

  it('mostra "Estou pronto" e dispara onReady ao clicar', async () => {
    const onReady = vi.fn()
    render(
      <ReadinessCard
        readyCount={0}
        totalCount={2}
        pendingUsernames={['ana', 'bruno']}
        amIReady={false}
        onReady={onReady}
      />,
    )
    await userEvent.click(screen.getByRole('button', { name: 'Estou pronto' }))
    expect(onReady).toHaveBeenCalledOnce()
  })

  it('quando já sinalizei mas faltam outros, mostra "Aguardando outros…" sem botão', () => {
    render(
      <ReadinessCard
        readyCount={1}
        totalCount={2}
        pendingUsernames={['bruno']}
        amIReady
        onReady={() => {}}
      />,
    )
    expect(screen.getByText('Aguardando outros…')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Estou pronto' })).not.toBeInTheDocument()
  })

  it('quando todos prontos, libera a mensagem e marca "Pronto ✓"', () => {
    render(
      <ReadinessCard
        readyCount={2}
        totalCount={2}
        pendingUsernames={[]}
        amIReady
        onReady={() => {}}
      />,
    )
    expect(screen.getByText(/Todos prontos/)).toBeInTheDocument()
    expect(screen.getByText('Pronto ✓')).toBeInTheDocument()
  })
})
