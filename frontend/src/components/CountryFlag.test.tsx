import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { CountryFlag } from './CountryFlag'
import { COUNTRIES } from '../mocks/seed'

describe('CountryFlag (issue #123)', () => {
  it('sem nome, a bandeira é uma imagem com o nome do país como rótulo e tooltip', () => {
    render(<CountryFlag country={COUNTRIES.URU} />)
    const flag = screen.getByRole('img', { name: 'Uruguai' })
    expect(flag).toHaveTextContent('🇺🇾')
    expect(flag).toHaveAttribute('title', 'Uruguai')
  })

  it('com nome, o emoji vira decorativo e o nome aparece uma vez só', () => {
    render(<CountryFlag country={COUNTRIES.BRA} withName />)
    expect(screen.getByText('Brasil')).toBeInTheDocument()
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
  })
})
