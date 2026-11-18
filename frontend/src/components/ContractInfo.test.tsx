import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ContractInfo } from './ContractInfo'

describe('ContractInfo (issue #136)', () => {
  it('mostra salário e término do contrato', () => {
    render(
      <ContractInfo
        player={{ name: 'Renato', salary: 11_000_00, contract: { start: '2026-01-01', end: '2028-12-31', finalSeason: false } }}
      />,
    )
    expect(screen.getByText(/\/mês/)).toBeInTheDocument()
    expect(screen.getByText('até 31/12/2028')).toBeInTheDocument()
    expect(screen.queryByText('ÚLTIMO ANO')).not.toBeInTheDocument()
  })

  it('destaca o contrato na última temporada', () => {
    render(
      <ContractInfo
        player={{ name: 'Renato', salary: 11_000_00, contract: { start: '2026-01-01', end: '2026-12-31', finalSeason: true } }}
      />,
    )
    expect(screen.getByText('ÚLTIMO ANO')).toBeInTheDocument()
  })

  it('jogador sem contrato aparece como livre', () => {
    render(<ContractInfo player={{ name: 'Livre', salary: 0 }} />)
    expect(screen.getByText('livre')).toBeInTheDocument()
  })
})
