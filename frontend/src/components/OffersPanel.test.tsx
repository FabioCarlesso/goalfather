import { describe, expect, it, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { OffersPanel } from './OffersPanel'
import type { TransferOffer } from '../domain/types'

const base: TransferOffer = {
  id: 1,
  buyerClubId: 2,
  buyerClubName: 'Atlético Bonsucesso',
  sellerClubId: 1,
  sellerClubName: 'Goal Father FC',
  playerId: 10,
  playerName: 'Renato Silva',
  amount: 900_000_00,
  roundsLeft: 2,
  status: { type: 'Pending' },
}

const offers: TransferOffer[] = [
  base,
  { ...base, id: 2, playerName: 'João Faria', status: { type: 'Expired', reason: 'PLAYER_TRANSFERRED' } },
  {
    ...base,
    id: 3,
    buyerClubId: 1,
    buyerClubName: 'Goal Father FC',
    sellerClubId: 3,
    sellerClubName: 'Rival FC',
    playerName: 'Craque Rival',
    roundsLeft: 1,
  },
]

describe('OffersPanel (issue #63)', () => {
  it('separa recebidas de enviadas e conta as pendentes recebidas', () => {
    render(<OffersPanel offers={offers} clubId={1} onAction={() => {}} />)

    expect(screen.getByRole('heading', { name: /Propostas/ })).toHaveTextContent('1 nova')
    const received = screen.getByText('Recebidas').parentElement!
    expect(within(received).getByText('Renato Silva')).toBeInTheDocument()
    expect(within(received).getByText('expira em 2 rodadas')).toBeInTheDocument()
    // Resolvida aparece com o estado, sem botões; o motivo vai no title.
    expect(within(received).getByText('Expirada')).toHaveAttribute('title', 'o jogador deixou o clube vendedor')

    const sent = screen.getByText('Enviadas').parentElement!
    expect(within(sent).getByText('Craque Rival')).toBeInTheDocument()
    expect(within(sent).getByText('expira em 1 rodada')).toBeInTheDocument()
  })

  it('dispara aceitar/recusar nas recebidas e retirar nas enviadas', async () => {
    const user = userEvent.setup()
    const onAction = vi.fn()
    render(<OffersPanel offers={offers} clubId={1} onAction={onAction} />)

    await user.click(screen.getByRole('button', { name: 'Aceitar' }))
    await user.click(screen.getByRole('button', { name: 'Recusar' }))
    await user.click(screen.getByRole('button', { name: 'Retirar' }))

    expect(onAction.mock.calls).toEqual([[1, 'accept'], [1, 'reject'], [3, 'withdraw']])
  })

  it('trava os botões da proposta com comando em voo', () => {
    render(<OffersPanel offers={offers} clubId={1} onAction={() => {}} busyOfferId={1} />)

    expect(screen.getByRole('button', { name: 'Aceitar' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Retirar' })).toBeEnabled()
  })

  it('mostra estado vazio sem propostas', () => {
    render(<OffersPanel offers={[]} clubId={1} onAction={() => {}} />)

    expect(screen.getByText('Nenhuma proposta recebida.')).toBeInTheDocument()
    expect(screen.getByText(/Nenhuma proposta enviada/)).toBeInTheDocument()
  })
})
