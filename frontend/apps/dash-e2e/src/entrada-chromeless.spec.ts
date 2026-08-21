/**
 * E2E spec: DASH — /entrada chromeless brand entry (G3)
 * Issue: FE-DASH-025
 * 3 scenarios — P6 implementation
 *
 * CHROMELESS DESIGN:
 *   /entrada is registered at TOP-LEVEL in app.routes.ts (outside AppShellComponent).
 *   Rendered directly by EntradaComponent with no sidebar, no topbar, no router-outlet chrome.
 *   AppShellComponent (which contains .shell-layout, nav.sidebar, app-topbar) is NOT mounted.
 *
 * EntradaComponent template:
 *   - Full-screen div with bg-sidebar color scheme
 *   - img[alt="NeuroAssist AI"] logo
 *   - h1: "Um gesto que você não vai perder."
 *   - a[routerLink="/"] with text "Entrar no painel" → navigates to / (home)
 *   - Footer: "Calma por padrão · alarme reservado só para o que importa"
 *
 * WAVE-0: authGuard bypass → authenticated sessions do NOT redirect from /entrada.
 *   TODO[RBAC]: when authGuard is wired with real tokens, an authenticated user
 *   navigating to /entrada should be redirected to / (SPEC-01 §1).
 */
import { test, expect, Page } from '@playwright/test';

// ── Navigation helper ─────────────────────────────────────────────────────────

/** Navigate to /entrada and wait for the chromeless page to load. */
async function openEntrada(page: Page): Promise<void> {
  await page.goto('/entrada');
  // Wait for EntradaComponent CTA — the most distinctive element
  await expect(page.getByRole('link', { name: /Entrar no painel/i })).toBeVisible({ timeout: 6000 });
}

// ── Tests ─────────────────────────────────────────────────────────────────────

test.describe('DASH — /entrada chromeless brand entry (FE-DASH-025, G3)', () => {

  // ── Scenario 1: /entrada renderiza sem shell chrome ────────────────────────
  // BDD: "Usuário não autenticado navega para /entrada → sidebar e topbar AUSENTES"
  //
  // /entrada is a top-level route in app.routes.ts (outside AppShellComponent).
  // AppShellComponent is NOT instantiated → .shell-layout, nav.sidebar, app-topbar absent.
  // EntradaComponent renders its own full-screen layout (bg-sidebar class on root div).
  test('G3 — /entrada renderiza sem shell chrome (sem sidebar, sem topbar)', async ({ page }) => {
    await openEntrada(page);

    // Shell layout is ABSENT (AppShellComponent not mounted for this route)
    await expect(page.locator('.shell-layout')).not.toBeVisible();

    // Sidebar is ABSENT
    await expect(page.locator('nav.sidebar')).not.toBeVisible();

    // app-topbar is ABSENT (no shell topbar element)
    await expect(page.locator('app-topbar')).not.toBeVisible();

    // URL stays at /entrada (no redirect in Wave-0)
    await expect(page).toHaveURL(/entrada/);
  });

  // ── Scenario 2: Heading e identidade visual visíveis ─────────────────────
  // BDD: "Elementos de identidade visual do EntradaComponent estão presentes"
  //
  // EntradaComponent template renders:
  //   - img[alt="NeuroAssist AI"] (logo)
  //   - h1: "Um gesto que você não vai perder."
  //   - Footer text about calma por padrão
  test('Heading e identidade visual visíveis em /entrada', async ({ page }) => {
    await openEntrada(page);

    // Main value-proposition heading
    await expect(
      page.getByRole('heading', { name: 'Um gesto que você não vai perder.' }),
    ).toBeVisible();

    // Logo image
    await expect(page.getByAltText('NeuroAssist AI')).toBeVisible();

    // Sub-caption about the product
    await expect(page.getByText(/Webcam comum, alarme confiável/i)).toBeVisible();

    // Trust footer line
    await expect(page.getByText(/Calma por padrão/i)).toBeVisible();
  });

  // ── Scenario 3: CTA "Entrar no painel" navega para / ────────────────────
  // BDD: "CTA da entrada navega para /"
  //
  // EntradaComponent renders <a routerLink="/" ...>Entrar no painel</a>.
  // Clicking it triggers Angular router navigation → AppShellComponent mounts → / renders.
  // DashHomeFacade.load() is called → HomeStatusComponent renders with greeting.
  test('CTA Entrar no painel navega para a home (/)', async ({ page }) => {
    await openEntrada(page);

    // CTA link is visible
    const ctaLink = page.getByRole('link', { name: /Entrar no painel/i });
    await expect(ctaLink).toBeVisible();

    // Click CTA → Angular router navigates to /
    await ctaLink.click();

    // After navigation: URL is / (or root)
    await expect(page).toHaveURL(/localhost:4200\/?$/, { timeout: 5000 });

    // HomeStatusComponent renders (DashHomeFacade.load() → 400ms → "Olá, Jamilly")
    await expect(page.getByRole('heading', { name: /Olá/ })).toBeVisible({ timeout: 6000 });

    // Shell chrome now visible (inside AppShellComponent)
    await expect(page.locator('nav.sidebar')).toBeVisible();
  });

});
