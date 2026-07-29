import { test, expect } from '@playwright/test';

/**
 * SPEC-08 Scenario 6 — device-repair-unpair
 * Covers: SPEC-03, SPEC-04, SPEC-05, SPEC-07
 *
 * Conductor can re-pair / unpair / retire device.
 * Unpair MUST refresh from server (no optimistic flip — SPEC-04 §3).
 * Caregiver session: management actions are HIDDEN (not disabled).
 *
 * TODO[FE-EDGE-015]: implement re-pair action test (Layer 2)
 * TODO[FE-EDGE-016]: implement retire confirmation dialog (Layer 2)
 */

test.describe('EDGE – Device management (re-pair / unpair / retire)', () => {
  test('should show management actions for conductor (mock: canManage=true)', async ({ page }) => {
    await page.goto('/edge/device');
    // Mock: canManage=true → management buttons visible
    await expect(page.getByText(/Reparear/i)).toBeVisible({ timeout: 3000 });
    await expect(page.getByText(/Despareador/i)).toBeVisible();
    await expect(page.getByText(/Aposentar/i)).toBeVisible();
  });

  test('unpair should show confirmation dialog', async ({ page }) => {
    await page.goto('/edge/device');
    await page.getByText(/Despareador/i).first().click();
    // Confirmation dialog should appear
    await expect(page.getByText(/Despareador dispositivo\?/i)).toBeVisible();
  });

  test('unpair cancel should dismiss without action', async ({ page }) => {
    await page.goto('/edge/device');
    await page.getByText(/Despareador/i).first().click();
    await page.getByText(/Cancelar/i).click();
    // Dialog should disappear
    await expect(page.getByText(/Despareador dispositivo\?/i)).not.toBeVisible();
  });
});
