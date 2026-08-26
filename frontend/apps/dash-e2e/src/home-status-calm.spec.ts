/**
 * E2E spec: DASH — Home Status calm + role-hide nav + rota /notificacoes
 * Issues: FE-DASH-024 (G1 + G2)
 * 6 scenarios — P6 implementation
 *
 * MOCK SEED STATE (runtime — DashSummaryMockAdapter):
 *   caregiver: Jamilly Amaral (role: 'cuidadora')
 *   patient:   Márcio Amaral (device: online, latestEvent: 'Ajuda'/emergencia/nao_avaliado)
 *   unacknowledgedCount: 1
 *   degradedSections: []
 *
 * CALM INVARIANT (SPEC-01 §3):
 *   DashHomeFacade.calm = activeEvent === null (DASH_ACTIVE_EVENT_TOKEN).
 *   O token JÁ está ligado ao EventDeliveryFacade do EVNT (app.config.ts) — a nota
 *   "Wave-0: EVNT not yet wired → calm=true always" caducou. Hoje o estado calmo é
 *   uma PRÉ-CONDIÇÃO que a suíte precisa montar (scriptSemAlarmeDeEmergencia), não
 *   um efeito colateral de integração faltando.
 *
 * E2E flags (page.addInitScript → localStorage before Angular bootstraps):
 *   __DASH_E2E_SUMMARY_FAIL__ : getSummary() throws 503 → facade sets status='error'
 *
 * WAVE-0 guards: authGuard + dashGuard inject DASH_AUTH_SESSION / DASH_ACCESS_DECISION
 * with optional:true; factory returns null → both guards return true (bypass active).
 *
 * G1 (role-hide nav): Sidebar is STATIC in Wave-0 (hardcoded PAC/EVNT/ACS entries).
 *   No "Onboarding" link exists in sidebar. DashShellIntegrationService.registerWithShell()
 *   does not yet connect to the static SidebarComponent.
 *   TODO[INT-FE-DASH-003]: when ShellRegistry is wired, "Status" will appear as sidebar item.
 *
 * G2: /notificacoes is accessible via authGuard+dashGuard Wave-0 bypass.
 */
import { test, expect, Page } from '@playwright/test';

import { scriptSemAlarmeDeEmergencia } from '@neuroassist/evnt/testing';
import { scriptSemearSessao } from '@neuroassist/shared/auth-login/testing';

// Sessão semeada: todas as rotas do shell passam a exigir login (shared/auth-login).
// Sem isto a suíte cai em /login. `addInitScript` roda antes de qualquer script da
// página — o AuthSessionStore lê o sessionStorage no construtor, então semear
// depois do goto seria tarde demais.
//
// Emergência apagada: esta suíte é sobre o estado CALMO. `calm` deixou de ser
// "true por construção" — o DASH_ACTIVE_EVENT_TOKEN hoje está ligado de verdade ao
// EventDeliveryFacade do EVNT (app.config.ts), e o mock do EVNT semeia uma
// emergência ativa. Com ela no ar `calm` é false, o StatusCard entra no ramo
// "Evento pendente de confirmação" e o alarme ainda preempta a tela inteira. Nada
// disso é defeito: é o produto certo medido a partir de um estado que não é desta
// suíte. Quem prova a preempção é a evnt-e2e.
test.beforeEach(async ({ page }) => {
  await page.addInitScript(scriptSemearSessao());
  await page.addInitScript(scriptSemAlarmeDeEmergencia());
});

// ── Navigation helpers ─────────────────────────────────────────────────────────

/** Navigate to / (home status) and wait for the greeting heading. */
async function openHome(page: Page): Promise<void> {
  await page.goto('/');
  // h1: "Olá, Jamilly" (firstCaregiverName = first word of 'Jamilly Amaral')
  await expect(page.getByRole('heading', { name: /Olá/ })).toBeVisible({ timeout: 6000 });
}

/** Navigate to / and wait for patient data to load (not just the greeting). */
async function openHomeAndWaitForPatient(page: Page): Promise<void> {
  await openHome(page);
  // h2 inside StatusCard — "Márcio está bem" (calm=true, Wave-0)
  await expect(page.getByRole('heading', { name: /Márcio está bem/i })).toBeVisible({ timeout: 5000 });
}

