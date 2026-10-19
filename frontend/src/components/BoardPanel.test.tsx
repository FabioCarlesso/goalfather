import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { BoardPanel } from './BoardPanel'
import type { BoardStatus } from '../domain/types'

const status: BoardStatus = {
  clubId: 1,
  season: 2026,
  objective: 'TITLE',
  targetPosition: 1,
  currentPosition: 4,
  divisionSize: 6,
  matchday: 3,
  confidence: 20,
  mood: 'AT_RISK',
  bonusCents: 300_000_00,
  firingConfidence: 25,
}

describe('BoardPanel (issue #62)', () => {
  it('mostra meta, posição, bônus e a confiança vindos do backend', () => {
    render(<BoardPanel status={status} />)

    expect(screen.getByRole('heading', { name: 'Diretoria' })).toBeInTheDocument()
    expect(screen.getByText('Conquistar o título')).toBeInTheDocument()
    expect(screen.getByText('até o 1º de 6')).toBeInTheDocument()
    expect(screen.getByText('4º')).toBeInTheDocument()
    expect(screen.getByText('após 3 rodada(s)')).toBeInTheDocument()
    // Humor vem pronto do backend — o painel não recalcula o limiar.
    expect(screen.getByText('Cargo em risco')).toBeInTheDocument()
    expect(screen.getByRole('meter', { name: 'Confiança da diretoria' })).toHaveAttribute('aria-valuenow', '20')
    expect(screen.getByTitle('Limiar de demissão: 25%')).toBeInTheDocument()
  })

  it('antes da estreia avisa que a temporada não começou', () => {
    render(<BoardPanel status={{ ...status, matchday: 0, mood: 'WARY', confidence: 50 }} />)

    expect(screen.getByText('temporada ainda não começou')).toBeInTheDocument()
    expect(screen.getByText('Diretoria atenta')).toBeInTheDocument()
  })
})
