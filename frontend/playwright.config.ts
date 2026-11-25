import { defineConfig, devices } from '@playwright/test'

// End to end tests drive a real browser against the real backend. Start the backend first
// (for example with `docker compose up --build` in the repository root). The tests start Vite themselves.
const API_URL = process.env.PAYCORE_API_URL ?? 'http://localhost:8080'

export default defineConfig({
  testDir: './e2e',
  globalSetup: './e2e/global-setup.ts',
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: !process.env.CI,
    env: { PAYCORE_API_URL: API_URL },
  },
})
