/**
 * E2E spec: live-test-composite
 * Feature: frontend/e2e/live-test-composite.feature
 * Gate: A5 e2e-validate. Prototype-first (D-484), registered by
 * fe-nx-register-module.py (D-487).
 *
 * Verifies the two-step composite gesture flow:
 *   Step 1 (principal) → recognized → step indicator advances to secundario
 *   Step 2 (secundario) → recognized → final recognized state
 *
 * Adapter note:
 *   window.__GEST_E2E_COMPOSITE = true  → GestDevHostComponent sets composite:true on the draft
 *   window.__GEST_E2E_MOCK_RECOGNIZED = true  → EdgeDetectionMockAdapter emits:
 *     ack (300ms) → recognized (600ms) → recognized (1200ms, for composite step 2)
 *
 * Camera note:
 *   getUserMedia mocked (no real webcam).
 */
import { test, expect } from '@playwright/test';
import { gotoGest, mockCameraSuccess } from './gest-e2e-helpers';

test.describe('live-test-composite', () => {

  // ── Scenario 1 ───────────────────────────────────────────────────────────
  // BDD: "A composite gesture test advances through both steps and reaches recognized"
  test('composite gesture advances step indicator and reaches recognized state', async ({ page }) => {
    await page.addInitScript(() => {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      (window as any).__GEST_E2E_COMPOSITE = true;
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      (window as any).__GEST_E2E_MOCK_RECOGNIZED = true;
    });
    await mockCameraSuccess(page);

    await gotoGest(page);

    // Click "Testar gesto"
    await page.getByRole('button', { name: 'Testar gesto' }).click();

    // At ~700ms: first recognized signal fires (600ms) → step advances to 'secundario'
    // Step indicator is visible while testState === 'testing'
    await page.waitForTimeout(700);

    // Step 2 span ("2. Gesto secundário") should now be bold (class.font-bold = true)
    const step2Span = page.locator('span', { hasText: '2. Gesto secundário' });
    await expect(step2Span).toBeVisible({ timeout: 2_000 });
    await expect(step2Span).toHaveClass(/font-bold/);

    // Step 1 span should NOT be bold anymore
    const step1Span = page.locator('span', { hasText: '1. Gesto principal' });
    await expect(step1Span).not.toHaveClass(/font-bold/);

    // At ~1400ms: second recognized signal fires (1200ms) → final recognized
    await page.waitForTimeout(800);

    // Final state: recognized — badge shows success message
    await expect(
      page.getByText('Gesto reconhecido com sucesso!'),
    ).toBeVisible({ timeout: 3_000 });

    // Composite step indicator disappears (only shown when testState === 'testing')
    await expect(step2Span).not.toBeVisible({ timeout: 2_000 });
  });

});
