/**
 * E2E spec: backend-error-handling
 * Feature: frontend/e2e/backend-error-handling.feature
 * Gate: A5 e2e-validate (structural). Prototype-first (D-484), registered by
 * fe-nx-register-module.py (D-487).
 *
 * Verifies that when the edge bridge stream errors (connection failure, timeout,
 * or protocol mismatch), the facade handles it gracefully:
 *   - State transitions to 'not-recognized' (not a crash)
 *   - Error message is shown in the badge
 *   - "Tentar novamente" button appears for recovery
 *
 * Adapter: mock with window.__GEST_E2E_MOCK_STREAM_ERROR = true
 *   → EdgeDetectionMockAdapter's observable throws at 400ms
 *   → Facade's error handler catches it and updates state
 *
 * Camera: mocked via addInitScript.
 *
 * Note: in adapter:'http' mode, this covers real WebSocket disconnection and
 * version-mismatch scenarios. In adapter:'mock' mode the error is simulated.
 */
import { test, expect } from '@playwright/test';
import { gotoGest, mockCameraSuccess, usarMotorSimulado } from './gest-e2e-helpers';

test.describe('backend-error-handling', () => {

  // ── Scenario 1 ───────────────────────────────────────────────────────────
  // BDD: "Edge bridge stream error shows error message in the state badge"
  // The facade's subscribeToStream error handler:
  //   error: () => {
  //     this._state.update({ state: 'not-recognized', message: 'Erro na conexão...' })
  //   }
  test('stream connection error transitions to not-recognized with error message', async ({ page }) => {
    // O erro de stream é um comportamento do adapter simulado; sem forçar o motor
    // simulado a app usa o MediaPipe do browser, que nunca erra nem reconhece
    // contra uma webcam falsa — e o cenário ficaria preso em 'testing'.
    await usarMotorSimulado(page);
    await page.addInitScript(() => {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      (window as any).__GEST_E2E_MOCK_STREAM_ERROR = true;
    });
    await mockCameraSuccess(page);

    await gotoGest(page);

    // Start the test to trigger the stream subscription
    await page.getByRole('button', { name: 'Testar gesto' }).click();

    // O adapter erra aos 400ms; esperar pela CONDIÇÃO, não por um relógio fixo.
    // Badge must show the connection error message
    await expect(
      page.getByText('Erro na conexão com o agente de detecção. Tente novamente.'),
    ).toBeVisible({ timeout: 3_000 });

    // "Tentar novamente" button must appear (not-recognized state)
    await expect(
      page.getByRole('button', { name: 'Tentar novamente' }),
    ).toBeVisible({ timeout: 2_000 });

    // "Parar" must NOT be visible (stream already ended via error)
    await expect(page.getByRole('button', { name: 'Parar' })).not.toBeVisible();
  });

  // ── Scenario 2 ───────────────────────────────────────────────────────────
  // BDD: "Tentar novamente after stream error re-initiates the test"
  test('retry after stream error starts a fresh test run', async ({ page }) => {
    await usarMotorSimulado(page);
    await page.addInitScript(() => {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      (window as any).__GEST_E2E_MOCK_STREAM_ERROR = true;
    });
    await mockCameraSuccess(page);

    await gotoGest(page);

    await page.getByRole('button', { name: 'Testar gesto' }).click();

    // Wait for not-recognized state
    await expect(
      page.getByRole('button', { name: 'Tentar novamente' }),
    ).toBeVisible({ timeout: 3_000 });

    // Click "Tentar novamente" — calls facade.retry() → startTest() again
    await page.getByRole('button', { name: 'Tentar novamente' }).click();

    // "não crashou" não prova que houve nova execução. O que prova é o ciclo
    // inteiro repetido: a app volta a 'testing' (botão Parar) e o mesmo erro
    // torna a derrubá-la em not-recognized. Tudo por condição — o botão Parar
    // aparece no clique e some ~400ms depois, quando o stream erra de novo.
    await expect(page.getByRole('button', { name: 'Parar' })).toBeVisible({ timeout: 2_000 });
    await expect(page.getByRole('button', { name: 'Parar' })).not.toBeVisible({ timeout: 3_000 });
    await expect(
      page.getByText('Erro na conexão com o agente de detecção. Tente novamente.'),
    ).toBeVisible({ timeout: 2_000 });
    await expect(
      page.getByRole('button', { name: 'Tentar novamente' }),
    ).toBeVisible({ timeout: 2_000 });
    // A página continua funcional (sem crash de JS):
    await expect(page.locator('gest-gesture-live-test')).toBeVisible();
  });

});
