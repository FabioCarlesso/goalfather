import { defineConfig, devices } from '@playwright/test'

// Em modo 'real' apontamos para o backend Spring em :8080 e desligamos o MSW.
// Em modo padrao (mock-first) usamos Vite + MSW.
const REAL_BACKEND = process.env.VITE_USE_MOCKS === 'false'

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
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
