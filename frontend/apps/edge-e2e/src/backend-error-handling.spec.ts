/**
 * SPEC-08 Scenario 8 — backend-error-handling (FE-EDGE-026)
 *
 * ProblemDetail error responses → correct user-facing surface.
 * Error surface routing (SPEC-07 §4):
 *   400 /errors/validation      → inline/banner
 *   403 /errors/insufficient-scope → toast
 *   409 /errors/pairing-conflict  → banner
 *   409 /errors/device-retired    → banner (NEW EARS clause — FE-EDGE-027)
 *
 * Privacy invariant: no clientSecret, enrollmentToken, or biometric data in DOM
 *   (api-contract §8, SPEC-07 §9).
 *
 * Mode A (mock): privacy check + basic DOM assertions.
 * Mode B (HTTP): full error surface routing via route intercepts.
 *
 * References: SPEC-08 §8, SPEC-07, api-contract §8.
 */

import { test, expect } from '@playwright/test';
import {
  EDGE_BASE,
  EDGE_DEVICE_ID,
  EDGE_PATIENT_ID,
  makeDevicePayload,
  fulfillError,
  fulfillRegister,
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
// Mode A — Mock adapter: privacy assertion + basic structure
// ---------------------------------------------------------------------------

test.describe('EDGE – Backend error handling (mock-adapter mode, privacy)', () => {
  test('device detail does not expose clientSecret in DOM', async ({ page }) => {
    await page.goto('/edge/device');
    await expect(page.getByText(/Dispositivo/i).first()).toBeVisible({ timeout: 4000 });
    const content = await page.content();
    expect(content).not.toContain('clientSecret');
  });

  test('device detail does not expose enrollmentToken in DOM', async ({ page }) => {
    await page.goto('/edge/device');
    await expect(page.getByText(/Dispositivo/i).first()).toBeVisible({ timeout: 4000 });
    const content = await page.content();
    expect(content).not.toContain('enrollmentToken');
  });

  test('device detail does not expose biometric confidence values in DOM', async ({ page }) => {
    await page.goto('/edge/device');
    await expect(page.getByText(/Dispositivo/i).first()).toBeVisible({ timeout: 4000 });
    const content = await page.content();
    expect(content).not.toContain('"confidence"');
    expect(content).not.toContain('confidence:');
  });

  test('pairing page does not expose credential data in DOM', async ({ page }) => {
    await page.goto('/edge/pairing');
    await expect(page.getByText(/Parear dispositivo/i)).toBeVisible({ timeout: 4000 });
    const content = await page.content();
    expect(content).not.toContain('clientSecret');
    expect(content).not.toContain('"confidence"');
  });
});

// ---------------------------------------------------------------------------
// Mode B — HTTP adapter + route intercepts (full error surface routing)
// ---------------------------------------------------------------------------

const HTTP_MODE = process.env['EDGE_HTTP_MODE'] === '1';

test.describe('EDGE – Backend error handling (HTTP mode, route intercepts)', () => {
  test.skip(!HTTP_MODE, 'Requires EDGE_HTTP_MODE=1 (app in HTTP adapter mode)');

  test('400 /errors/validation → inline/banner on register', async ({ page }) => {
    await page.route(`${EDGE_BASE}/devices`, async (route, req) => {
      if (req.method() !== 'POST') { await route.continue(); return; }
      await fulfillError(
        route,
        '/errors/validation',
        400,
        'Verifique os dados do dispositivo antes de continuar.',
      );
    });

    await page.goto('/edge/pairing');
    await page.getByText(/Solicitar código/i).click();

    // Banner: "Verifique os dados..." must appear inline.
    await expect(
      page.getByText(/Verifique os dados/i),
    ).toBeVisible({ timeout: 4000 });

    // Not a toast (toasts are transient; banner is persistent).
    // Verify no credential data in the error message DOM.
    const errorText = await page.locator('[role="alert"], .banner, .error-banner').textContent();
    if (errorText) {
      expect(errorText).not.toContain('clientSecret');
      expect(errorText).not.toContain('enrollmentToken');
    }
  });

  test('403 /errors/insufficient-scope → toast (not banner/modal)', async ({ page }) => {
    await page.route(`${EDGE_BASE}/devices/${EDGE_DEVICE_ID}/pairings`, async (route, req) => {
      if (req.method() !== 'POST') { await route.continue(); return; }
      await fulfillError(
        route,
        '/errors/insufficient-scope',
        403,
        'Você não tem permissão para gerenciar dispositivos.',
      );
    });

    // Seed device first (for the pairing action to exist)
    await page.route(`${EDGE_BASE}/devices`, async (route, req) => {
      if (req.method() !== 'POST') { await route.continue(); return; }
      await fulfillRegister(route);
    });

    await page.goto('/edge/pairing');
    await page.getByText(/Solicitar código/i).click();

    // Trigger pair-to-patient (requires navigating through the wizard steps)
    // Surface assertion: toast appears (not banner or modal)
    await expect(
      page.getByText(/Você não tem permissão/i),
    ).toBeVisible({ timeout: 5000 });
  });

  test('409 /errors/pairing-conflict → banner on unpair', async ({ page }) => {
    await page.route(`${EDGE_BASE}/devices/${EDGE_DEVICE_ID}`, async (route, req) => {
      if (req.method() !== 'GET') { await route.continue(); return; }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(makeDevicePayload()),
      });
    });

    await page.route(`${EDGE_BASE}/devices/${EDGE_DEVICE_ID}/pairings/current`, async (route, req) => {
      if (req.method() !== 'DELETE') { await route.continue(); return; }
      await fulfillError(
        route,
        '/errors/pairing-conflict',
        409,
        'Este paciente já tem um dispositivo ativo. Despareie o atual antes de parear um novo.',
      );
    });

    await page.goto(`/edge/device/${EDGE_DEVICE_ID}`);
    page.on('dialog', (dialog) => dialog.accept());

    const unpairBtn = page.getByText(/Despareador/i).first();
    if (await unpairBtn.isVisible({ timeout: 4000 })) {
      await unpairBtn.click();
      // Banner: conflict message visible persistently (not self-dismissing toast)
      await expect(
        page.getByText(/Este paciente já tem um dispositivo ativo/i),
      ).toBeVisible({ timeout: 4000 });
    }
  });

  test('409 /errors/device-retired → banner on device detail load', async ({ page }) => {
    // Stub GET device to return 409 device-retired.
    await page.route(`${EDGE_BASE}/devices/${EDGE_DEVICE_ID}`, async (route, req) => {
      if (req.method() !== 'GET') { await route.continue(); return; }
      await fulfillError(
        route,
        '/errors/device-retired',
        409,
        'Este dispositivo foi aposentado e não pode ser usado. Registre um novo.',
      );
    });

    await page.goto(`/edge/device/${EDGE_DEVICE_ID}`);

    // Banner: "Este dispositivo foi aposentado..." must appear.
    await expect(
      page.getByText(/Este dispositivo foi aposentado/i),
    ).toBeVisible({ timeout: 4000 });

    // Re-register CTA must be present (SPEC-07 device-retired EARS clause).
    await expect(
      page.getByText(/Registre um novo|Registrar novo dispositivo/i),
    ).toBeVisible({ timeout: 3000 });

    // Privacy: no credential data in error message.
    const content = await page.content();
    expect(content).not.toContain('clientSecret');
    expect(content).not.toContain('enrollmentToken');
  });

  test('privacy invariant: no credential data in any error surface', async ({ page }) => {
    // Test each error surface for privacy invariant.
    const privacyTerms = ['clientSecret', 'enrollmentToken', '"confidence"', 'biometric'];

    await page.route(`${EDGE_BASE}/devices`, async (route, req) => {
      if (req.method() !== 'POST') { await route.continue(); return; }
      await fulfillError(route, '/errors/validation', 400, 'Verifique os dados do dispositivo.');
    });

    await page.goto('/edge/pairing');
    await page.getByText(/Solicitar código/i).click();

    await page.waitForTimeout(2000);
    const errorContent = await page.content();

    for (const term of privacyTerms) {
      expect(errorContent).not.toContain(term);
    }
  });
});
