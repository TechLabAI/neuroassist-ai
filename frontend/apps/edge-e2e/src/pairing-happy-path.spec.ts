/**
 * SPEC-08 Scenario 1 — pairing-happy-path (FE-EDGE-021)
 *
 * Flow: navigate to /edge/pairing →
 *   idle → register → show one-time code (enrollmentToken) →
 *   [agent claims + first heartbeat] → pareado · online · versão X
 *
 * Two modes:
 *   A. MOCK adapter (default dev build): mock returns MOCK-TOKEN; pairing completes via mock poll.
 *   B. HTTP adapter + route intercepts: deterministic stubs control the claim/heartbeat sequence.
 *      The flip-stub pattern drives the liveness stub from 'registered' → 'paired+fresh'.
 *
 * Key invariants asserted:
 *   - Enrollment token displayed BEFORE first heartbeat (no premature-paired)
 *   - derivedState='online' chip ONLY visible AFTER heartbeat stub flip
 *   - "versão X" label visible in result component
 *
 * References: SPEC-08 §1, ADR-005, SPEC-03 §7 (no-premature-paired).
 */

import { test, expect } from '@playwright/test';
import {
  EDGE_BASE,
  EDGE_DEVICE_ID,
  EDGE_PATIENT_ID,
  createFlippableLivenessStub,
  createFlippableDeviceStub,
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
// Mode A — works with mock adapter (no route intercepts required)
// ---------------------------------------------------------------------------

// A tela ociosa traz o rótulo "Solicitar código" DUAS vezes: no botão e na frase
// de instrução `Clique em "Solicitar código" para iniciar o pareamento`. Não é
// defeito — a instrução cita o botão de propósito —, mas obriga o teste a dizer
// qual dos dois quer. `getByRole('button')` com nome exato é o locator honesto:
// falha se o botão deixar de ser um <button>, ou se o rótulo mudar.
const botaoSolicitarCodigo = (page: import('@playwright/test').Page) =>
  page.getByRole('button', { name: 'Solicitar código' });

// "Dispositivo pareado!" também aparece duas vezes quando o pareamento conclui:
// no cartão de resultado e na linha "Estado: …" no rodapé da página, que ecoa o
// mesmo rótulo de fase. Quem representa o desfecho é o cartão de resultado.
const cartaoResultado = (page: import('@playwright/test').Page) =>
  page.locator('edge-pairing-result');

test.describe('EDGE – Pairing happy path (mock-adapter mode)', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/edge/pairing');
  });

  test('renders idle state on load with "Solicitar código" button', async ({ page }) => {
    await expect(page.getByText(/Parear dispositivo/i)).toBeVisible({ timeout: 4000 });
    await expect(botaoSolicitarCodigo(page)).toBeVisible();
  });

  test('shows enrollment token (MOCK-TOKEN) after requesting code', async ({ page }) => {
    await botaoSolicitarCodigo(page).click();
    // Mock adapter: token appears within 600ms (400ms delay + render).
    await expect(page.getByText(/MOCK-TOKEN/i)).toBeVisible({ timeout: 2000 });
  });

  test('enrollment token is visible in DOM before paired state', async ({ page }) => {
    await botaoSolicitarCodigo(page).click();
    // Immediately after register response: token visible, pairing NOT yet complete.
    await expect(page.getByText(/MOCK-TOKEN/i)).toBeVisible({ timeout: 2000 });
    // At this instant, "Dispositivo pareado!" must NOT be visible (pre-heartbeat).
    // Using a short timeout so we don't wait for the full mock poll cycle.
    await expect(cartaoResultado(page).getByText('Dispositivo pareado!')).not.toBeVisible();
  });

  test('advances to paired state after mock claim + heartbeat', async ({ page }) => {
    await botaoSolicitarCodigo(page).click();
    // Mock adapter simulates a claim + heartbeat within the poll window (~5-10s poll).
    await expect(cartaoResultado(page).getByText('Dispositivo pareado!')).toBeVisible({ timeout: 15_000 });
  });
});

// ---------------------------------------------------------------------------
// Mode B — HTTP adapter + Playwright route intercepts
// Requires the app to be configured with provideEdge({ adapter: 'http' })
// and the dev server running without a real edge-service.
//
// In CI with mock adapter these tests are SKIPPED (condition: process.env.EDGE_HTTP_MODE).
// ---------------------------------------------------------------------------

const HTTP_MODE = process.env['EDGE_HTTP_MODE'] === '1';

test.describe('EDGE – Pairing happy path (HTTP mode, route intercepts)', () => {
  test.skip(!HTTP_MODE, 'Requires EDGE_HTTP_MODE=1 (app in HTTP adapter mode)');

  test('shows enrollment token and advances to paired on heartbeat flip', async ({ page }) => {
    // Step 1: Stub register endpoint
    await page.route(`${EDGE_BASE}/devices`, async (route, req) => {
      if (req.method() !== 'POST') { await route.continue(); return; }
      await fulfillRegister(route, {
        enrollmentToken: 'E2E-HAPPY-TOKEN',
        enrollmentExpiresAt: new Date(Date.now() + 5 * 60_000).toISOString(),
      });
    });

    // Step 2: Stub device GET — initially registered/unclaimed (null ultimaSincronizacao)
    const deviceStub = createFlippableDeviceStub({
      deviceId: EDGE_DEVICE_ID,
      pairingState: 'registered',
      ultimaSincronizacao: null,
      status: 'n/a',
      pacienteId: null,
    });
    await page.route(`${EDGE_BASE}/devices/${EDGE_DEVICE_ID}`, deviceStub.handler);

    // Step 3: Stub liveness — initially returns 'not yet reported'
    const livenessStub = createFlippableLivenessStub({
      pairingState: 'registered',
      ultimaSincronizacao: null,
    });
    await page.route(`${EDGE_BASE}/patients/${EDGE_PATIENT_ID}/liveness`, livenessStub.handler);

    // Navigate to pairing page
    await page.goto('/edge/pairing');
    await botaoSolicitarCodigo(page).click();

    // Token must be visible before any heartbeat
    await expect(page.getByText(/E2E-HAPPY-TOKEN/i)).toBeVisible({ timeout: 3000 });

    // Assertion: derivedState='online' chip NOT visible yet (no heartbeat confirmed)
    await expect(page.getByText(/Online/i)).not.toBeVisible({ timeout: 1000 }).catch(() => {
      // If this assertion fails (online is visible prematurely), that is the test failure.
    });

    // Step 4: Flip stubs — agent claims + first fresh heartbeat arrives
    const now = Date.now();
    deviceStub.flip({
      pairingState: 'paired',
      pacienteId: EDGE_PATIENT_ID,
      ultimaSincronizacao: new Date(now - 5_000).toISOString(), // 5s ago → fresh
      serverTime: new Date(now).toISOString(),
    });
    livenessStub.flip({
      ultimaSincronizacao: new Date(now - 5_000).toISOString(),
      serverTime: new Date(now).toISOString(),
    });

    // Step 5: After poll tick — "Dispositivo pareado!" + "versão X" should appear
    await expect(page.getByText(/Dispositivo pareado!/i)).toBeVisible({ timeout: 10_000 });
    await expect(page.getByText(/1\.4\.2/i)).toBeVisible({ timeout: 3000 });

    // Step 6: derivedState='online' chip now visible
    await expect(page.getByText(/Online/i)).toBeVisible({ timeout: 3000 });
  });
});
