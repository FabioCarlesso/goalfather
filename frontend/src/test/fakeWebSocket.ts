import { act } from '@testing-library/react'
import type { RoundEvent } from '../domain/types'

/**
 * WebSocket falso para testes em jsdom. O MSW em Node não intercepta
 * WebSocket (ver `test/setup.ts`): trocando o global por esta classe
 * (`vi.stubGlobal('WebSocket', FakeWebSocket)`), o teste dita a ordem exata
 * dos eventos e de abertura/fechamento.
 */
export class FakeWebSocket {
  static instances: FakeWebSocket[] = []
  onopen: (() => void) | null = null
  onmessage: ((e: { data: string }) => void) | null = null
  onerror: (() => void) | null = null
  onclose: ((e: { code: number; reason: string }) => void) | null = null
  closed = false
  readonly url: string
  // Listeners de `close` via addEventListener (o `handOff` da #115 usa).
  private closeListeners: (() => void)[] = []

  constructor(url: string) {
    this.url = url
    FakeWebSocket.instances.push(this)
  }

  /** Último socket aberto pelo código sob teste. */
  static last(): FakeWebSocket {
    const socket = FakeWebSocket.instances.at(-1)
    if (!socket) throw new Error('nenhum WebSocket aberto')
    return socket
  }

  close() {
    this.closed = true
  }

  addEventListener(type: string, listener: () => void) {
    if (type === 'close') this.closeListeners.push(listener)
  }

  open() {
    act(() => this.onopen?.())
  }

  /** Entrega um evento do servidor (`send` seria o caminho cliente → servidor). */
  emit(event: RoundEvent) {
    act(() => this.onmessage?.({ data: JSON.stringify(event) }))
  }

  /** Falha de rede/protocolo do lado do browser (`error` antes do `close`). */
  fail() {
    act(() => this.onerror?.())
  }

  serverClose(code = 1000, reason = '') {
    act(() => {
      this.onclose?.({ code, reason })
      this.closeListeners.forEach((l) => l())
    })
  }
}
