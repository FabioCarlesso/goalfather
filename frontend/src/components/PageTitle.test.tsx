import { render } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { PageTitle } from './PageTitle'

describe('PageTitle', () => {
  it('iça o <title> para o document.head no formato "<Tela> · GoalFather"', () => {
    const { container } = render(<PageTitle>Mercado</PageTitle>)

    expect(document.title).toBe('Mercado · GoalFather')
    expect(document.head.querySelector('title')?.textContent).toBe('Mercado · GoalFather')
    // Não fica no corpo onde foi renderizado.
    expect(container.querySelector('title')).toBeNull()
  })

  it('remove o título do head ao desmontar', () => {
    const { unmount } = render(<PageTitle>Tabela</PageTitle>)
    unmount()
    expect(document.head.querySelector('title')).toBeNull()
  })
})
