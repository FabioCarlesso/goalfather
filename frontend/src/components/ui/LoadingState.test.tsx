import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { LoadingState, type LoadingShape } from './LoadingState'

describe('LoadingState (issue #105)', () => {
  it('anuncia o rótulo em role="status", sem aria-busy para não calar o anúncio', () => {
    render(<LoadingState label="Carregando tabela…" />)

    const status = screen.getByRole('status')
    expect(status).toHaveTextContent('Carregando tabela…')
    expect(status).not.toHaveAttribute('aria-busy')
  })

  it('esconde o skeleton de leitores de tela e respeita prefers-reduced-motion', () => {
    render(<LoadingState />)

    const skeleton = screen.getByTestId('skeleton')
    expect(skeleton).toHaveAttribute('aria-hidden', 'true')
    expect(skeleton).toHaveClass('animate-pulse', 'motion-reduce:animate-none')
  })

  it.each<[LoadingShape, number]>([['list', 3], ['table', 4], ['cards', 2]])(
    'desenha a forma %s com %i itens',
    (shape, rows) => {
      render(<LoadingState shape={shape} rows={rows} withHeader={false} />)

      // A tabela tem uma linha a mais: a do cabeçalho.
      const expected = shape === 'table' ? rows + 1 : rows
      expect(screen.getByTestId('skeleton').firstElementChild?.children).toHaveLength(expected)
    },
  )

  it('withHeader reserva o espaço do cabeçalho da página', () => {
    render(<LoadingState shape="cards" rows={1} />)
    expect(screen.getByTestId('skeleton').children).toHaveLength(2)
  })
})
