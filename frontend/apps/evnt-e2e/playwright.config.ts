import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright config for EVNT e2e (A5 e2e-validate gate).
 * 5 features / 9 scenarios from the module e2e contracts (FE-EVNT-026..030).
 * Registered by fe-nx-register-module.py (D-487); prototype-first (D-484).
 *
 * webServer: sobe o dev server em :4200 se ainda não houver um.
 * reuseExistingServer: true → reaproveita um server já em execução (compartilhado
 * com acs-e2e etc.).
 *
 * APP_URL aponta a suíte para uma instância já servida e DESLIGA o webServer —
 * é assim que se roda contra o container (docker compose … web → :14200). Sem
 * esse desligamento o Playwright tentaria subir/validar :4200 mesmo com APP_URL
 * definido, e bastaria qualquer coisa escutando naquela porta para a suíte rodar
 * contra o app errado.
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
  webServer: process.env['APP_URL']
    ? undefined
    : {
        command: 'npx nx serve neuroassist-ai-web --no-open',
        url: 'http://localhost:4200',
        reuseExistingServer: true,
        timeout: 180_000,
      },
  // Recusa rodar contra um app que não é o nosso — ver o arquivo para o caso real.
  globalSetup: '../../tools/e2e/assert-app-servido.ts',
});
