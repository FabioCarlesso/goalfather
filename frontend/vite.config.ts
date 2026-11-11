/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// Backend do proxy: `E2E_BACKEND_URL` (o mesmo que `e2e/helpers.ts` usa para o
// reset) permite rodar o `e2e:real` contra um backend em outra porta — ex.:
// worktrees em paralelo, cada uma com o seu. Default: o Spring em :8080.
const BACKEND_URL = process.env.E2E_BACKEND_URL ?? 'http://localhost:8080'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    proxy: {
      // REST: quando MSW esta desligado (VITE_USE_MOCKS=false), proxy
      // forward para o backend Spring em :8080. Com MSW ligado, MSW
      // intercepta antes do proxy ver.
      '/api': {
        target: BACKEND_URL,
        changeOrigin: true,
      },
      // WebSocket de partida ao vivo: proxy direto para o backend Spring.
      // MSW intercepta no nivel do construtor do WebSocket em DEV; quando
      // MSW estiver desligado (VITE_USE_MOCKS=false), este proxy assume.
      '/ws': {
        target: BACKEND_URL.replace(/^http/, 'ws'),
        ws: true,
        rewriteWsOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: false,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    // Vitest fica em src/. E2E (Playwright) vive em e2e/ e é executado
    // por outro runner — exclui aqui para não dar pickup conflitante.
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
    exclude: ['e2e/**', 'node_modules/**', 'dist/**'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html'],
      exclude: ['src/api/generated.d.ts', 'src/mocks/**', 'src/test/**'],
    },
  },
})
