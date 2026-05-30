import '@testing-library/jest-dom/vitest'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { cleanup } from '@testing-library/react'
import { setupServer } from 'msw/node'
import { handlers } from '../mocks/handlers'

// Desmonta a árvore React entre testes (vitest não tem auto-cleanup do RTL
// sem globals), evitando vazamento de DOM de um teste para o outro.
afterEach(() => cleanup())

// Servidor MSW para testes em Node. Handlers WebSocket sao no-op aqui
// (interceptacao real de WS so funciona via setupWorker no browser).
// Para testar fluxo de partida ao vivo, ver Playwright (E2E).
export const server = setupServer(...handlers)

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())
