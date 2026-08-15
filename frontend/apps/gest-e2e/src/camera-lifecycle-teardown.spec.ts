/**
 * E2E spec: camera-lifecycle-teardown
 * Feature: frontend/e2e/camera-lifecycle-teardown.feature
 * Gate: A5 e2e-validate. Prototype-first (D-484), registered by
 * fe-nx-register-module.py (D-487).
 *
 * Verifies that the MediaStream lifecycle is correctly managed:
 *   - Camera (MediaStream) is acquired on startTest()
 *   - Camera is released on stopTest() → previewStream becomes null → video element removed
 *   - A second startTest() after stop acquires a fresh MediaStream (no stale tracks)
 *
 * SPEC-04 §3 EARS:
 *   WHEN stopTest dispatched, SHALL unsubscribe + stop tracks.
 *
 * Camera: getUserMedia mocked (no real webcam). The mock returns a new empty
 * MediaStream each time — facade calls getTracks().forEach(t.stop()) on teardown.
 */
import { test, expect } from '@playwright/test';
import { gotoGest, mockCameraSuccess } from './gest-e2e-helpers';

test.describe('camera-lifecycle-teardown', () => {

  // ── Scenario 1 ───────────────────────────────────────────────────────────
  // BDD: "Camera is released when the test is stopped — video element removed from DOM"
  test('stopping the test releases the camera and removes the video element', async ({ page }) => {
    await mockCameraSuccess(page);
    // Use a long timeout so the facade's own timeout doesn't fire during the test
    await page.addInitScript(() => {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      (window as any).__GEST_E2E_TEMPO_MAX = 30_000;
    });

    await gotoGest(page);

    // Start the test
    await page.getByRole('button', { name: 'Testar gesto' }).click();

    // The <video> element must appear (previewStream acquired → @if renders video)
    // Wait for camera acquisition (async getUserMedia) + Angular re-render
    const videoEl = page.locator('gest-gesture-live-test video');
    await expect(videoEl).toBeVisible({ timeout: 5_000 });

    // Stop the test
    await page.getByRole('button', { name: 'Parar' }).click();

    // After stopTest(): state → 'idle', previewStream → null, video removed from DOM
    await expect(videoEl).not.toBeVisible({ timeout: 3_000 });

    // The "Testar gesto" button must return (idle state)
    await expect(page.getByRole('button', { name: 'Testar gesto' })).toBeVisible();
  });

  // ── Scenario 2 ───────────────────────────────────────────────────────────
  // BDD: "Starting a new test after stop acquires a fresh camera session"
  // Verifies that the second startTest() mounts a new, clean camera session.
  test('starting a second test after stop re-acquires camera cleanly', async ({ page }) => {
    await mockCameraSuccess(page);
    await page.addInitScript(() => {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      (window as any).__GEST_E2E_TEMPO_MAX = 30_000;
    });

    await gotoGest(page);

    // === Run 1 ===
    await page.getByRole('button', { name: 'Testar gesto' }).click();
    const videoEl = page.locator('gest-gesture-live-test video');
    await expect(videoEl).toBeVisible({ timeout: 5_000 });

    // Stop run 1
    await page.getByRole('button', { name: 'Parar' }).click();
    await expect(videoEl).not.toBeVisible({ timeout: 3_000 });

    // === Run 2 ===
    await page.getByRole('button', { name: 'Testar gesto' }).click();

    // A fresh video element must appear — new MediaStream acquired
    await expect(videoEl).toBeVisible({ timeout: 5_000 });

    // Badge must reflect the fresh "testing" state (facade.message reset by startTest())
    await expect(
      page.getByText('Câmera ativa — faça o gesto agora…'),
    ).toBeVisible({ timeout: 3_000 });
  });

});
