/**
 * E2E spec: Notificação de Rotina — discreet, dismissível (FE-EVNT-029)
 *
 * Verifies that routine (non-emergency) events are rendered as discrete notices —
 * not as a full-screen alarm overlay. Also verifies that dismissing a notice
 * removes it from the queue without a page reload (client-only, optimistic dismiss).
 *
 * E2E flag __EVNT_E2E_NO_EMERGENCY__:
 *   listActive() → { activeEvent: null, events: [MOCK_ROUTINE_1, MOCK_ROUTINE_2] }
 *   evntBootstrap.init() hydrate() → _activeEvent = null, _routineQueue = [r1, r2]
 *   EventFeedPageComponent renders 2 × RoutineNoticeComponent (data-testid="routine-notice").
 *   AlarmOverlayComponent is hidden (vm().visible = false — no emergency active).
 *
 * Mock seed routines:
 *   evt-002: urgencia='rotina', commandLabel='Água', feedback='nao_avaliado'
 *   evt-003: urgencia='rotina', commandLabel='Coberta', feedback='nao_avaliado'
 *
 * FLAG-02 (FE-EVNT-024): routineNoticesEnabled() defaults to true — notices visible.
 */
import { test, expect } from '@playwright/test';
import { navigateToEvntNoEmergency, SEL } from './evnt-e2e-helpers';

test.describe('Notificação de Rotina — discreta e dismissível (FE-EVNT-029)', () => {

  // ── Scenario 1: Notificação discreta não full-bleed ───────────────────────
  // BDD: "Routine events render as discrete notices, not as a full-screen alarm overlay"
  test('Notificação de rotina é discreta e o alarme overlay está ausente', async ({ page }) => {
    // Suppress the emergency event — only routines in listActive()
    await page.addInitScript(() => {
      localStorage.setItem('__EVNT_E2E_NO_EMERGENCY__', 'true');
    });

    await navigateToEvntNoEmergency(page);

    // Routine notices must be visible (2 from seed: 'Água' + 'Coberta')
    await expect(page.locator(SEL.routineNotice)).toHaveCount(2, { timeout: 3000 });
    await expect(page.locator(SEL.routineNotice).first()).toBeVisible();

    // Alarm overlay must NOT be present (no emergency active)
    await expect(page.locator(SEL.alarmOverlay)).not.toBeVisible();
  });

  // ── Scenario 2: Dismiss remove notificação (client-only, optimistic) ──────
  // BDD: "Dismissing a routine notice removes it from the queue without a page reload"
  test('Dismiss remove a notificação de rotina sem reload', async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem('__EVNT_E2E_NO_EMERGENCY__', 'true');
    });

    await navigateToEvntNoEmergency(page);

    // Wait for 2 notices to render
    await expect(page.locator(SEL.routineNotice)).toHaveCount(2, { timeout: 3000 });

    // Click the dismiss (Dispensar) button on the FIRST notice
    const firstNotice = page.locator(SEL.routineNotice).first();
    await firstNotice.getByRole('button', { name: /dispensar notificação/i }).click();

    // After dismiss: only 1 notice remains (client-only, no reload)
    await expect(page.locator(SEL.routineNotice)).toHaveCount(1, { timeout: 2000 });

    // Alarm overlay still absent
    await expect(page.locator(SEL.alarmOverlay)).not.toBeVisible();
  });

});
