/**
 * E2E spec: Confirmação de Atendimento (FE-EVNT-027)
 *
 * Verifies the primary safety-critical path: confirming an alarm clears the overlay.
 * Also verifies that a network failure keeps the overlay + shows an error message.
 *
 * Mock seed state (default, no flags):
 *   confirm() → succeeds after 600 ms mock timer → _activeEvent = null → overlay hidden.
 *
 * E2E flag __EVNT_E2E_CONFIRM_FAIL__:
 *   confirm() → throws immediately after 600 ms → _confirmError set → overlay stays.
 *
 * NON-OPTIMISTIC invariant (SPEC-04 §5):
 *   AlarmOverlayComponent clears ONLY after POST /confirmation 200.
 *   No premature dismiss — overlay stays visible while confirming (spinner shown).
 */
import { test, expect } from '@playwright/test';
import { navigateToEvntWithAlarm, SEL } from './evnt-e2e-helpers';

test.describe('Confirmação de Atendimento (FE-EVNT-027)', () => {

  // ── Scenario 1: Confirmação bem-sucedida limpa o alarme ──────────────────
  // BDD: "A successful confirmation clears the alarm overlay (non-optimistic)"
  test('Confirmação bem-sucedida limpa o alarme', async ({ page }) => {
    await navigateToEvntWithAlarm(page);
    await expect(page.locator(SEL.alarmOverlay)).toBeVisible();

    // Click the confirm button inside the alertdialog
    const confirmBtn = page.locator('[role="alertdialog"]').getByRole('button', {
      name: /confirmar que atendi/i,
    });
    await expect(confirmBtn).toBeEnabled();
    await confirmBtn.click();

    // While confirming: overlay stays + "Confirmando…" spinner (CONCUR-01 guard)
    // Then after 600 ms mock timer: _activeEvent = null → overlay hidden
    await expect(page.locator(SEL.alarmOverlay)).not.toBeVisible({ timeout: 4000 });

    // No error message after successful confirm
    await expect(page.getByText(/erro ao confirmar/i)).not.toBeVisible();
  });

  // ── Scenario 2: Falha de rede mantém o alarme + erro inline ──────────────
  // BDD: "A network failure keeps the alarm overlay visible and shows an inline error"
  //
  // UNBLOCKED (P9): page.addInitScript sets localStorage.__EVNT_E2E_CONFIRM_FAIL__='true'
  // before Angular bootstraps. EvntClientMockAdapter.confirm() reads this flag inside
  // timer(600) and throws, causing EventDeliveryFacade.confirm() to set _confirmError
  // signal → AlarmOverlayComponent renders error message without clearing the overlay.
  test('Falha de rede mantém o alarme + erro inline', async ({ page }) => {
    // Instruct the mock adapter to throw on confirm() — simulates network failure.
    await page.addInitScript(() => {
      localStorage.setItem('__EVNT_E2E_CONFIRM_FAIL__', 'true');
    });

    await navigateToEvntWithAlarm(page);
    await expect(page.locator(SEL.alarmOverlay)).toBeVisible();

    const confirmBtn = page.locator('[role="alertdialog"]').getByRole('button', {
      name: /confirmar que atendi/i,
    });
    await expect(confirmBtn).toBeEnabled();
    await confirmBtn.click();

    // After 600 ms: confirm throws → _confirmError set → error message visible
    await expect(page.getByText(/erro ao confirmar/i)).toBeVisible({ timeout: 4000 });

    // Overlay must remain visible (alarm not cleared on error — safety invariant)
    await expect(page.locator(SEL.alarmOverlay)).toBeVisible();
  });

});
