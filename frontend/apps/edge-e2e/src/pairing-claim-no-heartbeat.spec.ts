/**
 * SPEC-08 Scenario 3 — pairing-claim-no-heartbeat (FE-EDGE-022)
 *
 * Agent claims the enrollment token but never sends first heartbeat →
 * phase stays 'awaiting-first-heartbeat', NEVER advances to 'paired'.
 *
 * Safety-critical invariant: PairingFacade MUST NOT advance to 'paired' on
 * a claim response alone — a fresh ultimaSincronizacao is required (ADR-005,
 * SPEC-03 §7 no-premature-paired).
 *
 * This invariant is enforced in the PairingFacade (handleFirstHeartbeat guard)
 * and is validated at the unit level in edge-pairing.facade.spec.ts (P2 DoD).
 * This e2e spec validates the UI contract: the "pareado" step must NOT be
 * marked done while phase is still 'awaiting-first-heartbeat'.
 *
 * HTTP mode: stub liveness to always return null ultimaSincronizacao.
 *
 * References: SPEC-08 §3, ADR-005, SPEC-03 §7.
 */

import { test, expect } from '@playwright/test';
import {
  EDGE_BASE,
  EDGE_DEVICE_ID,
  EDGE_PATIENT_ID,
  fulfillRegister,
  makeDevicePayload,
  makeLivenessPayload,
} from './fixtures/edge-stubs';

import { scriptSemearSessao } from '@neuroassist/shared/auth-login/testing';

// Sessão semeada: todas as rotas do shell passam a exigir login (shared/auth-login).
// Sem isto a suíte cai em /login. `addInitScript` roda antes de qualquer script da
// página — o AuthSessionStore lê o sessionStorage no construtor, então semear
// depois do goto seria tarde demais.
test.beforeEach(async ({ page }) => {
  await page.addInitScript(scriptSemearSessao());
});

// ---------------------------------------------------------------------------
// Mode A — Mock adapter assertions
// ---------------------------------------------------------------------------

test.describe('EDGE – Pairing claim no heartbeat (mock-adapter mode)', () => {
  test('pairing page renders correctly (surface check)', async ({ page }) => {
    await page.goto('/edge/pairing');
    await expect(page.getByText(/Parear dispositivo/i)).toBeVisible({ timeout: 4000 });
  });

  test('pairing page remains in awaiting state without heartbeat', async ({ page }) => {
    // The mock PairingMockAdapter calls pairToPatient which returns a fresh device immediately.
    // For no-heartbeat validation at the UI level we assert the waiting step is shown
    // immediately after register before the poll cycle completes.
    await page.goto('/edge/pairing');
    await page.getByText(/Solicitar código/i).click();

    // Token visible (awaiting-claim phase active)
    await expect(page.getByText(/MOCK-TOKEN/i)).toBeVisible({ timeout: 2000 });

    // At this point (immediately after register), "Dispositivo pareado!" MUST NOT be shown.
    // This is the no-premature-paired check at the e2e level.
    await expect(page.getByText(/Dispositivo pareado!/i)).not.toBeVisible();
  });
});

// ---------------------------------------------------------------------------
// Mode B — HTTP adapter + route intercepts (full no-heartbeat scenario)
// ---------------------------------------------------------------------------

const HTTP_MODE = process.env['EDGE_HTTP_MODE'] === '1';

test.describe('EDGE – Pairing claim no heartbeat (HTTP mode, route intercepts)', () => {
  test.skip(!HTTP_MODE, 'Requires EDGE_HTTP_MODE=1 (app in HTTP adapter mode)');

  test('phase stays awaiting-first-heartbeat when liveness always returns null ultimaSincronizacao', async ({ page }) => {
    // Stub register
    await page.route(`${EDGE_BASE}/devices`, async (route, req) => {
      if (req.method() !== 'POST') { await route.continue(); return; }
      await fulfillRegister(route, { enrollmentToken: 'NO-HB-TOKEN' });
    });

    // Stub pairings claim (agent claims but no heartbeat yet)
    await page.route(
      `${EDGE_BASE}/devices/${EDGE_DEVICE_ID}/pairings`,
      async (route, req) => {
        if (req.method() !== 'POST') { await route.continue(); return; }
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(makeDevicePayload({
            pairingState: 'registered',
            ultimaSincronizacao: null, // agent claimed but never heartbeated
            status: 'n/a',
          })),
        });
      },
    );

    // Stub device GET — always returns unclaimed (null ultimaSincronizacao)
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

    // Stub liveness — always null (never a fresh heartbeat)
    await page.route(`**/patients/${EDGE_PATIENT_ID}/liveness`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(makeLivenessPayload({
          ultimaSincronizacao: null,
          status: 'n/a',
        })),
      });
    });

    await page.goto('/edge/pairing');
    await page.getByText(/Solicitar código/i).click();

    // Token displayed (awaiting-claim)
    await expect(page.getByText(/NO-HB-TOKEN/i)).toBeVisible({ timeout: 3000 });

    // Wait for the pairing steps to show (progress component)
    // "Aguardando código" or "Aguardando heartbeat" step must remain active
    // and "Pareado" step must NOT be marked done.
    await page.waitForTimeout(2000); // wait through 1 claim poll cycle

    // Assertion: phase is NOT 'paired' — chip MUST NOT be green
    await expect(page.getByText(/Dispositivo pareado!/i)).not.toBeVisible();

    // Assertion: connectivity chip MUST NOT show Online (no heartbeat → not online)
    await expect(page.locator('[data-derived-state="online"]')).not.toBeVisible();
  });
});
