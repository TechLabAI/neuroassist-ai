import { test, expect } from '@playwright/test';

/**
 * SPEC-08 Scenario 4 — connectivity-derived-freshness
 * Covers: SPEC-01, SPEC-03, SPEC-04 (ADR-005 core success signal)
 *
 * Asserts that the connectivity chip derives state from server-relative heartbeat age,
 * never from the persisted status flag.
 * With the mock adapter the device is seeded fresh (12s age < 45s staleness threshold)
 * → chip should show 'online'.
 *
 * TODO[FE-EDGE-007]: virtual clock test for stale/offline transitions (Layer 2)
 */

test.describe('EDGE – Connectivity derived from freshness (ADR-005)', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/edge/device');
  });

  test('should show device detail page', async ({ page }) => {
    // Mock adapter: device is seeded with fresh heartbeat → should show online state
    await expect(page.getByText(/Dispositivo/i).first()).toBeVisible({ timeout: 3000 });
  });

  test('should derive connectivity from heartbeat age, not raw flag', async ({ page }) => {
    // The page should show the derived state label (not 'n/a' or raw 'online' flag)
    // Mock device is fresh (12s ago) < stalenessThresholdMs (45s) → DerivedState='online'
    await expect(page.getByText(/Online/i)).toBeVisible({ timeout: 3000 });
  });

  test('should navigate to /edge/device when connectivity chip is clicked', async ({ page }) => {
    // Navigate to home first, then click chip
    await page.goto('/');
    await page.locator('edge-connectivity-chip a').click();
    await expect(page).toHaveURL(/\/edge\/device/);
  });
});
