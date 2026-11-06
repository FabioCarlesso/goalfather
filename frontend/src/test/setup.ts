import '@testing-library/jest-dom/vitest'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { cleanup } from '@testing-library/react'
import { setupServer } from 'msw/node'
import { handlers } from '../mocks/handlers'

// Desmonta a árvore React entre testes (vitest não tem auto-cleanup do RTL
// sem globals), evitando vazamento de DOM de um teste para o outro.
afterEach(() => cleanup())

// Polyfill mínimo de `<dialog>` (issue #104): o jsdom cria o
// `HTMLDialogElement` e reflete o atributo `open`, mas NÃO implementa
// `show()`, `showModal()` nem `close()`, nem o fechamento por `Esc`. O `Modal`
// de `components/ui/` depende desse comportamento nativo; aqui imitamos só o
// necessário do navegador: abrir/fechar, disparar `close`, focar o primeiro
// elemento focável ao abrir e tratar `Esc` como `cancel` → `close`.
// Inerte (foco preso) e `::backdrop` não são simulados — isso é do browser.
if (typeof HTMLDialogElement !== 'undefined' && !HTMLDialogElement.prototype.showModal) {
  const modals = new Set<HTMLDialogElement>()
  const FOCUSABLE = 'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'

  HTMLDialogElement.prototype.show = function show(this: HTMLDialogElement) {
    this.open = true
  }
  HTMLDialogElement.prototype.showModal = function showModal(this: HTMLDialogElement) {
    this.open = true
    modals.add(this)
    this.querySelector<HTMLElement>(FOCUSABLE)?.focus()
  }
  HTMLDialogElement.prototype.close = function close(this: HTMLDialogElement, returnValue?: string) {
    if (!this.open) return
    this.open = false
    modals.delete(this)
    if (returnValue !== undefined) this.returnValue = returnValue
    this.dispatchEvent(new Event('close'))
  }
  document.addEventListener('keydown', (e) => {
    if (e.key !== 'Escape') return
    // `isConnected`: um dialog desmontado aberto (cleanup do RTL) não conta.
    const top = [...modals].filter((d) => d.isConnected && d.open).at(-1)
    if (!top) return
    const cancel = new Event('cancel', { cancelable: true })
    if (top.dispatchEvent(cancel)) top.close()
  })
}

// Servidor MSW para testes em Node. Handlers WebSocket sao no-op aqui
// (interceptacao real de WS so funciona via setupWorker no browser).
// Para testar fluxo de partida ao vivo, ver Playwright (E2E).
export const server = setupServer(...handlers)

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())
