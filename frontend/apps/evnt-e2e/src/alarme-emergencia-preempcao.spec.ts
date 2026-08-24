/**
 * E2E spec: Alarme de Emergência — Preempção (FE-EVNT-026)
 *
 * Verifies that an active emergency event causes the AlarmOverlayComponent to
 * preempt the current screen (rendered above the router-outlet via AppShellComponent).
 *
 * Mock seed state (default, no flags):
 *   listActive() → { activeEvent: MOCK_ACTIVE_EMERGENCY (urgencia:'emergencia'), events: [...] }
 *   The evntBootstrap APP_INITIALIZER calls hydrate() which sets _activeEvent.
 *   AlarmOverlayComponent in AppShellComponent renders as soon as _activeEvent != null.
 *
 * INFRASTRUCTURE NOTE:
 *   The alarm overlay is registered with ShellOutletRegistry (ADR-005) AND directly
 *   included in AppShellComponent (P9 interim — INT-FE-EVNT-001 stub). The direct
 *   inclusion ensures it is visible in e2e without the full dynamic registry rendering.
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

test.describe('Alarme de Emergência — Preempção (FE-EVNT-026)', () => {

  // ── Scenario 1: Alarme preempta a tela ativa ──────────────────────────────
  // BDD: "An active emergency alarm preempts the active screen (ADR-005 global outlet)"
  test('Alarme de emergência preempta a tela ativa', async ({ page }) => {
    // Default mock seed: listActive() returns MOCK_ACTIVE_EMERGENCY as activeEvent.
    // evntBootstrap.init() (APP_INITIALIZER) calls hydrate() → _activeEvent set → overlay visible.
    await navigateToEvntWithAlarm(page);

    // Overlay must be visible: data-testid and role="alertdialog"
    await expect(page.locator(SEL.alarmOverlay)).toBeVisible();
    await expect(page.locator('[role="alertdialog"]')).toBeVisible();

    // Overlay must contain the confirm button
    await expect(
      page.locator('[role="alertdialog"]').getByRole('button', { name: /confirmar que atendi/i }),
    ).toBeVisible();
  });

  // ── Scenario 2: Recarregar durante alarme ativo re-exibe o alarme ─────────
  // BDD: "Reloading the page during an active alarm re-shows the alarm overlay (hydration)"
  test('Recarregar durante alarme ativo re-exibe o alarme (hidratação)', async ({ page }) => {
    // First navigation — alarm visible
    await navigateToEvntWithAlarm(page);
    await expect(page.locator(SEL.alarmOverlay)).toBeVisible();

    // Page reload — Angular re-bootstraps; APP_INITIALIZER re-runs hydrate()
    await page.reload();

    // After reload: alarm must reappear (hydration replays the emergency)
    await page.waitForSelector(SEL.alarmOverlay, { timeout: 8000 });
    await expect(page.locator(SEL.alarmOverlay)).toBeVisible();
    await expect(page.locator('[role="alertdialog"]')).toBeVisible();
  });

});
