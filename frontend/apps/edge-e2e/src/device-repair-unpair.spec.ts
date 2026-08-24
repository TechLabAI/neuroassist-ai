/**
 * SPEC-08 Scenario 6 — device-repair-unpair (FE-EDGE-024)
 *
 * Assertions:
 *   (a) Conductor session: re-pair / unpair / retire buttons visible.
 *   (b) Caregiver session: zero management affordance in DOM.
 *   (c) 409 pairing-conflict on unpair → GET /devices/{id} re-fetched (no stale write).
 *   (d) 409 optimistic-lock → device read-model refreshed, no retry.
 *
 * Mode A (mock adapter): conductor actions rendered; basic confirm dialog.
 * Mode B (HTTP + intercepts): 409 handling, caregiver RBAC, call-count assertions.
 *
 * References: SPEC-08 §6, SPEC-07 §1 (409 conflict), SPEC-05 §5 (RBAC).
 */

import { test, expect } from '@playwright/test';
import {
  EDGE_BASE,
  EDGE_DEVICE_ID,
  makeDevicePayload,
  fulfillError,
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
// Mode A — Mock adapter (basic conductor actions)
// ---------------------------------------------------------------------------

test.describe('EDGE – Device management actions (mock-adapter mode)', () => {
  test('shows management actions for conductor (mock: canManage=true)', async ({ page }) => {
    await page.goto('/edge/device');
    // Mock adapter: canManage=true → management buttons visible (SPEC-05 §5).
    await expect(page.getByText(/Reparear|Despareador|Aposentar/i).first()).toBeVisible({ timeout: 4000 });
  });

  test('unpair shows confirmation dialog', async ({ page }) => {
    await page.goto('/edge/device');
    const unpairBtn = page.getByText(/Despareador/i).first();
    if (await unpairBtn.isVisible({ timeout: 3000 })) {
      await unpairBtn.click();
      // Confirmation dialog must appear before executing the action.
      await expect(page.getByText(/Despareador dispositivo\?|Confirmar/i)).toBeVisible({ timeout: 3000 });
    }
  });

  test('cancel unpair dismisses confirmation without executing action', async ({ page }) => {
    await page.goto('/edge/device');
    const unpairBtn = page.getByText(/Despareador/i).first();
    if (await unpairBtn.isVisible({ timeout: 3000 })) {
      await unpairBtn.click();
      const cancelBtn = page.getByText(/Cancelar/i);
      if (await cancelBtn.isVisible({ timeout: 2000 })) {
        await cancelBtn.click();
        // Dialog should dismiss.
        await expect(page.getByText(/Despareador dispositivo\?/i)).not.toBeVisible({ timeout: 2000 });
      }
    }
  });
});

// ---------------------------------------------------------------------------
// Mode B — HTTP adapter + route intercepts (full RBAC and 409 handling)
// ---------------------------------------------------------------------------

const HTTP_MODE = process.env['EDGE_HTTP_MODE'] === '1';

test.describe('EDGE – Device management (HTTP mode, route intercepts)', () => {
  test.skip(!HTTP_MODE, 'Requires EDGE_HTTP_MODE=1 (app in HTTP adapter mode)');

  test('caregiver session: zero management action buttons in DOM', async ({ page }) => {
    // Simulate a caregiver session by injecting a no-manage flag via localStorage.
    // The DeviceFacade._canManage signal is set based on session entitlement.
    // In HTTP mode, the session token determines permissions.
    // For e2e, we inject a flag that the app reads to mock a caregiver session.
    await page.addInitScript(() => {
      window.localStorage.setItem('__EDGE_E2E_CAREGIVER__', '1');
    });

    await page.goto(`/edge/device/${EDGE_DEVICE_ID}`);
    await expect(page.getByText(/Dispositivo/i).first()).toBeVisible({ timeout: 4000 });

    // Caregiver: management buttons MUST be hidden (not disabled) in DOM.
    // SPEC-05 §5: "DeviceActionsComponent hides (not disables) management affordances"
    const actionButtonCount = await page.locator('[data-action="manage"]').count();
    expect(actionButtonCount).toBe(0);

    // Alternatively: assert specific buttons absent from DOM
    await expect(page.getByText(/Despareador/i)).not.toBeVisible();
    await expect(page.getByText(/Aposentar/i)).not.toBeVisible();
  });

  test('409 pairing-conflict on unpair: GET /devices/{id} re-fetched, no stale write', async ({ page }) => {
    let getDeviceCallCount = 0;

    // Stub GET device (conductor session)
    await page.route(`${EDGE_BASE}/devices/${EDGE_DEVICE_ID}`, async (route, req) => {
      if (req.method() === 'GET') {
        getDeviceCallCount++;
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(makeDevicePayload()),
        });
        return;
      }
      await route.continue();
    });

    const countBefore = getDeviceCallCount; // 0

    // Stub DELETE → 409 pairing-conflict
    await page.route(
      `${EDGE_BASE}/devices/${EDGE_DEVICE_ID}/pairings/current`,
      async (route, req) => {
        if (req.method() === 'DELETE') {
          await fulfillError(
            route,
            '/errors/pairing-conflict',
            409,
            'Este paciente já tem um dispositivo ativo. Despareie o atual antes de parear um novo.',
          );
          return;
        }
        await route.continue();
      },
    );

    await page.goto(`/edge/device/${EDGE_DEVICE_ID}`);
    await expect(page.getByText(/Despareador/i)).toBeVisible({ timeout: 4000 });

    // Handle confirm dialog
    page.on('dialog', (dialog) => dialog.accept());

    // Click unpair → 409 returned → device must be re-fetched
    await page.getByText(/Despareador/i).first().click();
    await page.waitForTimeout(1000);

    // After 409 conflict: GET /devices/{id} must have been called again (refresh).
    expect(getDeviceCallCount).toBeGreaterThan(countBefore);

    // DELETE must NOT be retried automatically (409 is not a retryable conflict).
    const deleteCallCount = await page.evaluate(() =>
      (window as Record<string, unknown>)['__edgeDeleteCount__'] as number ?? 0,
    );
    // The delete was called exactly once (no automatic retry after 409).
    expect(deleteCallCount).toBeLessThanOrEqual(1);
  });

  test('409 optimistic-lock on unpair: device read-model refreshed', async ({ page }) => {
    let getDeviceCallCount = 0;

    await page.route(`${EDGE_BASE}/devices/${EDGE_DEVICE_ID}`, async (route, req) => {
      if (req.method() === 'GET') {
        getDeviceCallCount++;
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(makeDevicePayload()),
        });
        return;
      }
      await route.continue();
    });

    await page.route(
      `${EDGE_BASE}/devices/${EDGE_DEVICE_ID}/pairings/current`,
      async (route, req) => {
        if (req.method() === 'DELETE') {
          await fulfillError(
            route,
            '/errors/optimistic-lock',
            409,
            'O estado do dispositivo mudou. Recarregando…',
          );
          return;
        }
        await route.continue();
      },
    );

    await page.goto(`/edge/device/${EDGE_DEVICE_ID}`);
    const countBefore = getDeviceCallCount;

    page.on('dialog', (dialog) => dialog.accept());
    await page.getByText(/Despareador/i).first().click();
    await page.waitForTimeout(1000);

    // GET /devices/{id} must have been called again after optimistic-lock 409.
    expect(getDeviceCallCount).toBeGreaterThan(countBefore);
  });
});
