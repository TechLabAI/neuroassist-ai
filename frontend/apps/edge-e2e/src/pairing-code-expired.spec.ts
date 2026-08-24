/**
 * SPEC-08 Scenario 2 — pairing-code-expired (FE-EDGE-022)
 *
 * Enrollment token TTL elapses before agent claims →
 * phase transitions to 'expired' → user prompted to re-register.
 * Phase MUST NOT hang in 'awaiting-claim' past the TTL (api-contract §2.1, SPEC-03 §7).
 *
 * Two modes:
 *   A. MOCK adapter: pairing code card renders; use Playwright clock.setFixedTime() to
 *      advance past a very short enrollmentExpiresAt.
 *   B. HTTP adapter + route intercepts: stub register with enrollmentExpiresAt = now+1s,
 *      advance clock by 2s, assert expired state.
 *
 * Safety-critical: a stale token must never silently hang or auto-advance to paired
 * without a real agent claim (SPEC-03 §7 code-TTL invariant).
 *
 * References: SPEC-08 §2, SPEC-03 §7, api-contract §2.1.
 */

import { test, expect } from '@playwright/test';
import { EDGE_BASE, fulfillRegister, EDGE_DEVICE_ID, makeDevicePayload } from './fixtures/edge-stubs';

import { scriptSemearSessao } from '@neuroassist/shared/auth-login/testing';

// Sessão semeada: todas as rotas do shell passam a exigir login (shared/auth-login).
// Sem isto a suíte cai em /login. `addInitScript` roda antes de qualquer script da
// página — o AuthSessionStore lê o sessionStorage no construtor, então semear
// depois do goto seria tarde demais.
test.beforeEach(async ({ page }) => {
  await page.addInitScript(scriptSemearSessao());
});

// ---------------------------------------------------------------------------
// Mode A — Mock adapter: uses Playwright virtual clock to expire the token
// The mock adapter returns `enrollmentExpiresAt = now + 5min`, so we install
// a fake clock, advance past that boundary, and assert the expired UI.
// ---------------------------------------------------------------------------

test.describe('EDGE – Pairing code expired (mock-adapter mode)', () => {
  test('pairing page renders the pairing surface correctly', async ({ page }) => {
    await page.goto('/edge/pairing');
    await expect(page.getByText(/Parear dispositivo/i)).toBeVisible({ timeout: 4000 });
  });

  test('pairing page shows re-register CTA when token expires (virtual clock)', async ({ page }) => {
    // Install virtual clock BEFORE page loads so Angular uses the frozen time.
    await page.clock.install({ time: new Date('2026-08-19T10:00:00.000Z') });

    await page.goto('/edge/pairing');
    await page.getByText(/Solicitar código/i).click();

    // Wait for the token to appear (mock: 400ms delay)
    await expect(page.getByText(/MOCK-TOKEN/i)).toBeVisible({ timeout: 2000 });

    // Advance clock past the mock token TTL (mock returns 5 min = 300s).
    // Advance by 310 seconds to be safely past the expiry.
    await page.clock.fastForward('5:30'); // advance 5m30s (330 seconds)

    // After TTL expiry the PairingCodeCardComponent should show "Código expirado"
    // and the re-register CTA must be present (SPEC-03 §7).
    // The mock adapter timer fires a TTL check via startTtlWatch().
    // In mock mode the token expires at Date.now() + 5min — if clock is advanced
    // the expired state should trigger (phase = 'expired').
    await expect(
      page.getByText(/Código expirado|Novo código|expirou/i),
    ).toBeVisible({ timeout: 5000 });
  });
});

// ---------------------------------------------------------------------------
// Mode B — HTTP adapter + route intercepts
// ---------------------------------------------------------------------------

const HTTP_MODE = process.env['EDGE_HTTP_MODE'] === '1';

test.describe('EDGE – Pairing code expired (HTTP mode, route intercepts)', () => {
  test.skip(!HTTP_MODE, 'Requires EDGE_HTTP_MODE=1 (app in HTTP adapter mode)');

  test('shows expired state after short-TTL token elapses', async ({ page }) => {
    // Install virtual clock so we can fast-forward time without real waits.
    await page.clock.install({ time: new Date('2026-08-19T10:00:00.000Z') });

    // Stub register: token expires in 1 second (very short TTL).
    await page.route(`${EDGE_BASE}/devices`, async (route, req) => {
      if (req.method() !== 'POST') { await route.continue(); return; }
      await fulfillRegister(route, {
        enrollmentToken: 'SHORT-TTL-TOKEN',
        enrollmentExpiresAt: new Date(Date.now() + 1_000).toISOString(), // 1s TTL
      });
    });

    // Stub device GET (claim poll returns registered, unclaimed)
    await page.route(`${EDGE_BASE}/devices/${EDGE_DEVICE_ID}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(makeDevicePayload({
          pairingState: 'registered',
          ultimaSincronizacao: null,
          status: 'n/a',
        })),
      });
    });

    await page.goto('/edge/pairing');
    await page.getByText(/Solicitar código/i).click();

    // Token appears
    await expect(page.getByText(/SHORT-TTL-TOKEN/i)).toBeVisible({ timeout: 3000 });

    // Advance clock by 2 seconds (past the 1s TTL)
    await page.clock.fastForward(2_000);

    // Assertions (SPEC-08 §2):
    // 1. "Código expirado" visible
    await expect(page.getByText(/Código expirado|expirou/i)).toBeVisible({ timeout: 3000 });
    // 2. Copy button gone (no point copying an expired token)
    await expect(page.getByText(/Copiar código/i)).not.toBeVisible();
    // 3. Re-register CTA present
    await expect(page.getByText(/Novo código|Solicitar novo/i)).toBeVisible({ timeout: 2000 });

    // 4. Click re-register → new POST /devices called
    const registerCalls: string[] = [];
    await page.on('request', req => {
      if (req.method() === 'POST' && req.url().includes('/devices')) {
        registerCalls.push(req.url());
      }
    });
    await page.getByText(/Novo código|Solicitar novo/i).click();
    // register() must be called again for a fresh token
    await expect.poll(() => registerCalls.length).toBeGreaterThan(0);
  });
});
