import { describe, expect, it, vi } from 'vitest'
import { handOff } from './streamHandoff'

// Só o pedaço da API de WebSocket que o handOff usa.
class FakeSocket extends EventTarget {
  onopen: (() => void) | null = () => {}
  onmessage: (() => void) | null = () => {}
  onerror: (() => void) | null = () => {}
  onclose: (() => void) | null = () => {}
  close = vi.fn()
}
const asSocket = (s: FakeSocket) => s as unknown as WebSocket

describe('handOff', () => {
  it('silencia a conexão antiga já e só a fecha quando a nova termina', () => {
    const previous = new FakeSocket()
    const next = new FakeSocket()

    handOff(asSocket(previous), asSocket(next))

    expect(previous.onmessage).toBeNull()
    expect(previous.onclose).toBeNull()
    expect(previous.onerror).toBeNull()
    expect(previous.onopen).toBeNull()
    // A antiga segue aberta: é ela que segura a corrida no servidor.
    expect(previous.close).not.toHaveBeenCalled()

    next.dispatchEvent(new Event('close'))
    expect(previous.close).toHaveBeenCalledOnce()
    // Não mexe nos handlers da nova.
    expect(next.onclose).not.toBeNull()
  })

  it('sem conexão anterior, não faz nada', () => {
    const next = new FakeSocket()
    handOff(null, asSocket(next))
    next.dispatchEvent(new Event('close'))
    expect(next.close).not.toHaveBeenCalled()
  })
})
