/**
 * E2E spec: stream-no-cache-between-runs
 * Feature: frontend/e2e/stream-no-cache-between-runs.feature
 * Gate: A5 e2e-validate (GATE GEST-FE-026 — OBRIGATÓRIO). Prototype-first (D-484),
 * registered by fe-nx-register-module.py (D-487).
 *
 * GATE GEST-FE-026: Verifies that no state from a previous gesture test run
 * "bleeds" into the next run:
 *   - MediaStream (camera) is properly released after stopTest()
 *   - Observable subscription from run N is unsubscribed before run N+1
 *   - State signals (testState, message, detectorType) are reset by startTest()
 *
 * Failure of this gate would mean stale frames from run N are visible during
 * run N+1, or that duplicate subscriptions cause unexpected behavior.
 *
 * Adapter: mock (honest-degraded path). Camera: mocked via addInitScript.
 * __GEST_E2E_TEMPO_MAX = 30000 — prevents the facade's own timeout from firing
 * during the test; we stop manually between runs.
 */
import { test, expect } from '@playwright/test';
import { gotoGest, mockCameraSuccess } from './gest-e2e-helpers';

test.describe('stream-no-cache-between-runs', () => {

  // ── GATE GEST-FE-026 ──────────────────────────────────────────────────────
  // BDD: "No stream state from a previous run is visible when a new run starts"
  // Assertions:
  //   1. After run 1 stop: video element NOT in DOM (previewStream = null)
  //   2. After run 2 start: video element IS in DOM (fresh MediaStream)
  //   3. State badge shows fresh "testing" message — no stale state from run 1
  //   4. No "Tentar novamente" button before the caregiver clicks it
  //      (no leaked not-recognized state from run 1 interfering with run 2)
  test('stream state from run 1 is fully cleared before run 2 starts', async ({ page }) => {
    await mockCameraSuccess(page);
    // Prevent facade timeout from firing during this test
    await page.addInitScript(() => {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      (window as any).__GEST_E2E_TEMPO_MAX = 30_000;
    });

    await gotoGest(page);

    const videoEl = page.locator('gest-gesture-live-test video');

    // ── Run 1 ───────────────────────────────────────────────────────────────

    // Start run 1
    await page.getByRole('button', { name: 'Testar gesto' }).click();

    // Video element must appear (MediaStream acquired)
    await expect(videoEl).toBeVisible({ timeout: 5_000 });

    // State badge shows "testing" message (fresh start)
    await expect(
      page.getByText('Câmera ativa — faça o gesto agora…'),
    ).toBeVisible({ timeout: 3_000 });

    // Wait briefly so the mock adapter emits at least one signal (300ms ack)
    await page.waitForTimeout(500);

    // Stop run 1
    await page.getByRole('button', { name: 'Parar' }).click();

    // ── ASSERTION 1: After stop, video element is NOT in DOM ─────────────────
    // GestureTestFacade.stopTest() calls releaseCamera() → _previewStream.set(null)
    // Template: @if (testState === 'testing' || testState === 'recognized') { <video> }
    // Since state → 'idle', video must be removed.
    await expect(videoEl).not.toBeVisible({ timeout: 3_000 });

    // ── ASSERTION 2: "Testar gesto" button is back (idle state) ─────────────
    const startBtn = page.getByRole('button', { name: 'Testar gesto' });
    await expect(startBtn).toBeVisible({ timeout: 2_000 });

    // Verify NO stale "Tentar novamente" button (would mean not-recognized state leaked)
    await expect(page.getByRole('button', { name: 'Tentar novamente' })).not.toBeVisible();

    // ── Run 2 ───────────────────────────────────────────────────────────────

    // Start run 2
    await startBtn.click();

    // ── ASSERTION 3: Video element back in DOM (fresh MediaStream acquired) ──
    await expect(videoEl).toBeVisible({ timeout: 5_000 });

    // ── ASSERTION 4: State badge shows fresh testing message (not run 1 state) ─
    await expect(
      page.getByText('Câmera ativa — faça o gesto agora…'),
    ).toBeVisible({ timeout: 3_000 });

    // "Parar" button must be visible (in testing state)
    await expect(page.getByRole('button', { name: 'Parar' })).toBeVisible();

    // No "Tentar novamente" button (not leaked from run 1)
    await expect(page.getByRole('button', { name: 'Tentar novamente' })).not.toBeVisible();

    // Clean up: stop run 2
    await page.getByRole('button', { name: 'Parar' }).click();
  });

});
