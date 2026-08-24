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
 *     ack (300ms) → recognized (600ms) → recognized (2000ms, for composite step 2)
 *
 * Camera note:
 *   getUserMedia mocked (no real webcam).
 */
import { test, expect } from '@playwright/test';
import { gotoGest, mockCameraSuccess, usarMotorSimulado } from './gest-e2e-helpers';

test.describe('live-test-composite', () => {

  // ── Scenario 1 ───────────────────────────────────────────────────────────
  // BDD: "A composite gesture test advances through both steps and reaches recognized"
  test('composite gesture advances step indicator and reaches recognized state', async ({ page }) => {
    // Sem o motor simulado a app usa o MediaPipe (ADR-007), que contra uma
    // webcam falsa nunca completa passo nenhum — e é o motor simulado que lê
    // __GEST_E2E_MOCK_RECOGNIZED.
    await usarMotorSimulado(page);
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

    // Aos 600ms o primeiro 'recognized' chega e o passo avança para 'secundario'.
    // O indicador só existe enquanto testState === 'testing', e o veredito final
    // (2000ms) o remove — por isso as duas asserções abaixo esperam por condição
    // e não por relógio: um waitForTimeout que passe do veredito mediria a
    // ausência do indicador, não o avanço de passo.
    const step2Span = page.locator('span', { hasText: '2. Gesto secundário' });
    await expect(step2Span).toBeVisible({ timeout: 3_000 });
    await expect(step2Span).toHaveClass(/font-bold/, { timeout: 3_000 });

    // Step 1 span should NOT be bold anymore
    const step1Span = page.locator('span', { hasText: '1. Gesto principal' });
    await expect(step1Span).not.toHaveClass(/font-bold/);

    // Aos 2000ms o segundo 'recognized' fecha o teste composto.
    // Final state: recognized — badge shows success message
    await expect(
      page.getByText('Gesto reconhecido com sucesso!'),
    ).toBeVisible({ timeout: 3_000 });

    // Composite step indicator disappears (only shown when testState === 'testing')
    await expect(step2Span).not.toBeVisible({ timeout: 2_000 });
  });

});
