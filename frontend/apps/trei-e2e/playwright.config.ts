import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright config for TREI e2e (P6 e2e-validate gate).
 * 6 features / 26 scenarios from the module e2e contracts
 * (docs/architecture/frontend/modules/trei/frontend/e2e/).
 * Registered by /fe-architect-promote neuroassist-ai TREI (D-494); Track D.
 *
 * DREI has no BA prototype — specs scaffolded from api-contract + vision doc.
 */
export default defineConfig({
  testDir: './src',
  fullyParallel: true,
  forbidOnly: !!process.env['CI'],
  retries: process.env['CI'] ? 2 : 0,
  workers: process.env['CI'] ? 1 : undefined,
  reporter: 'html',
  use: {
    baseURL: process.env['APP_URL'] ?? 'http://localhost:4200',
    trace: 'on-first-retry',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
  webServer: process.env['APP_URL']
    ? undefined
    : {
        command: 'npx nx serve neuroassist-ai-web --no-open',
        url: 'http://localhost:4200',
        reuseExistingServer: true,
        timeout: 180_000,
      },
  globalSetup: '../../tools/e2e/assert-app-servido.ts',
});
