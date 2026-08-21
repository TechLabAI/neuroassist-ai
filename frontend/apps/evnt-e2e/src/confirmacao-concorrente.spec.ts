/**
 * E2E spec: Confirmação Concorrente — 409 (FE-EVNT-028)
 *
 * Verifies that a 409 feedback-terminal response is treated as a success-equivalent:
 * the alarm overlay clears and no error state is shown (the other caregiver already
 * attended to the patient — the outcome is the same, just confirmed by someone else).
 *
 * E2E flag __EVNT_E2E_CONFIRM_409__:
 *   confirm() → throws { type: '/errors/feedback-terminal', confirmadoPor: 'Outra Cuidadora' }
 *   EventDeliveryFacade.confirm() catches this, sets _alreadyConfirmed = true and
 *   _activeEvent = null → overlay hides (success-equivalent path).
 *
 * IMPLEMENTATION NOTE — "Já confirmado por Outra Cuidadora" text:
 *   The plan expected this text to be visible AFTER the 409 response. However,
 *   AlarmOverlayComponent renders "Já confirmado por outra pessoa" INSIDE the
 *   @if (vm().visible && vm().event) block. Since _activeEvent = null causes
 *   vm().visible = false, the block hides before Angular renders the intermediate
 *   alreadyConfirmed state. The text is therefore not observable in the DOM.
 *   BLOCKER-UI: add a persistent "already confirmed" toast/banner outside the overlay
 *   to make this state testable (tracked: FE-EVNT-028-CONCURRENT-UI-GAP).
 *   For now, the test verifies the two observable outcomes: overlay cleared + no error.
 */
import { test, expect } from '@playwright/test';
import { navigateToEvntWithAlarm, SEL } from './evnt-e2e-helpers';

test.describe('Confirmação Concorrente — 409 feedback-terminal (FE-EVNT-028)', () => {

  // ── Scenario: 409 tratado como sucesso-equivalente ────────────────────────
  // BDD: "A 409 feedback-terminal response clears the alarm as a success-equivalent"
  //
  // UNBLOCKED (P9): page.addInitScript sets localStorage.__EVNT_E2E_CONFIRM_409__='true'
  // before Angular bootstraps. EvntClientMockAdapter.confirm() reads this flag inside
  // timer(600) and throws the feedback-terminal error. EventDeliveryFacade catches it
  // as a success-equivalent: _alreadyConfirmed = true, _activeEvent = null → overlay hides.
  test('409 (feedback-terminal) tratado como sucesso-equivalente — overlay limpo', async ({ page }) => {
    // Instruct the mock adapter to return 409 feedback-terminal on confirm()
    await page.addInitScript(() => {
      localStorage.setItem('__EVNT_E2E_CONFIRM_409__', 'true');
    });

    await navigateToEvntWithAlarm(page);
    await expect(page.locator(SEL.alarmOverlay)).toBeVisible();

    // Confirm the alarm — the 409 should be handled as success-equivalent
    const confirmBtn = page.locator('[role="alertdialog"]').getByRole('button', {
      name: /confirmar que atendi/i,
    });
    await expect(confirmBtn).toBeEnabled();
    await confirmBtn.click();

    // After 600 ms: 409 received → treated as success → _activeEvent = null → overlay hidden
    await expect(page.locator(SEL.alarmOverlay)).not.toBeVisible({ timeout: 4000 });

    // Success-equivalent: NO error message should appear (unlike the network error scenario)
    await expect(page.getByText(/erro ao confirmar/i)).not.toBeVisible();
  });

});
