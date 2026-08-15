/**
 * E2E spec: live-test-degraded-offline
 * Feature: frontend/e2e/live-test-degraded-offline.feature
 * Gate: A5 e2e-validate. Prototype-first (D-484), registered by
 * fe-nx-register-module.py (D-487).
 *
 * Verifies that when the device is offline, the gestDeviceOnlineGuard sets
 * the facade to degraded mode and the component renders the degraded banner
 * immediately — without requiring the caregiver to start a test.
 *
 * ADR-005 honest-degraded invariant: the degraded banner appears as soon as
 * degraded:true, so caregivers are never surprised mid-test.
 *
 * No camera needed — the degraded banner renders before any test is started.
 */
import { test, expect } from '@playwright/test';
import { gotoGest } from './gest-e2e-helpers';

test.describe('live-test-degraded-offline', () => {

  // ── Scenario 1 ───────────────────────────────────────────────────────────
  // BDD: "Device offline triggers degraded banner on page load without starting a test"
  // gestDeviceOnlineGuard checks navigator.onLine on activation → calls
  // facade.setDeviceOnline(false, 'device-offline') → degraded:true → banner visible.
  test('device offline shows degraded banner immediately on /gest load', async ({ page }) => {
    // Override navigator.onLine before Angular bootstraps
    await page.addInitScript(() => {
      Object.defineProperty(navigator, 'onLine', {
        configurable: true,
        get: () => false,
      });
    });

    await gotoGest(page);

    // Degraded banner must be visible (honest-degraded, ADR-005)
    // The banner text for 'device-offline' degradedReason:
    const degradedBanner = page.getByText(
      'Dispositivo offline — teste em modo simulado, resultado não é real.',
    );
    await expect(degradedBanner).toBeVisible({ timeout: 3_000 });

    // The banner must appear WITHOUT the caregiver clicking "Testar gesto"
    // (verifying it's driven by facade.degraded(), not by starting a test)
    await expect(page.getByRole('button', { name: 'Testar gesto' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Parar' })).not.toBeVisible();
  });

});
