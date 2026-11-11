import { describe, expect, it, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { FormationGrid, type FormationGridProps } from './FormationGrid'
import { FORMATIONS } from '../domain/formations'
import type { Player, Position } from '../domain/types'

const player = (id: number, name: string, position: Position, overall = 70, extra: Partial<Player> = {}): Player => ({
  id,
  name,
  position,
  overall,
  pace: overall,
  shooting: overall,
  passing: overall,
  defending: overall,
  stamina: 90,
  salary: 0,
  age: 25,
  goals: 0,
  yellowCards: 0,
  redCards: 0,
  availability: { type: 'Available' },
  star: overall >= 82,
  ...extra,
})

// 4-4-2 completo + dois reservas (um lesionado).
const SQUAD: Player[] = [
  player(1, 'Marcos Figueiredo', 'GK', 78),
  player(2, 'Rodrigo Alves', 'CB', 75),
  player(3, 'Túlio Mendes', 'CB'),
  player(4, 'Caio Bernardes', 'CB'),
  player(5, 'Pedro Henrique', 'CB'),
  player(6, 'Felipe Costa', 'MF'),
  player(7, 'Diego Lobato', 'MF'),
  player(8, 'Igor Vasques', 'MF'),
  player(9, 'Carlos Almeida', 'MF'),
  player(10, 'Renato Silva', 'FW', 88, { stamina: 64 }),
  player(11, 'João Faria', 'FW'),
  player(12, 'Lucas Reserva', 'FW', 68),
  player(13, 'Bruno Machucado', 'MF', 77, { availability: { type: 'Injured', roundsOut: 3 } }),
]
const STARTERS = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11]

function renderGrid(props: Partial<FormationGridProps> = {}) {
  const onAssign = vi.fn()
  const onSwap = vi.fn()
  render(
    <FormationGrid
      formation="4-4-2"
      slots={STARTERS}
      squad={SQUAD}
      onAssign={onAssign}
      onSwap={onSwap}
      {...props}
    />,
  )
  return { onAssign, onSwap, user: userEvent.setup() }
}

const sector = (name: string) => screen.getByRole('group', { name })
const reserves = () => within(screen.getByRole('region', { name: /Reservas/ }))

