import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Button } from './Button'

describe('Button (issue #104)', () => {
  it('é primary/md por padrão, com type=button e foco visível', () => {
    render(<Button>Salvar</Button>)
    const btn = screen.getByRole('button', { name: 'Salvar' })

    expect(btn).toHaveAttribute('type', 'button')
    expect(btn).toHaveClass('bg-accent', 'px-4', 'py-2', 'focus-visible:ring-2')
  })

  it.each([
    ['secondary', 'border-slate-700'],
    ['danger', 'bg-danger'],
  ] as const)('aplica a variante %s', (variant, cls) => {
    render(<Button variant={variant}>Ok</Button>)
    expect(screen.getByRole('button', { name: 'Ok' })).toHaveClass(cls)
  })

  it('aplica o tamanho sm', () => {
    render(<Button size="sm">Comprar</Button>)
    expect(screen.getByRole('button', { name: 'Comprar' })).toHaveClass('px-3', 'py-1.5')
  })

  it('dispara onClick e repassa className extra', async () => {
    const onClick = vi.fn()
    render(<Button onClick={onClick} className="w-full">Ir</Button>)
    const btn = screen.getByRole('button', { name: 'Ir' })

    await userEvent.click(btn)
    expect(onClick).toHaveBeenCalledOnce()
    expect(btn).toHaveClass('w-full')
  })

  it('loading desabilita, marca aria-busy e mostra o spinner', async () => {
    const onClick = vi.fn()
    render(<Button loading onClick={onClick}>Enviando</Button>)
    const btn = screen.getByRole('button', { name: 'Enviando' })

    expect(btn).toBeDisabled()
    expect(btn).toHaveAttribute('aria-busy', 'true')
    expect(screen.getByTestId('button-spinner')).toBeInTheDocument()
    await userEvent.click(btn)
    expect(onClick).not.toHaveBeenCalled()
  })

  it('recebe foco pela navegação por teclado', async () => {
    render(<Button>Foco</Button>)
    await userEvent.tab()
    expect(screen.getByRole('button', { name: 'Foco' })).toHaveFocus()
  })
})
