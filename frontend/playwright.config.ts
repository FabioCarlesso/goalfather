import { defineConfig, devices } from '@playwright/test'

// Em modo 'real' apontamos para o backend Spring em :8080 e desligamos o MSW.
// Em modo padrao (mock-first) usamos Vite + MSW.
const REAL_BACKEND = process.env.VITE_USE_MOCKS === 'false'

// Porta do Vite configurável para rodar mais de uma suíte ao mesmo tempo
// (ex.: worktrees em paralelo). Com a porta fixa e `reuseExistingServer`,
// uma suíte testaria o servidor de outro checkout.
const PORT = Number(process.env.E2E_PORT ?? 5173)
const BASE_URL = `http://localhost:${PORT}`

export default defineConfig({
  testDir: './e2e',
  // Contra o backend real há UMA liga, resetada a cada teste (issue #101):
  // dois workers resetariam o estado um do outro no meio do teste.
  fullyParallel: !REAL_BACKEND,
  forbidOnly: !!process.env.CI,
  // Sem retry no modo real (issue #102): um teste que só passa na segunda
  // tentativa esconderia a dependência de ordem/estado que a #101 eliminou.
  retries: process.env.CI && !REAL_BACKEND ? 2 : 0,
  // Mock: cada teste tem o estado do MSW isolado na própria página, então o CI
  // usa os 4 vCPUs do runner (issue #156 — serial, a suíte levava ~7min30).
  // Local fica no padrão do Playwright (metade dos núcleos); para poupar a
  // máquina, `--workers=1`. Real: sempre 1 (liga única, ver acima).
  workers: REAL_BACKEND ? 1 : process.env.CI ? 4 : undefined,
  reporter: process.env.CI ? 'github' : 'list',

  use: {
    baseURL: BASE_URL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    // Chrome Headless Shell exige headless: true
    launchOptions: {
      // Adapta para o Headless Shell baixado pelo playwright install
      headless: true,
    },
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'], channel: undefined },
    },
  ],

  webServer: {
    // Mock sem latência artificial (src/mocks/latency.ts): 120ms por chamada
    // só deixava a suíte mais lenta. Com `reuseExistingServer`, um `npm run
    // dev` já aberto na mesma porta mantém a latência dele.
    command: `${REAL_BACKEND ? 'VITE_USE_MOCKS=false ' : 'VITE_MOCK_LATENCY_MS=0 '}npm run dev -- --port ${PORT} --strictPort`,
    url: BASE_URL,
    reuseExistingServer: !process.env.CI,
    timeout: 60_000,
  },
})
