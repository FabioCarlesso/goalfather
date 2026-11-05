import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Modal } from './Modal'

/** Gatilho + modal controlado, como uma tela usaria. */
function Harness({ onClose = () => {} }: { onClose?: () => void }) {
  const [open, setOpen] = useState(false)
  const close = () => {
    onClose()
    setOpen(false)
  }
  return (
    <>
      <button onClick={() => setOpen(true)}>Vender</button>
      <Modal
        open={open}
        onClose={close}
        title="Vender jogador?"
        actions={<button onClick={close}>Cancelar</button>}
      >
        A venda é imediata.
      </Modal>
    </>
  )
}

// O jsdom não implementa `showModal()`/`Esc`: o polyfill de `src/test/setup.ts`
// imita o comportamento nativo do browser que o Modal usa.
describe('Modal (issue #104)', () => {
  it('fica fechado até o gatilho abrir', async () => {
    render(<Harness />)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Vender' }))

    const dialog = screen.getByRole('dialog', { name: 'Vender jogador?' })
    expect(dialog).toHaveAttribute('open')
    expect(screen.getByText('A venda é imediata.')).toBeInTheDocument()
    // showModal() leva o foco para dentro do diálogo.
    expect(dialog).toContainElement(document.activeElement as HTMLElement)
  })

  it('Esc fecha, avisa onClose e devolve o foco ao gatilho', async () => {
    const onClose = vi.fn()
    render(<Harness onClose={onClose} />)
    const trigger = screen.getByRole('button', { name: 'Vender' })

    await userEvent.click(trigger)
    expect(screen.getByRole('dialog')).toBeInTheDocument()

    await userEvent.keyboard('{Escape}')

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(onClose).toHaveBeenCalledOnce()
    expect(trigger).toHaveFocus()
  })

  it('fechar por um botão de ação também devolve o foco ao gatilho', async () => {
    const onClose = vi.fn()
    render(<Harness onClose={onClose} />)
    const trigger = screen.getByRole('button', { name: 'Vender' })

    await userEvent.click(trigger)
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }))

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(onClose).toHaveBeenCalledOnce()
    expect(trigger).toHaveFocus()
  })

  it('o botão × fecha o modal', async () => {
    render(<Harness />)
    await userEvent.click(screen.getByRole('button', { name: 'Vender' }))
    await userEvent.click(screen.getByRole('button', { name: 'Fechar' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})
