import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { Card } from './Card'

describe('Card (issue #104)', () => {
  it('renderiza só o conteúdo quando não há título', () => {
    const { container } = render(<Card>conteúdo</Card>)

    expect(screen.getByText('conteúdo')).toBeInTheDocument()
    expect(screen.queryByRole('heading')).not.toBeInTheDocument()
    expect(container.firstChild).toHaveClass('rounded-lg', 'border-border', 'bg-surface-raised/60', 'p-4')
  })

  it('mostra título e slot de ações', () => {
    render(
      <Card title="Diretoria" actions={<span>legenda</span>} className="space-y-3">
        corpo
      </Card>,
    )

    expect(screen.getByRole('heading', { level: 2, name: 'Diretoria' })).toBeInTheDocument()
    expect(screen.getByText('legenda')).toBeInTheDocument()
    expect(screen.getByText('corpo')).toBeInTheDocument()
  })

  it('como section, vira região nomeada pelo título', () => {
    render(<Card as="section" title="Propostas">lista</Card>)
    expect(screen.getByRole('region', { name: 'Propostas' })).toBeInTheDocument()
  })

  it('como div, não cria região', () => {
    render(<Card title="Treino">x</Card>)
    expect(screen.queryByRole('region')).not.toBeInTheDocument()
  })
})
