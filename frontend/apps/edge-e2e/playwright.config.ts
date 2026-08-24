import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright config for EDGE e2e (A5 e2e-validate gate).
 * 8 features from SPEC-08 e2e scenarios:
 *   - device-connectivity-derived (ADR-005 freshness derivation)
 *   - pairing-flow (idle → awaiting-claim → paired)
 *   - connectivity-chip-shell (always-visible topbar chip)
 *   - stale-detection (staleness band honest rendering)
 *   - offline-detection (offline after window elapsed)
 *   - unknown-on-poll-failure (poll failure → unknown, never online)
 *   - unpair-no-optimistic (unpair refreshes from server)
 *   - code-expired (token TTL respected → expired phase)
 * Registered by fe-nx-register-module.py (D-487 pattern); promote-first (D-493).
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
  // Sem isto a suíte roda contra QUALQUER coisa que esteja escutando em :4200 —
  // foi o que aconteceu aqui: um dev server esquecido de uma sessão anterior
  // devolvia `/` para toda rota, e 58 cenários reprovaram por "selector não
  // encontrado" sem que houvesse defeito nenhum no aplicativo. Só a evnt-e2e
  // tinha este bloco; as outras seis dependiam de alguém ter subido o server.
  //
  // APP_URL aponta para uma instância já servida (o container, :14200) e desliga
  // o webServer — sem esse desligamento o Playwright validaria :4200 mesmo assim.
  webServer: process.env['APP_URL']
    ? undefined
    : {
        command: 'npx nx serve neuroassist-ai-web --no-open',
        url: 'http://localhost:4200',
        reuseExistingServer: true,
        timeout: 180_000,
      },
});
