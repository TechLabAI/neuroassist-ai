/**
 * E2E spec: DASH — Bootstrap register nav+outlet lifecycle (G6)
 * Issue: FE-DASH-028
 * 3 scenarios — P6 implementation
 *
 * BOOTSTRAP CONTEXT (SPEC-06 §1/§3):
 *   When Angular bootstraps and DASH routes load, DashShellIntegrationService is
 *   instantiated (providedIn: 'root'). The service registers with the ShellOutletRegistry.
 *
 *   Wave-0 — static sidebar (SidebarComponent):
 *     The sidebar in Wave-0 is HARDCODED with PAC, EVNT, ACS nav items.
 *     DashShellIntegrationService.registerWithShell() takes a DashShellRegistry interface
 *     but is NOT wired to the static SidebarComponent (INT-FE-DASH-003 pending).
 *     DASH is the default landing (/) — it has no dedicated sidebar link.
 *
 *   ShellOutletRegistry (DASH_GLOBAL_INTERRUPT_REGION):
 *     Registration happens in DashShellIntegrationService via outletRegistry.registerGlobalOutlet().
 *     ShellOutletRegistry is a Map-based service — it does NOT render DOM elements directly.
 *     The `data-outlet-region="dash.global-interrupt"` would require a dedicated outlet
 *     component to be rendered. In Wave-0: registry is populated but no DOM attribute visible.
 *     AlarmOverlayComponent (evnt-alarm-overlay) is rendered directly by AppShellComponent.
 *
 *   TODO[INT-FE-DASH-003]: when ShellRegistry + SidebarComponent are dynamically wired,
 *   assert "Status" as first sidebar item and data-outlet-region in DOM.
 *
 * WAVE-0 guards: authGuard + dashGuard bypass (DASH_AUTH_SESSION null factory).
 */
import { test, expect, Page } from '@playwright/test';

import { scriptSemearSessao } from '@neuroassist/shared/auth-login/testing';

// Sessão semeada: todas as rotas do shell passam a exigir login (shared/auth-login).
// Sem isto a suíte cai em /login. `addInitScript` roda antes de qualquer script da
// página — o AuthSessionStore lê o sessionStorage no construtor, então semear
// depois do goto seria tarde demais.
test.beforeEach(async ({ page }) => {
  await page.addInitScript(scriptSemearSessao());
});

// ── Navigation helper ─────────────────────────────────────────────────────────

/** Navigate to / and confirm DASH home has mounted. */
async function openDashHome(page: Page): Promise<void> {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: /Olá/ })).toBeVisible({ timeout: 6000 });
}

// ── Tests ─────────────────────────────────────────────────────────────────────

test.describe('DASH — Bootstrap lifecycle: nav + outlet (FE-DASH-028, G6)', () => {

  // ── Scenario 1: Bootstrap monta DASH como landing padrão ──────────────────
  // BDD: "bootstrap: navegar para / → verificar que DASH é o landing padrão"
  //
  // app.routes.ts wires DASH_ROUTES as the default shell children (path:'').
  // When Angular bootstraps and navigates to /, AppShellComponent + HomeStatusComponent mount.
  // DashHomeFacade.load() is called → after 400ms mock delay → "Olá, Jamilly" visible.
  // DashShellIntegrationService is instantiated (providedIn: 'root') as a side-effect.
  test('Bootstrap monta DASH como landing padrão em / com shell intacto', async ({ page }) => {
    await openDashHome(page);

    // HomeStatusComponent rendered: greeting h1
    await expect(page.getByRole('heading', { name: /Olá, Jamilly/i })).toBeVisible();

    // Patient data loads after mock delay
    await expect(page.getByRole('heading', { name: /Márcio está bem/i })).toBeVisible({ timeout: 5000 });

    // Shell chrome: sidebar and topbar present (AppShellComponent mounted)
    await expect(page.locator('nav.sidebar')).toBeVisible();

    // Brand in sidebar visible (NeuroAssist AI)
    await expect(page.locator('.sidebar-brand')).toBeVisible();
  });

  // ── Scenario 2: Navegação entre rotas sem crash ────────────────────────────
  // BDD: "Destroy fecha SSE sem crash" + "idempotência — nav múltipla não duplica"
  //
  // Navigating / → /notificacoes → / exercises DASH route lifecycle:
  //   mount HomeStatus (load()) → mount NotificationCenter (load() + open SSE) →
  //   back to HomeStatus (re-mount, load() again).
  // DashStreamClient.open('/panel/stream') is called on /notificacoes load;
  // navigating away doesn't crash (destroyRef handles takeUntilDestroyed).
  // Wave-0: SSE mock fails gracefully (EventSource gets onerror in JSDOM-like env,
  // DashStreamClient switches to fallback after 3 retries — non-fatal).
  test('Navegação / → /notificacoes → / não crasha e re-monta corretamente', async ({ page }) => {
    // Step 1: Land on /
    await openDashHome(page);
    await expect(page.getByRole('heading', { name: /Olá, Jamilly/i })).toBeVisible();

    // Step 2: Navigate to /notificacoes
    await page.goto('/notificacoes');
    await expect(page.getByRole('heading', { name: 'Notificações' })).toBeVisible({ timeout: 6000 });

    // Step 3: Navigate back to /
    await page.goto('/');
    await expect(page.getByRole('heading', { name: /Olá/ })).toBeVisible({ timeout: 6000 });

    // Re-mount: HomeStatusComponent re-calls load() → data re-rendered
    await expect(page.getByRole('heading', { name: /Márcio está bem/i })).toBeVisible({ timeout: 5000 });

    // No duplicate content (re-mount renders single greeting)
    await expect(page.getByRole('heading', { name: /Olá, Jamilly/i })).toHaveCount(1);
  });

  // ── Scenario 3 (G6): Sidebar Wave-0 — DASH nav item ausente (static sidebar) ─
  // BDD: "Nav registrado no shell após bootstrap"
  //
  // Wave-0 observation: SidebarComponent is STATIC — only PAC, EVNT, ACS entries.
  // DASH is the default landing (/) so it naturally has no sidebar link.
  // DashShellIntegrationService.registerWithShell() is pending DashShellRegistry wiring
  // (INT-FE-DASH-003). "Status" nav item will appear in sidebar when wired.
  //
  // Current assertion: sidebar is present, has static nav items, no duplicate DASH link.
  // TODO[INT-FE-DASH-003]: update to assert 'Status' link at order:0 when ShellRegistry wired.
  test('G6 — Wave-0: sidebar presente com nav estático; DASH nav pending INT-FE-DASH-003', async ({ page }) => {
    await openDashHome(page);

    // Sidebar chrome is present
    await expect(page.locator('nav.sidebar')).toBeVisible();

    // Sidebar brand: "NeuroAssist AI"
    await expect(page.locator('.sidebar-brand')).toContainText('NeuroAssist AI');

    // Static nav items exist (PAC, EVNT, ACS hardcoded in SidebarComponent)
    const sidebarLinks = page.locator('nav.sidebar ul.sidebar-nav a');
    await expect(sidebarLinks).not.toHaveCount(0);

    // Wave-0: no duplicate Status link (DASH is not in static sidebar)
    // When INT-FE-DASH-003 is done: assert sidebarLinks.first() has text "Status"
    const statusLinks = page.locator('nav.sidebar a[href="/"]');
    // Status link count should be 0 or 1 (no duplication)
    const count = await statusLinks.count();
    expect(count).toBeLessThanOrEqual(1);
  });

});
