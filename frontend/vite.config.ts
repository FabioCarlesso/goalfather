import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    proxy: {
      // WebSocket de partida ao vivo: proxy direto para o backend Spring.
      // MSW não intercepta WS, então o handler real é configurado no backend
      // ou num mock server custom durante desenvolvimento (ver src/mocks/ws.ts).
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true,
        rewriteWsOrigin: true,
      },
    },
  },
})