describe('FormationGrid', () => {
  const EXPECTED: Record<string, [number, number, number, number]> = {
    // [gol, defesa, meio, ataque]
    '4-4-2': [1, 4, 4, 2],
    '4-3-3': [1, 4, 3, 3],
    '3-5-2': [1, 3, 5, 2],
    '5-3-2': [1, 5, 3, 2],
  }

  it.each(FORMATIONS)('%s distribui os 11 slots pelas linhas do campo', (formation) => {
    renderGrid({ formation, slots: Array(11).fill(null) })
    const [gk, def, mid, att] = EXPECTED[formation]!

    expect(within(sector('Gol')).getAllByRole('button')).toHaveLength(gk)
    expect(within(sector('Defesa')).getAllByRole('button')).toHaveLength(def)
    expect(within(sector('Meio-campo')).getAllByRole('button')).toHaveLength(mid)
    expect(within(sector('Ataque')).getAllByRole('button')).toHaveLength(att)
    // Slot vazio mostra a posição esperada.
    expect(within(sector('Gol')).getByRole('button', { name: 'Goleiro 1: vazio' })).toHaveTextContent('GL')
  })

  it('slot preenchido mostra nome curto, OVR, estrela e stamina no nome acessível', () => {
    renderGrid()
    const slot = screen.getByRole('button', { name: /^Atacante 1:/ })

    expect(slot).toHaveAccessibleName('Atacante 1: Renato Silva, Atacante, OVR 88, stamina 64%, estrela')
    expect(slot).toHaveTextContent('Silva')
    expect(slot).toHaveTextContent('88')
    expect(slot).toHaveTextContent('★')
    expect(screen.getByRole('button', { name: /^Goleiro 1:/ })).not.toHaveTextContent('★')
  })

  it('marca o jogador fora de posição', () => {
    // MF no slot de atacante.
    renderGrid({ slots: [1, 2, 3, 4, 5, 6, 7, 8, 12, 10, 9] })

    const slot = screen.getByRole('button', { name: /^Atacante 2:/ })
    expect(slot).toHaveAccessibleName(/Carlos Almeida.*fora de posição$/)
    expect(slot).toHaveTextContent('MC!')
    expect(screen.getByRole('button', { name: /^Atacante 1:/ })).not.toHaveAccessibleName(/fora de posição/)
  })

  it('reservas listam exatamente quem está fora dos 11, com o lesionado desabilitado', () => {
    renderGrid()

    const buttons = reserves().getAllByRole('button')
    expect(buttons.map((b) => b.getAttribute('aria-label'))).toEqual([
      expect.stringMatching(/^Bruno Machucado.*lesionado por 3 jogo/),
      expect.stringMatching(/^Lucas Reserva/),
    ])
    expect(reserves().getByRole('button', { name: /Bruno Machucado/ })).toBeDisabled()
    expect(reserves().getByRole('button', { name: /Lucas Reserva/ })).toBeEnabled()
  })

  it('clique no slot e depois no reserva emite a troca', async () => {
    const { user, onAssign } = renderGrid()
    const slot = screen.getByRole('button', { name: /^Atacante 2:/ })

    await user.click(slot)
    expect(slot).toHaveAttribute('aria-pressed', 'true')
    await user.click(reserves().getByRole('button', { name: /Lucas Reserva/ }))

    expect(onAssign).toHaveBeenCalledWith(10, 12)
    expect(slot).toHaveAttribute('aria-pressed', 'false')
  })

  it('reserva primeiro e depois o slot também troca', async () => {
    const { user, onAssign } = renderGrid({ slots: [...STARTERS.slice(0, 10), null] })

    await user.click(reserves().getByRole('button', { name: /Lucas Reserva/ }))
    await user.click(screen.getByRole('button', { name: 'Atacante 2: vazio' }))

    expect(onAssign).toHaveBeenCalledWith(10, 12)
  })

  it('clique em dois titulares troca os dois de lugar; o mesmo slot de novo desfaz a seleção', async () => {
    const { user, onSwap } = renderGrid()
    const gk = screen.getByRole('button', { name: /^Goleiro 1:/ })

    await user.click(gk)
    await user.click(gk)
    expect(gk).toHaveAttribute('aria-pressed', 'false')
    expect(onSwap).not.toHaveBeenCalled()

    await user.click(gk)
    await user.click(screen.getByRole('button', { name: /^Zagueiro 2:/ }))
    expect(onSwap).toHaveBeenCalledWith(0, 2)
  })

  it('lesionado não é escalável', async () => {
    const { user, onAssign } = renderGrid()

    await user.click(screen.getByRole('button', { name: /^Meio-campo 1:/ }))
    await user.click(reserves().getByRole('button', { name: /Bruno Machucado/ }))

    expect(onAssign).not.toHaveBeenCalled()
  })

  it('"Tirar do time" esvazia o slot selecionado', async () => {
    const { user, onAssign } = renderGrid()

    await user.click(screen.getByRole('button', { name: /^Zagueiro 1:/ }))
    await user.click(screen.getByRole('button', { name: 'Tirar Alves do time' }))

    expect(onAssign).toHaveBeenCalledWith(1, null)
  })

  it('é operável só com teclado: Tab até o slot, Enter seleciona, Espaço no reserva troca', async () => {
    const { user, onAssign } = renderGrid({ slots: [...STARTERS.slice(0, 10), null] })

    // Os setores vêm do ataque para o gol: o primeiro Tab cai no Atacante 1.
    await user.tab()
    expect(screen.getByRole('button', { name: /^Atacante 1:/ })).toHaveFocus()
    await user.tab()
    const empty = screen.getByRole('button', { name: 'Atacante 2: vazio' })
    expect(empty).toHaveFocus()
    await user.keyboard('{Enter}')
    expect(empty).toHaveAttribute('aria-pressed', 'true')

    reserves().getByRole('button', { name: /Lucas Reserva/ }).focus()
    await user.keyboard(' ')

    expect(onAssign).toHaveBeenCalledWith(10, 12)
  })
})
