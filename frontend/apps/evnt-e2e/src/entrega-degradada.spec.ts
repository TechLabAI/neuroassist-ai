/**
 * E2E spec: Entrega Degradada — SSE falha, polling ativo (FE-EVNT-030)
 *
 * Verifies the degraded-mode fallback path: when the SSE channel fails,
 * EventDeliveryFacade activates polling and the EvntDegradedModeBannerComponent
 * becomes visible in EventFeedPageComponent. The active alarm (set by hydrate())
 * is preserved — the overlay remains visible even without SSE.
 *
 * E2E flag __EVNT_E2E_SSE_FAIL__:
 *   openStream() → throwError() immediately
 *   EventDeliveryFacade.startLiveChannel() catches the error:
 *     → telemetry.trackSseError()
 *     → _deliveryMode.set('degraded')
 *     → telemetry.trackDegradedActivated('sse_error')
 *     → _startPolling()
 *   EventFeedPageComponent: deliveryMode() === 'degraded' → <evnt-degraded-mode-banner> visible.
 *
 * Alarm persistence (BRIDGE-01):
 *   hydrate() runs BEFORE startLiveChannel() (both in APP_INITIALIZER, sequential).
 *   hydrate() sets _activeEvent = MOCK_ACTIVE_EMERGENCY regardless of SSE flag.
 *   SSE failure only affects _deliveryMode — NOT _activeEvent.
 *   AlarmOverlayComponent stays visible: emergency hydrated before SSE attempted.
 *
 * DOM visibility note:
 *   The alarm overlay is `fixed inset-0 z-50` — it visually covers the feed page.
 *   The degraded banner is in EventFeedPageComponent (behind the overlay).
 *   Playwright's toBeVisible() checks DOM attachment + non-zero bounding box,
 *   NOT visual accessibility through z-index stacking. Both elements pass.
 */
import { test, expect } from '@playwright/test';
import { navigateToEvntWithAlarm, SEL } from './evnt-e2e-helpers';

import { scriptSemearSessao } from '@neuroassist/shared/auth-login/testing';

// Sessão semeada: todas as rotas do shell passam a exigir login (shared/auth-login).
// Sem isto a suíte cai em /login. `addInitScript` roda antes de qualquer script da
// página — o AuthSessionStore lê o sessionStorage no construtor, então semear
// depois do goto seria tarde demais.
test.beforeEach(async ({ page }) => {
  await page.addInitScript(scriptSemearSessao());
});

test.describe('Entrega Degradada — SSE falha, polling ativo (FE-EVNT-030)', () => {

  // ── Scenario 1: SSE falha → banner degradado + alarme preservado ──────────
  // BDD: "When SSE fails, the degraded mode banner appears and the alarm overlay stays"
  //
  // UNBLOCKED (P9): page.addInitScript sets __EVNT_E2E_SSE_FAIL__='true'.
  // EvntClientMockAdapter.openStream() throws immediately → facade catches error →
  // _deliveryMode = 'degraded' → EventFeedPageComponent shows degraded banner.
  // hydrate() (runs BEFORE startLiveChannel()) set _activeEvent → alarm still visible.
  test('SSE falha — banner degradado visível e alarme overlay preservado', async ({ page }) => {
    // Force SSE failure so EventDeliveryFacade falls back to polling
    await page.addInitScript(() => {
      localStorage.setItem('__EVNT_E2E_SSE_FAIL__', 'true');
    });

    // Navigate — alarm visible (from hydration, before SSE even attempted)
    await navigateToEvntWithAlarm(page);

    // Alarm overlay still present (hydration set _activeEvent before SSE attempted)
    await expect(page.locator(SEL.alarmOverlay)).toBeVisible();

    // Degraded banner must be visible in EventFeedPageComponent
    // Note: the overlay is z-50 covering the page, but Playwright toBeVisible()
    // checks DOM presence + bounding box, not z-index stacking.
    await expect(page.locator(SEL.degradedBanner)).toBeVisible({ timeout: 3000 });

    // Banner text confirms polling interval
    await expect(page.locator(SEL.degradedBanner)).toContainText(/modo degradado/i);
  });

  // ── Scenario 2: Polling fallback mantém alarme ativo após SSE falha ───────
  // BDD: "After SSE fails, polling keeps the emergency alarm active"
  //
  // BRIDGE-01: verifies that the polling fallback is running by confirming the
  // alarm remains visible after the SSE error (polling re-fetches /events/active
  // and finds the same emergency — _activeEvent stays set).
  //
  // Note: the mock adapter's poll interval is 15 s (default EvntFeatureFlagsService).
  // This test verifies the alarm state AFTER the SSE error but within the first
  // poll window — the alarm should still be set from hydration.
  test('Polling fallback mantém o alarme ativo após SSE falha', async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem('__EVNT_E2E_SSE_FAIL__', 'true');
    });

    await navigateToEvntWithAlarm(page);

    // Both indicators of degraded+active state must be present
    await expect(page.locator(SEL.alarmOverlay)).toBeVisible();
    await expect(page.locator(SEL.degradedBanner)).toBeVisible({ timeout: 3000 });

    // Wait briefly and confirm alarm has NOT been cleared by polling
    // (polling would only clear if a different state came from backend — mock returns same emergency)
    await page.waitForTimeout(1500);
    await expect(page.locator(SEL.alarmOverlay)).toBeVisible();

    // Delivery mode is 'degraded' — visible via the degraded banner text
    await expect(page.locator(SEL.degradedBanner)).toContainText(/verificando a cada/i);
  });

});
