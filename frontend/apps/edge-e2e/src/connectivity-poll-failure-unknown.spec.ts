/**
 * SPEC-08 Scenario 5 — connectivity-poll-failure-unknown (FE-EDGE-023)
 *
 * Liveness poll failure → derivedState = 'unknown', NEVER 'online'.
 * Silent retry on next tick; state recovers when poll succeeds again.
 *
 * Invariant: unknown ≠ online (ADR-005 §2, honesty invariant).
 * No toast on poll failure (SPEC-07 §3 — only action ops get toast).
 *
 * Mode A (mock): verifies default online state + privacy (no credential leak).
 * Mode B (HTTP + intercepts): force poll to fail via route abort → assert unknown.
 *
 * References: SPEC-08 §5, SPEC-04 §3 (poll failure handling), SPEC-07 §3.
 */

import { test, expect } from '@playwright/test';
import {
  EDGE_BASE,
  EDGE_PATIENT_ID,
  makeLivenessPayload,
} from './fixtures/edge-stubs';

import { scriptSemearSessao } from '@neuroassist/shared/auth-login/testing';
import { scriptSemAlarmeDeEmergencia } from '@neuroassist/evnt/testing';

// Estado de partida da suíte. Ambos via `addInitScript`, que roda antes de
// qualquer script da página: o AuthSessionStore lê o sessionStorage no construtor
// e o mock do EVNT lê a bandeira na primeira chamada, então semear depois do goto
// seria tarde demais nos dois casos.
//
//   1. sessão — todas as rotas do shell passam a exigir login; sem isto a suíte
//      cai em /login;
//   2. sem emergência ativa — o mock do EVNT semeia uma, e o alarme de emergência
//      preempta a tela inteira (é o comportamento certo do produto, e a suíte do
//      EVNT existe para prová-lo). Sobre estas telas ele só contribui um segundo
//      <h1> e um alertdialog por cima do conteúdo.
test.beforeEach(async ({ page }) => {
  await page.addInitScript(scriptSemearSessao());
  await page.addInitScript(scriptSemAlarmeDeEmergencia());
});

// ---------------------------------------------------------------------------
// Mode A — Mock adapter (basic assertions)
// ---------------------------------------------------------------------------

test.describe('EDGE – Poll failure → unknown (mock-adapter mode)', () => {
  test('device detail shows online state when poll succeeds (mock default)', async ({ page }) => {
    await page.goto('/edge/device');
    // Default mock adapter: poll always succeeds → Online state.
    //
    // Escopado no cartão de status da página: "online" também aparece no chip do
    // topbar (shell) e na nota de rodapé sobre derivação, e um getByText solto
    // casaria com os três. Quem responde por "o dispositivo desta página está
    // online" é o cartão.
    await expect(
      page.locator('edge-connectivity-status-card').getByText('Online', { exact: true }),
    ).toBeVisible({ timeout: 4000 });
  });

  test('no credential data visible in device detail by default', async ({ page }) => {
    await page.goto('/edge/device');
    await expect(page.getByText(/Dispositivo/i).first()).toBeVisible({ timeout: 4000 });
    const content = await page.content();
    expect(content).not.toContain('clientSecret');
    expect(content).not.toContain('enrollmentToken');
  });
});

// ---------------------------------------------------------------------------
// Mode B — HTTP adapter + route intercepts
// ---------------------------------------------------------------------------

const HTTP_MODE = process.env['EDGE_HTTP_MODE'] === '1';

test.describe('EDGE – Poll failure → unknown (HTTP mode, route intercepts)', () => {
  test.skip(!HTTP_MODE, 'Requires EDGE_HTTP_MODE=1 (app in HTTP adapter mode)');

  test('chip transitions to unknown when poll fails, recovers when poll succeeds', async ({ page }) => {
    let pollShouldFail = false;

    // Stub liveness: dynamically fail or succeed based on `pollShouldFail` flag.
    await page.route(`**/patients/${EDGE_PATIENT_ID}/liveness`, async (route) => {
      if (pollShouldFail) {
        // Abort the request to simulate a network error.
        await route.abort('failed');
        return;
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(makeLivenessPayload({})),
      });
    });

    await page.goto('/edge/device');

    // Phase 1: poll succeeds → online state
    await expect(page.getByText(/Online|Dispositivo online/i)).toBeVisible({ timeout: 4000 });

    // Phase 2: force poll failure → unknown state (never online)
    pollShouldFail = true;
    // Wait for the next poll tick to fire.
    await page.waitForTimeout(2000);
    await expect(page.getByText(/Desconhecido|Sem confirmação|unknown/i)).toBeVisible({ timeout: 5000 });

    // Invariant: must NOT show online while poll is failing
    await expect(page.getByText(/^Online$|Dispositivo online/i)).not.toBeVisible();

    // Invariant: no toast on poll failure (SPEC-07 §3)
    await expect(page.locator('[role="alert"]')).not.toBeVisible();
    await expect(page.getByText(/Tentando novamente/i)).not.toBeVisible();

    // Phase 3: restore poll success → chip recovers to online
    pollShouldFail = false;
    await page.waitForTimeout(2000);
    await expect(page.getByText(/Online|Dispositivo online/i)).toBeVisible({ timeout: 5000 });
  });

  test('poll failure: device metadata still visible (no data wipe)', async ({ page }) => {
    // First load succeeds, then poll fails.
    let failCount = 0;
    await page.route(`**/patients/${EDGE_PATIENT_ID}/liveness`, async (route) => {
      failCount++;
      if (failCount > 1) {
        await route.abort('failed');
        return;
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(makeLivenessPayload({})),
      });
    });

    await page.goto('/edge/device');
    await expect(page.getByText(/Dispositivo/i).first()).toBeVisible({ timeout: 4000 });

    // After poll fails: device info still rendered (just chip degrades to unknown).
    await page.waitForTimeout(2000);
    await expect(page.getByText(/Dispositivo/i).first()).toBeVisible();
  });

  test('no toast shown on poll failure (SPEC-07 §3 — silent poll)', async ({ page }) => {
    await page.route(`**/patients/${EDGE_PATIENT_ID}/liveness`, async (route) => {
      await route.abort('failed');
    });

    await page.goto('/edge/device');

    // Let several poll ticks fire.
    await page.waitForTimeout(3000);

    // No toast must have appeared.
    await expect(page.locator('[role="alert"]')).not.toBeVisible();
    await expect(page.getByText(/Tentando novamente|retry/i)).not.toBeVisible();
  });
});
