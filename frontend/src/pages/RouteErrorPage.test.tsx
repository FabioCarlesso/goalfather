import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { RouteErrorPage } from './RouteErrorPage'

function Boom(): never {
  throw new Error('detalhe interno')
}

const renderAt = (path: string) =>
  render(
    <RouterProvider
      router={createMemoryRouter(
        [{ errorElement: <RouteErrorPage />, children: [{ path: '/boom', element: <Boom /> }] }],
        { initialEntries: [path] },
      )}
    />,
  )

describe('RouteErrorPage', () => {
  it('URL inexistente explica e oferece a volta', () => {
    renderAt('/nao-existe')

    expect(screen.getByRole('heading', { name: 'Página não encontrada' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Voltar ao início' })).toHaveAttribute('href', '/')
  })

  it('erro de render não expõe o detalhe do erro', () => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    vi.spyOn(console, 'warn').mockImplementation(() => {})
    renderAt('/boom')

    expect(screen.getByRole('heading', { name: 'Algo deu errado' })).toBeInTheDocument()
    expect(screen.queryByText(/detalhe interno/)).not.toBeInTheDocument()
    vi.restoreAllMocks()
  })
})
