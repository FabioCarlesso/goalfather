import '@testing-library/jest-dom/vitest'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { setupServer } from 'msw/node'
import { handlers } from '../mocks/handlers'

// Servidor MSW para testes em Node. Handlers WebSocket sao no-op aqui
// (interceptacao real de WS so funciona via setupWorker no browser).
// Para testar fluxo de partida ao vivo, ver Playwright (E2E).
export const server = setupServer(...handlers)

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())
