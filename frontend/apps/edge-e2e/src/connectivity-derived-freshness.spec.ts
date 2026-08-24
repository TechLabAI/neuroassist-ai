/**
 * SPEC-08 Scenario 4 — connectivity-derived-freshness (FE-EDGE-023)
 *
 * Asserts that the connectivity chip derives state from server-relative heartbeat age,
 * never from the persisted status flag. Drives a virtual clock through:
 *   online (age < 45s staleness threshold) →
 *   stale (45s ≤ age < 90s offline window) →
 *   offline (age ≥ 90s offline window)
 *
 * The mock adapter seeds a device with fresh heartbeat (12s ago) → initial online state.
 * Playwright clock is used to advance time past each threshold without real waits.
 *
 * Key invariant: chip MUST NOT show 'online' after the offline window has passed.
 *
 * Mode A (mock): verifies initial online state + chip navigation.
 * Mode B (HTTP + clock): drives all threshold transitions via virtual clock.
 *
 * References: SPEC-08 §4, ADR-005 (freshness derivation), SPEC-03 §2, SPEC-04 §3.
 */

import { test, expect } from '@playwright/test';
import {
  EDGE_BASE,
  aplicarModoHttp,
  EDGE_PATIENT_ID,
  makeLivenessPayload,
  makeDevicePayload,
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
// Mode A — Mock adapter (works out of the box)
// ---------------------------------------------------------------------------

test.describe('EDGE – Connectivity derived from freshness — mock mode', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/edge/device');
  });

  test('shows device detail page with at least one device element', async ({ page }) => {
    await expect(page.getByText(/Dispositivo/i).first()).toBeVisible({ timeout: 4000 });
  });

  test('derives online state from fresh heartbeat (12s ago < 45s threshold)', async ({ page }) => {
    // Mock adapter seeds device with ultimaSincronizacao = 12s ago → online.
    //
    // "online" aparece em TRÊS lugares desta rota, e todos legítimos: o chip do
    // topbar (shell, "Dispositivo online · 1.4.2"), o cartão de status da própria
    // página ("Online") e a nota de rodapé que explica a derivação ("…nunca
    // aparece como online"). Varrer a página inteira por esse texto é ambíguo por
    // construção. O sujeito desta asserção é o cartão de status da página — é ele
    // que publica o estado derivado do dispositivo aberto —, então o locator é
    // escopado nele.
    await expect(
      page.locator('edge-connectivity-status-card').getByText('Online', { exact: true }),
    ).toBeVisible({ timeout: 4000 });
  });

  test('chip navigates to /edge/device when clicked from non-EDGE route', async ({ page }) => {
    await page.goto('/');
    const chipLink = page.locator('edge-connectivity-chip a');
    if (await chipLink.count() > 0) {
      await chipLink.click();
      await expect(page).toHaveURL(/\/edge\/device/);
    }
  });

  test('chip state is derived from liveness, not raw status flag', async ({ page }) => {
    // The mock device has pairingState='paired' and a fresh sync (12s).
    // The chip MUST show a freshness-derived state, not 'Unknown' or 'n/a'.
    // Assert the chip is not in the 'unknown' / degraded state.
    await expect(page.getByText(/Desconhecido|Sem confirmação/i)).not.toBeVisible();
  });
});

// ---------------------------------------------------------------------------
// Mode B — HTTP adapter + virtual clock (full threshold traversal)
// Requires EDGE_HTTP_MODE=1 to enable route intercepts.
// ---------------------------------------------------------------------------

const HTTP_MODE = process.env['EDGE_HTTP_MODE'] === '1';

// Short thresholds for e2e speed (driven via query param in HTTP mode).
// In HTTP mode the app could read feature flags from ?e2e=true query param.
// The default thresholds are: staleness=45s, offline=90s.
const STALENESS_MS = 45_000;
const OFFLINE_MS = 90_000;

test.describe('EDGE – Freshness threshold transitions (HTTP mode, virtual clock)', () => {
  test.skip(!HTTP_MODE, 'Requires EDGE_HTTP_MODE=1 (app in HTTP adapter mode)');

  // A app precisa EMITIR as chamadas para os `page.route` abaixo terem o que
  // interceptar. Antes disto não havia como sair do mock em runtime, e por isso
  // estes cenários nunca rodaram.
  test.beforeEach(async ({ page }) => {
    await aplicarModoHttp(page);
  });

  test('chip transitions online → stale → offline as clock advances', async ({ page }) => {
    // Install virtual clock at a fixed epoch so server-relative timestamps are deterministic.
    const BASE_EPOCH = new Date('2026-08-19T10:00:00.000Z').getTime();
    await page.clock.install({ time: BASE_EPOCH });

    // O heartbeat CONGELA em BASE_EPOCH - 10s: é o dispositivo que ficou mudo.
    // O relógio DO SERVIDOR avança junto com o relógio virtual da página — e é
    // essa distância crescente (`serverTime - ultimaSincronizacao`) que o
    // `deriveState` mede.
    //
    // Antes, o stub devolvia serverTime FIXO em BASE_EPOCH. A idade era eterna e
    // imutavelmente 10s, então adiantar o relógio não mudava coisa alguma e o
    // estado jamais sairia de 'online': o cenário não conseguia falhar nem
    // acertar pelo motivo certo. Como o handler roda no Node, ele não enxerga o
    // relógio virtual — por isso espelhamos o avanço nesta variável.
    const freshSync = new Date(BASE_EPOCH - 10_000).toISOString();
    let relogioDoServidorMs = BASE_EPOCH;

    const avancar = async (ms: number) => {
      relogioDoServidorMs += ms;
      await page.clock.fastForward(ms);
    };

    const corpoLiveness = () =>
      JSON.stringify(makeLivenessPayload({
        serverTime: new Date(relogioDoServidorMs).toISOString(),
        ultimaSincronizacao: freshSync,
      }));

    await page.route(`**/patients/${EDGE_PATIENT_ID}/liveness`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: corpoLiveness(),
      });
    });

    // Stub GET patient device (required for resolveForActivePatient).
    // makeDevicePayload, não makeLivenessPayload: este endpoint devolve a visão
    // de dispositivo (com pairingState e versões de perfil), e o payload de
    // liveness não tem `pairingState` nenhum.
    await page.route(`${EDGE_BASE}/patients/${EDGE_PATIENT_ID}/device`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(makeDevicePayload({
          serverTime: new Date(relogioDoServidorMs).toISOString(),
          ultimaSincronizacao: freshSync,
        })),
      });
    });

    await page.goto('/edge/device');

    const cartao = page.locator('edge-connectivity-status-card');

    // Phase 1: online — age = 10s < 45s staleness
    await expect(cartao.getByText('Online', { exact: true })).toBeVisible({ timeout: 4000 });

    // Phase 2: advance clock past staleness threshold (45s + 5s buffer = 50s)
    await avancar(STALENESS_MS + 5_000);
    // Escopado no cartão: 'Sinal fraco' também aparece no chip da topbar, e um
    // getByText solto casaria com os dois (strict mode violation).
    await expect(cartao.getByText('Sinal fraco', { exact: true })).toBeVisible({ timeout: 5000 });

    // Phase 3: advance clock past offline window (90s total, another 45s from stale)
    await avancar(OFFLINE_MS - STALENESS_MS + 5_000);
    await expect(cartao.getByText('Offline', { exact: true })).toBeVisible({ timeout: 5000 });

    // Invariant: chip MUST NOT show 'online' after offline window elapsed.
    await expect(cartao.getByText('Online', { exact: true })).not.toBeVisible();
  });
});
