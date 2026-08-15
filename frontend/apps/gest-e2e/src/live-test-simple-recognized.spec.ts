/**
 * E2E spec: live-test-simple-recognized
 * Feature: frontend/e2e/live-test-simple-recognized.feature
 * Gate: A5 e2e-validate. Prototype-first (D-484), registered by
 * fe-nx-register-module.py (D-487).
 *
 * Verifies the happy path: caregiver starts a simple gesture test and the
 * edge agent recognizes the gesture.
 *
 * Adapter note:
 *   Runs with adapter:'mock' (window.__GEST_E2E_MOCK_RECOGNIZED=true) which
 *   bypasses the honest-degraded default path of EdgeDetectionMockAdapter.
 *   The fast recognition path emits: ack (300ms) → recognized (600ms).
 *
 * Camera note:
 *   getUserMedia is mocked via addInitScript (no real webcam needed).
 */
import { test, expect } from '@playwright/test';
import { gotoGest, mockCameraSuccess } from './gest-e2e-helpers';

test.describe('live-test-simple-recognized', () => {

  // ── Scenario 1 ───────────────────────────────────────────────────────────
  // BDD: "A simple gesture test reaches the recognized state"
  // Expectation: badge shows "Gesto reconhecido com sucesso!" with green styling.
  test('simple gesture test reaches recognized state and shows success message', async ({ page }) => {
    // Enable the mock recognition path in EdgeDetectionMockAdapter
    await page.addInitScript(() => {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      (window as any).__GEST_E2E_MOCK_RECOGNIZED = true;
    });
    await mockCameraSuccess(page);

    await gotoGest(page);

    // Click "Testar gesto" to start the live test
    await page.getByRole('button', { name: 'Testar gesto' }).click();

    // Wait for the recognized signal (600ms from mock) + Angular re-render buffer
    await page.waitForTimeout(900);

    // Badge must show "Gesto reconhecido com sucesso!"
    await expect(
      page.getByText('Gesto reconhecido com sucesso!'),
    ).toBeVisible({ timeout: 3_000 });

    // "Testar gesto" button reappears (recognized state allows restart)
    // OR "Tentar novamente" — for recognized, button label is "Testar gesto" (idle/not-recognized)
    // Actually recognized → the button says "Tentar novamente"? Let me re-check the template:
    // @if (facade.testState() === 'idle' || facade.testState() === 'not-recognized') { button }
    // Recognized state does NOT show the restart button — the caregiver can navigate away.
    // So no button should be visible for restart in recognized state.
    await expect(page.getByRole('button', { name: 'Parar' })).not.toBeVisible();
  });

  // ── Scenario 2 ───────────────────────────────────────────────────────────
  // BDD: "After recognition the outcome JSON is shown in the dev host"
  // GestDevHostComponent renders the outcome as JSON below the live-test.
  test('dev host displays the gesture outcome JSON after recognition', async ({ page }) => {
    await page.addInitScript(() => {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      (window as any).__GEST_E2E_MOCK_RECOGNIZED = true;
    });
    await mockCameraSuccess(page);

    await gotoGest(page);

    await page.getByRole('button', { name: 'Testar gesto' }).click();
    await page.waitForTimeout(900);

    // Outcome is rendered as <pre> JSON below gest-gesture-live-test
    const outcomeBlock = page.locator('pre');
    await expect(outcomeBlock).toBeVisible({ timeout: 3_000 });

    const preText = await outcomeBlock.innerText();
    // Outcome must include recognized:true
    expect(preText).toContain('"recognized": true');
    // Outcome must include degraded:false (non-degraded path)
    expect(preText).toContain('"degraded": false');
  });

});
