import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { Stat } from './Stat'

describe('Stat (issue #104)', () => {
  it('mostra rótulo e valor dentro de um card', () => {
    const { container } = render(<Stat label="Caixa" value="R$ 1.000,00" />)

    expect(screen.getByText('Caixa')).toBeInTheDocument()
    expect(screen.getByText('R$ 1.000,00')).toBeInTheDocument()
    expect(container.firstChild).toHaveClass('rounded-lg', 'bg-surface-raised/60')
  })
})
