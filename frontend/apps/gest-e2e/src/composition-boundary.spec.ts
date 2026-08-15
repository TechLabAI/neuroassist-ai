/**
 * E2E spec: composition-boundary
 * Feature: frontend/e2e/composition-boundary.feature
 * Gate: A5 e2e-validate (structural). Prototype-first (D-484), registered by
 * fe-nx-register-module.py (D-487).
 *
 * Verifies that GestureTestFacade is correctly composed inside GestDevHostComponent
 * at the /gest thin-host route, and that the initial UI state is correct.
 * Does NOT require a real camera — navigation + initial render only.
 */
import { test, expect } from '@playwright/test';
import { gotoGest } from './gest-e2e-helpers';

test.describe('composition-boundary', () => {

  // ── Scenario 1 ───────────────────────────────────────────────────────────
  // BDD: "The GEST dev host renders the gesture-live-test harness at /gest"
  // Verifies: route active, component composed, heading visible.
  test('dev host renders the gesture-live-test harness at /gest', async ({ page }) => {
    await gotoGest(page);

    // The GestDevHostComponent h1 heading must be present
    await expect(page.locator('h1')).toContainText('GEST — dev host (live-test harness)');

    // GestureLiveTestComponent must be embedded (selector in DOM)
    await expect(page.locator('gest-gesture-live-test')).toBeVisible();

    // The live-test container must render
    await expect(page.locator('.gest-live-test')).toBeVisible();
  });

  // ── Scenario 2 ───────────────────────────────────────────────────────────
  // BDD: "Empty state appears when facade has no active commandDraftRef"
  // The facade starts with commandDraftRef:'' and testState:'idle' → empty state
  // message appears, Testar gesto button is present.
  test('empty state and start button are visible before first test', async ({ page }) => {
    await gotoGest(page);

    // Empty state: facade.state().commandDraftRef is '' initially → empty copy visible
    const emptyMsg = page.locator('.gest-empty');
    await expect(emptyMsg).toBeVisible();
    await expect(emptyMsg).toContainText('Selecione um comando para iniciar o teste de gesto.');

    // "Testar gesto" start button must be present (idle state)
    await expect(page.getByRole('button', { name: 'Testar gesto' })).toBeVisible();
  });

});
