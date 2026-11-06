import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { EmptyState } from './EmptyState'
import { Button } from './Button'

describe('EmptyState (issue #105)', () => {
  it('mostra a mensagem, sem ação por padrão', () => {
    render(<EmptyState message="Nada por aqui." />)

    expect(screen.getByText('Nada por aqui.')).toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('mostra a ação opcional', () => {
    render(<EmptyState message="Nada por aqui." action={<Button>Criar</Button>} />)

    expect(screen.getByRole('button', { name: 'Criar' })).toBeInTheDocument()
  })
})
