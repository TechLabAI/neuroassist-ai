import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright config for HIST e2e (A5 e2e-validate gate).
 * 2 features / 13 scenarios from the module e2e contracts.
 * Registered by /fe-architect-promote HIST (D-494); track D promote.
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
});
