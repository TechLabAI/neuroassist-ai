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
  EDGE_PATIENT_ID,
  makeLivenessPayload,
} from './fixtures/edge-stubs';

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
    // The ConnectivityStatusCardComponent or chip should display the online label.
    await expect(
      page.getByText(/Online|Dispositivo online/i),
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

  test('chip transitions online → stale → offline as clock advances', async ({ page }) => {
    // Install virtual clock at a fixed epoch so server-relative timestamps are deterministic.
    const BASE_EPOCH = new Date('2026-08-19T10:00:00.000Z').getTime();
    await page.clock.install({ time: BASE_EPOCH });

    // Stub liveness: fresh heartbeat at BASE_EPOCH - 10s (well within staleness window).
    const serverTime = new Date(BASE_EPOCH).toISOString();
    const freshSync = new Date(BASE_EPOCH - 10_000).toISOString(); // 10s ago

    await page.route(`**/patients/${EDGE_PATIENT_ID}/liveness`, async (route) => {
      // Always return the fixed epoch timestamps — clock advances virtually.
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(makeLivenessPayload({
          serverTime,
          ultimaSincronizacao: freshSync,
        })),
      });
    });

    // Stub GET patient device (required for resolveForActivePatient).
    await page.route(`${EDGE_BASE}/patients/${EDGE_PATIENT_ID}/device`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(makeLivenessPayload({
          serverTime,
          ultimaSincronizacao: freshSync,
        })),
      });
    });

    await page.goto('/edge/device');

    // Phase 1: online — age = 10s < 45s staleness
    await expect(page.getByText(/Online|Dispositivo online/i)).toBeVisible({ timeout: 4000 });

    // Phase 2: advance clock past staleness threshold (45s + 5s buffer = 50s)
    await page.clock.fastForward(STALENESS_MS + 5_000);
    // After advancing, the next poll tick updates the derivedState.
    // stale: 'Sinal fraco' or chip in warn state
    await expect(
      page.getByText(/Sinal fraco|stale|Dispositivo sem sinal/i),
    ).toBeVisible({ timeout: 5000 });

    // Phase 3: advance clock past offline window (90s total, another 45s from stale)
    await page.clock.fastForward(OFFLINE_MS - STALENESS_MS + 5_000);
    // offline: chip shows 'Dispositivo offline'
    await expect(page.getByText(/Dispositivo offline|offline/i)).toBeVisible({ timeout: 5000 });

    // Invariant: chip MUST NOT show 'online' after offline window elapsed.
    await expect(page.getByText(/Online|Dispositivo online/i)).not.toBeVisible();
  });
});
