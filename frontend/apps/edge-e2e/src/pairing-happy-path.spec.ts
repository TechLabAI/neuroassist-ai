import { test, expect } from '@playwright/test';

/**
 * SPEC-08 Scenario 1 — pairing-happy-path
 * Covers: SPEC-01, SPEC-03, SPEC-04, SPEC-06
 *
 * Flow: navigate to /edge/pairing →
 *   idle → request code (register) → awaiting-claim (token shown) →
 *   agent claims + first heartbeat → paired · online · version X
 *
 * TODO[FE-EDGE-006]: implement real pairing UI spec (Layer 2 — /fe-validator FE-EDGE-006)
 */

test.describe('EDGE – Pairing happy path', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/edge/pairing');
  });

  test('should render the idle state on load', async ({ page }) => {
    await expect(page.getByText(/Parear dispositivo/i)).toBeVisible();
    await expect(page.getByText(/Solicitar código/i)).toBeVisible();
  });

  test('should show enrollment token when code is requested', async ({ page }) => {
    await page.getByText(/Solicitar código/i).click();
    // With mock adapter: token should appear within 600ms (400ms delay + render)
    await expect(page.getByText(/MOCK-TOKEN/i)).toBeVisible({ timeout: 2000 });
  });

  test('should advance to paired state after mock claim + heartbeat', async ({ page }) => {
    await page.getByText(/Solicitar código/i).click();
    // Mock adapter simulates a claim + first heartbeat within polling window
    // Wait for "Dispositivo pareado!" to appear (mock: ~5-10s poll)
    await expect(page.getByText(/Dispositivo pareado!/i)).toBeVisible({ timeout: 15_000 });
  });
});
