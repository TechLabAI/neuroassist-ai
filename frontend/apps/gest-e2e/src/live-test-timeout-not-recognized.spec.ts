/**
 * E2E spec: live-test-timeout-not-recognized
 * Feature: frontend/e2e/live-test-timeout-not-recognized.feature
 * Gate: A5 e2e-validate. Prototype-first (D-484), registered by
 * fe-nx-register-module.py (D-487).
 *
 * Verifies that when no gesture is recognized within the allowed time window
 * (tempoMaximoGestoMs), the facade finalizes the run as not-recognized and
 * the component shows the "Tentar novamente" button.
 *
 * SPEC-04 §3 EARS:
 *   WHEN no signal within tempoMaximoGestoMs → state:'not-recognized'
 *
 * Timeout override:
 *   window.__GEST_E2E_TEMPO_MAX = 2500 (ms) — sets a short timeout on the
 *   GestDevHostComponent draft so the test completes in ~3 seconds instead
 *   of the default 8 seconds. Eram 1200ms: como o sinal 'degraded' do motor
 *   simulado chega aos 900ms, o degradado ficava visível por apenas 300ms
 *   antes do veredito sobrescrever a badge — janela curta demais para uma
 *   asserção honesta. 2500ms deixa 1,6s de janela.
 *
 * Sequence with the mock adapter (default honest-degraded path):
 *   0ms:    startTest — state:'testing'
 *   300ms:  preview_frame_ack (mock default)
 *   900ms:  degraded signal → degraded:true, banner appears
 *   2500ms: facade timeout fires → finalize(false) → state:'not-recognized'
 */
import { test, expect } from '@playwright/test';
import { gotoGest, mockCameraSuccess, usarMotorSimulado } from './gest-e2e-helpers';

test.describe('live-test-timeout-not-recognized', () => {

  // ── Scenario 1 ───────────────────────────────────────────────────────────
  // BDD: "No gesture within the time window results in not-recognized state"
  test('timeout expires with no recognized signal → not-recognized state with retry', async ({ page }) => {
    // Este cenário mede o caminho degradado honesto do motor SIMULADO
    // (ack aos 300ms, 'degraded' aos 900ms). Com o motor do produto (MediaPipe,
    // ADR-007) nada disso acontece contra uma webcam falsa.
    await usarMotorSimulado(page);
    // Short timeout: 2500ms (well below the default 8000ms)
    await page.addInitScript(() => {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      (window as any).__GEST_E2E_TEMPO_MAX = 2500;
    });
    await mockCameraSuccess(page);

    await gotoGest(page);

    // Start the gesture test
    await page.getByRole('button', { name: 'Testar gesto' }).click();

    // === Intermediate state: degraded banner at ~900ms ===
    // The default mock emits 'degraded' at 900ms, triggering the honest-degraded banner
    await expect(
      page.getByText('Dispositivo offline — teste em modo simulado, resultado não é real.').or(
        page.getByText('Agente de detecção indisponível. Teste em modo simulado — resultado não é real.'),
      ),
    ).toBeVisible({ timeout: 2_000 });

    // === Final state: not-recognized aos ~2500ms ===
    // Esperado por condição: o timeout do facade dispara sozinho.
    // "Tentar novamente" button must appear (shown for 'idle' | 'not-recognized' states)
    await expect(
      page.getByRole('button', { name: 'Tentar novamente' }),
    ).toBeVisible({ timeout: 5_000 });

    // Badge must show the not-recognized message
    await expect(
      page.getByText('Não reconhecido — repita o gesto ou aumente a sensibilidade.'),
    ).toBeVisible({ timeout: 2_000 });

    // "Parar" button must NOT be visible (test is no longer running)
    await expect(page.getByRole('button', { name: 'Parar' })).not.toBeVisible();
  });

});
