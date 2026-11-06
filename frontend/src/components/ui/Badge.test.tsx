import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { Badge } from './Badge'

describe('Badge (issue #104)', () => {
  it('usa o tom neutral por padrão', () => {
    render(<Badge>Divisão 1</Badge>)
    expect(screen.getByText('Divisão 1')).toHaveClass('bg-slate-800', 'text-slate-300')
  })

  it.each([
    ['success', 'text-emerald-300'],
    ['warning', 'text-amber-300'],
    ['danger', 'text-red-300'],
    ['info', 'text-sky-300'],
  ] as const)('aplica o tom %s', (tone, cls) => {
    render(<Badge tone={tone}>{tone}</Badge>)
    expect(screen.getByText(tone)).toHaveClass(cls)
  })

  it('repassa atributos e className', () => {
    render(<Badge title="dica" className="uppercase">x</Badge>)
    const badge = screen.getByText('x')
    expect(badge).toHaveAttribute('title', 'dica')
    expect(badge).toHaveClass('uppercase')
  })
})
