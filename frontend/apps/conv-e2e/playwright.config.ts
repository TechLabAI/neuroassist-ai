import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright config for CONV e2e (Layer 2 / A5 e2e-validate gate).
 * 14 scenarios registered — all marked test.fixme() pending Layer 2 implementation
 * (G-4/CONV-FE-028). Gate = OK (pending) at this promote stage.
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
