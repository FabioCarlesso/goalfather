import { defineConfig, devices } from '@playwright/test'

// Em modo 'real' apontamos para o backend Spring em :8080 e desligamos o MSW.
// Em modo padrao (mock-first) usamos Vite + MSW.
const REAL_BACKEND = process.env.VITE_USE_MOCKS === 'false'

export default defineConfig({
  testDir: './e2e',
  // Contra o backend real há UMA liga, resetada a cada teste (issue #101):
  // dois workers resetariam o estado um do outro no meio do teste.
  fullyParallel: !REAL_BACKEND,
  forbidOnly: !!process.env.CI,
  // Sem retry no modo real (issue #102): um teste que só passa na segunda
  // tentativa esconderia a dependência de ordem/estado que a #101 eliminou.
  retries: process.env.CI && !REAL_BACKEND ? 2 : 0,
  workers: process.env.CI || REAL_BACKEND ? 1 : undefined,
  reporter: process.env.CI ? 'github' : 'list',

  use: {
    baseURL: 'http://localhost:5173',
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
    command: REAL_BACKEND ? 'VITE_USE_MOCKS=false npm run dev' : 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: !process.env.CI,
    timeout: 60_000,
  },
})
