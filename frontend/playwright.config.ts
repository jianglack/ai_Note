import { defineConfig, devices } from '@playwright/test';

const frontendURL = process.env.AINOTE_FRONTEND_URL
  ?? (process.env.AINOTE_E2E_FULLSTACK === 'true' ? 'http://127.0.0.1:5173' : 'http://127.0.0.1:4174');
const frontendPort = new URL(frontendURL).port || '4174';

export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: {
    timeout: 10_000,
  },
  use: {
    baseURL: frontendURL,
    trace: 'retain-on-failure',
  },
  webServer: process.env.AINOTE_E2E_REUSE_FRONTEND === 'true'
    ? undefined
    : {
        command: `npm run dev -- --host 127.0.0.1 --port ${frontendPort}`,
        url: frontendURL,
        reuseExistingServer: !process.env.CI,
        timeout: 120_000,
      },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
