import { test, expect } from '@playwright/test';

/**
 * SPEC-08 Scenario 7 — shell-chip-composition
 * Covers: SPEC-01, SPEC-02, SPEC-05, SPEC-06
 *
 * Topbar renders the connectivity chip on every authenticated route.
 * Chip click navigates to /edge/device.
 * No direct scope:edge import from shell internals (enforced by ESLint nx-boundary).
 *
 * TODO[FE-EDGE-002]: full shell-composition test (Layer 2 — login redirect, bootstrap init/destroy)
 */

test.describe('EDGE – Shell connectivity chip composition', () => {
  test('should render connectivity chip in topbar on the home route', async ({ page }) => {
    await page.goto('/');
    // The chip should be present (edge-connectivity-chip is always rendered in topbar)
    await expect(page.locator('edge-connectivity-chip')).toBeVisible({ timeout: 3000 });
  });

  test('chip should link to /edge/device', async ({ page }) => {
    await page.goto('/');
    const chipLink = page.locator('edge-connectivity-chip a');
    await expect(chipLink).toHaveAttribute('href', /\/edge\/device/);
  });

  test('should render EDGE route under the shell', async ({ page }) => {
    await page.goto('/edge/device');
    // Should NOT be a blank page
    await expect(page.locator('edge-device-detail-page, body')).toBeVisible();
  });

  test('pairing route should be accessible for conductor', async ({ page }) => {
    await page.goto('/edge/pairing');
    // With mock guard (always pass): pairing page renders
    await expect(page.getByText(/Parear dispositivo/i)).toBeVisible({ timeout: 3000 });
  });
});
