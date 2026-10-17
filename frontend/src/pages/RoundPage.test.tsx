import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import { RoundPage } from './RoundPage'
import { renderWithProviders } from '../test/render'

describe('RoundPage — card de partida (issue #85)', () => {
  it('expõe o nome completo do clube no title, já que nomes longos são truncados', async () => {
    renderWithProviders(<RoundPage />, { initialEntries: ['/round'] })

    // Seed: "Atlético Bonsucesso" e "Independente Sul" estão na 1ª divisão.
    const home = await screen.findByTitle('Atlético Bonsucesso')
    expect(home).toHaveTextContent('Atlético Bonsucesso')
    expect(home).toHaveClass('truncate')
    expect(await screen.findByTitle('Independente Sul')).toHaveClass('truncate')
  })
})
