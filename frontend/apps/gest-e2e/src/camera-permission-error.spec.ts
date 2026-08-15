/**
 * E2E spec: camera-permission-error
 * Feature: frontend/e2e/camera-permission-error.feature
 * Gate: A5 e2e-validate. Prototype-first (D-484), registered by
 * fe-nx-register-module.py (D-487).
 *
 * Verifies that when the browser denies camera access (NotAllowedError),
 * GestureLiveTestComponent transitions to the camera-error state and
 * renders the .gest-error block with a clear message and retry button.
 *
 * GEST-FE-020: camera-error state shows an error block (not the generic badge)
 * with a "Tentar novamente" action that calls facade.retry().
 */
import { test, expect } from '@playwright/test';
import { gotoGest, mockCameraError } from './gest-e2e-helpers';

test.describe('camera-permission-error', () => {

  // ── Scenario 1 ───────────────────────────────────────────────────────────
  // BDD: "Camera access denied shows the camera-error block with error message"
  test('camera permission denied shows error block and descriptive message', async ({ page }) => {
    await mockCameraError(page);
    await gotoGest(page);

    // Start the test to trigger acquireCamera()
    await page.getByRole('button', { name: 'Testar gesto' }).click();

    // Camera error block (.gest-error) must appear — replaces generic badge
    const errorBlock = page.locator('.gest-error');
    await expect(errorBlock).toBeVisible({ timeout: 4_000 });

    // The error message must be meaningful to the caregiver
    await expect(errorBlock).toContainText(
      'Não foi possível acessar a webcam. Verifique a permissão do navegador e tente novamente.',
    );

    // The generic state badge must NOT be shown for camera-error state
    // (template: @if (facade.testState() !== 'camera-error') — badge removed from DOM)
    await expect(
      page.locator('gest-gesture-live-test .inline-flex.rounded-full'),
    ).not.toBeVisible({ timeout: 2_000 });
  });

  // ── Scenario 2 ───────────────────────────────────────────────────────────
  // BDD: "Camera-error state provides a Tentar novamente retry button"
  test('camera-error state shows the Tentar novamente retry button', async ({ page }) => {
    await mockCameraError(page);
    await gotoGest(page);

    await page.getByRole('button', { name: 'Testar gesto' }).click();

    // Camera error block must contain the retry button
    const errorBlock = page.locator('.gest-error');
    await expect(errorBlock).toBeVisible({ timeout: 4_000 });

    const retryBtn = errorBlock.getByRole('button', { name: 'Tentar novamente' });
    await expect(retryBtn).toBeVisible();

    // The retry button triggers facade.retry() → startTest() again
    // (with camera still denied, this will loop back to camera-error — same block)
    await retryBtn.click();
    await expect(errorBlock).toBeVisible({ timeout: 4_000 });
  });

});
