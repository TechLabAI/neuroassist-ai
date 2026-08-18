import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright config for EVNT e2e (A5 e2e-validate gate).
 * 5 features / 9 scenarios from the module e2e contracts (FE-EVNT-026..030).
 * Registered by fe-nx-register-module.py (D-487); prototype-first (D-484).
 *
 * webServer: auto-starts Angular dev server if not already running on :4200.
 * reuseExistingServer: true → reuses a running server (shared with acs-e2e etc.).
 */
export default defineConfig({
  testDir: './src',
  fullyParallel: false,
  forbidOnly: !!process.env['CI'],
  retries: process.env['CI'] ? 2 : 0,
  workers: 1,
  reporter: 'html',
  use: {
    baseURL: process.env['APP_URL'] ?? 'http://localhost:4200',
    trace: 'on-first-retry',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
  webServer: {
    command: 'npx nx serve neuroassist-ai-web --no-open',
    url: 'http://localhost:4200',
    reuseExistingServer: true,
    timeout: 180_000,
  },
});