// ── Tests ─────────────────────────────────────────────────────────────────────

test.describe('DASH — Home Status calm + role-hide nav + /notificacoes (FE-DASH-024)', () => {

  // ── Scenario 1: Home carrega dados do paciente ─────────────────────────────
  // BDD: "Home carrega o resumo e mostra o estado calmo"
  //
  // DashSummaryMockAdapter returns Jamilly + Márcio after 400ms.
  // DashHomeFacade.load() sets status='loaded'; patient, caregiverName, device computed.
  test('Home page carrega com dados do paciente e cumprimento da cuidadora', async ({ page }) => {
    await openHome(page);

    // h1: "Olá, Jamilly" (firstCaregiverName = split(' ')[0] of 'Jamilly Amaral')
    await expect(page.getByRole('heading', { name: 'Olá, Jamilly' })).toBeVisible();

    // Sub-caption: patient name
    await expect(page.getByText(/Este é o status de Márcio Amaral agora\./)).toBeVisible();

    // Continua em / (sem redirecionamento). A porta não entra na asserção: a suíte
    // roda contra APP_URL (4201 aqui, 14200 no container) e prender o número fazia
    // o cenário reprovar por causa do endereço, não do comportamento.
    await expect(page).toHaveURL(/^https?:\/\/[^/]+\/$/);
  });

  // ── Scenario 2: StatusCard mostra estado calmo (Wave-0) ────────────────────
  // BDD: "Home carrega o resumo e mostra o estado calmo" + "calm honesty invariant"
  //
  // calm = activeEvent === null. Com a emergência do EVNT apagada no beforeEach,
  // activeEvent=null → calm=true → estaBem=true → h2 "Márcio está bem".
  // Note: mock latestEvent.urgencia='emergencia' (isEmergencyPending=true) but calm=true
  // takes precedence → "está bem" branch wins. Correct behavior per SPEC-01 §3:
  // o StatusCard nunca lê urgência do resumo agregado, só o sinal do EVNT.
  test('StatusCard mostra estado calmo — sem alarme ativo no EVNT (calm=true)', async ({ page }) => {
    await openHomeAndWaitForPatient(page);

    // StatusCard calm branch: "Márcio está bem" (patient first name + " está bem")
    await expect(page.getByRole('heading', { name: /Márcio está bem/i })).toBeVisible();

    // KPI row: "Notificações pendentes" = 1 (mock unacknowledgedCount)
    await expect(page.getByText('Notificações pendentes')).toBeVisible();
    await expect(page.getByText('1').first()).toBeVisible();

    // "Ver notificações" button is visible (unacknowledgedCount > 0 → link rendered)
    await expect(page.getByRole('link', { name: /Ver notificações/i })).toBeVisible();

    // "Ver histórico" link is always shown
    await expect(page.getByRole('button', { name: /Ver histórico de hoje/i })).toBeVisible();
  });

  // ── Scenario 3: Falha 503 mostra erro com tentar novamente ────────────────
  // BDD: "Falha ao carregar o resumo mostra erro com tentar novamente"
  //
  // UNBLOCKED via __DASH_E2E_SUMMARY_FAIL__: getSummary() throws status=503
  // after 400ms → DashHomeFacade catches → sets status='error' → template renders
  // error div with message and "Tentar novamente" button.
  test('Falha 503 ao carregar mostra erro com botão de tentar novamente', async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem('__DASH_E2E_SUMMARY_FAIL__', 'true');
    });
    await page.goto('/');

    // After ~400ms mock delay: status='error' → error div rendered
    await expect(page.getByText('Serviço temporariamente indisponível.')).toBeVisible({ timeout: 5000 });

    // Retry affordance
    await expect(page.getByRole('button', { name: 'Tentar novamente' })).toBeVisible();

    // No patient data (error state — nothing loaded)
    await expect(page.getByRole('heading', { name: /Olá,/ })).not.toBeVisible();
  });

  // ── Scenario 4: Retry após erro re-executa load ────────────────────────────
  // BDD: "Retry re-executa getSummary"
  //
  // After error, clicking "Tentar novamente" calls facade.retry() → facade.load()
  // → flag still set → second error (same flag). Confirms retry button is functional.
  test('Botão tentar novamente re-executa o carregamento após erro', async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem('__DASH_E2E_SUMMARY_FAIL__', 'true');
    });
    await page.goto('/');

    // Error state visible
    await expect(page.getByRole('button', { name: 'Tentar novamente' })).toBeVisible({ timeout: 5000 });

    // Click retry → facade.retry() → load() → 400ms delay → same error (flag still set)
    await page.getByRole('button', { name: 'Tentar novamente' }).click();

    // Retry triggered: button disappears briefly (loading state) then error reappears
    // After second 400ms mock delay: error message still present (flag not cleared)
    await expect(page.getByText('Serviço temporariamente indisponível.')).toBeVisible({ timeout: 5000 });
  });

  // ── Scenario 5 (G1): Sidebar não exibe link "Onboarding" ──────────────────
  // BDD: "WHILE role=cuidadora, item Onboarding oculto na sidebar"
  //
  // Wave-0: SidebarComponent is STATIC — only PAC, EVNT, ACS entries.
  // DashShellIntegrationService.registerWithShell() does not yet wire DASH nav
  // into the static sidebar (INT-FE-DASH-003 pending).
  // No "Onboarding" link exists in the sidebar in any role (current Wave-0).
  // TODO[INT-FE-DASH-003]: when ShellRegistry wires sidebar dynamically,
  // assert that 'cuidadora' does not see 'Onboarding' and 'coordenador_clinica' does.
  test('G1 — Wave-0: sidebar não tem link Onboarding (sidebar estática, RBAC pendente)', async ({ page }) => {
    await openHome(page);

    // Shell chrome is present (inside AppShellComponent).
    // O seletor mudou: a SidebarComponent renderiza <app-sidebar><aside>…</aside>
    // com <nav data-testid="sidebar-nav">. Não existe (nem existia mais) `nav.sidebar`
    // — a asserção apontava para uma estrutura antiga do shell.
    await expect(page.locator('app-sidebar')).toBeVisible();

    // Wave-0 observation: no "Onboarding" link in the static sidebar
    await expect(page.getByRole('link', { name: /Onboarding/i })).not.toBeVisible();

    // O menu tem pelo menos um item para o papel semeado (construirMenu filtra por
    // authority; sem sessão desenharia zero itens — ver menu-trabalho.ts).
    await expect(page.locator('[data-testid="sidebar-nav"] a')).not.toHaveCount(0);
  });

  // ── Scenario 6 (G2): /notificacoes acessível ──────────────────────────────
  // BDD: "rota /notificacoes acessível e com breadcrumb Status › Notificações"
  //
  // /notificacoes is a child of DASH_ROUTES (inside AppShellComponent shell).
  // Wave-0 guard bypass: authGuard + dashGuard return true (DASH_AUTH_SESSION null).
  // NotificationCenterComponent renders "Notificações" h1.
  // Note: breadcrumb is defined in DASH_BREADCRUMBS but not yet wired to a
  // BreadcrumbService in Wave-0 (INT-FE-DASH-003 pending). Only heading is asserted.
  test('G2 — /notificacoes acessível e renderiza NotificationCenter', async ({ page }) => {
    await page.goto('/notificacoes');

    // NotificationCenterComponent h1
    await expect(page.getByRole('heading', { name: 'Notificações' })).toBeVisible({ timeout: 6000 });

    // Sub-caption: "Notificações pendentes de revisão."
    await expect(page.getByText('Notificações pendentes de revisão.')).toBeVisible();

    // URL at /notificacoes (no redirect — Wave-0 bypass)
    await expect(page).toHaveURL(/notificacoes/);

    // Shell chrome intact (inside shell — has sidebar nav)
    await expect(page.locator('app-sidebar')).toBeVisible();

    // "← Voltar ao status" back link visible
    await expect(page.getByRole('link', { name: /Voltar ao status/i })).toBeVisible();
  });

});
