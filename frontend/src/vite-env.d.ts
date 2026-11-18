/// <reference types="vite/client" />

// Variáveis `VITE_*` lidas pela app, tipadas como string opcional em vez do
// `any` do índice genérico de `vite/client`.
interface ImportMetaEnv {
  readonly VITE_USE_MOCKS?: string
  /** Latência das rotas REST do MSW em ms (padrão 120; o E2E usa 0). */
  readonly VITE_MOCK_LATENCY_MS?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
