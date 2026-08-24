/**
 * E2E spec: DASH — Error states: 403 forbidden-scope banner + 503 retry + SSE fallback (G8)
 * Issue: FE-DASH-030
 * 4 scenarios — P6 implementation
 *
 * ERROR HANDLING CONTEXT (SPEC-07 §2/§4):
 *
 *   403 forbidden-scope (G8):
 *     DashSummaryMockAdapter throws { status:403, message:'Você não tem acesso a este paciente.' }
 *     → DashHomeFacade.mapError() produces AppError { code:'HTTP_403', message:..., status:403 }
 *     → HomeStatusComponent template renders error div (inline, NOT a modal):
 *         <p class="text-danger">{{ facade.error()?.message }}</p>
 *         <button>Tentar novamente</button>
 *     Shell chrome (sidebar, topbar) remains intact during error (error is local to content area).
 *     Message must not reveal patient existence (text is generic: "Você não tem acesso a este paciente.").
 *     Note: DASH does not use modals for errors (SPEC-07) — error is always inline.
 *
 *   503 upstream unavailable:
 *     Same inline pattern. Message: 'Serviço temporariamente indisponível.'
 *     "Tentar novamente" calls facade.retry() → re-runs load().
 *
 *   SSE fallback (SPEC-06 §4):
 *     DashStreamClient.open('/panel/stream') connects to EventSource.
 *     In test environment: EventSource fails (no real SSE server at /panel/stream).
 *     After 3 onerror events: DashStreamClient switches to long-poll fallback mode.
 *     Long-poll mock returns empty cursor → no new notifications appended.
 *     UI must remain stable (no crash from SSE failures). Notifications from catch-up HTTP
 *     are still shown. fallbackActive signal = true (internal only, not asserted in UI).
 *
 * E2E flags (page.addInitScript → localStorage before Angular bootstraps):
 *   __DASH_E2E_SUMMARY_FAIL__ : getSummary() throws 503
 *   __DASH_E2E_SUMMARY_403__  : getSummary() throws 403 forbidden-scope
 *
 * WAVE-0 guards bypass active; all error scenarios accessible without auth.
 */
import { test, expect, Page } from '@playwright/test';

import { scriptSemAlarmeDeEmergencia } from '@neuroassist/evnt/testing';
import { scriptSemearSessao } from '@neuroassist/shared/auth-login/testing';

// Sessão semeada: todas as rotas do shell passam a exigir login (shared/auth-login).
// Sem isto a suíte cai em /login. `addInitScript` roda antes de qualquer script da
// página — o AuthSessionStore lê o sessionStorage no construtor, então semear
// depois do goto seria tarde demais.
//
// Emergência apagada: esta suíte é sobre ESTADO DE ERRO inline (403/503/fallback do
// SSE). O mock do EVNT semeia uma emergência ativa e o alarme preempta a tela inteira
// em qualquer rota do shell — comportamento correto do produto, provado pela evnt-e2e.
// Aqui ele só sobrepõe o banner e intercepta o clique em "Tentar novamente".
test.beforeEach(async ({ page }) => {
  await page.addInitScript(scriptSemearSessao());
  await page.addInitScript(scriptSemAlarmeDeEmergencia());
});

// Nota de seletor: o shell renderiza <app-sidebar><aside>…</aside> — não existe
// `nav.sidebar`. As asserções de "shell intacto" apontavam para uma estrutura antiga
// e o erro que davam ("element(s) not found") escondia o que queriam medir.

// ── Tests ─────────────────────────────────────────────────────────────────────

test.describe('DASH — Error states: 403 + 503 + SSE fallback (FE-DASH-030, G8)', () => {

  // ── Scenario 1: 503 → banner inline com retry ──────────────────────────────
  // BDD: "503 → banner com retry button"
  //
  // UNBLOCKED via __DASH_E2E_SUMMARY_FAIL__: DashSummaryMockAdapter throws status=503.
  // DashHomeFacade catches → status='error' → error div rendered inline (no modal).
  // Sidebar and topbar remain present (shell chrome intact).
  test('503 upstream → banner inline com botão Tentar novamente, shell intacto', async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem('__DASH_E2E_SUMMARY_FAIL__', 'true');
    });
    await page.goto('/');

    // After ~400ms: error state active — error message visible
    await expect(page.getByText('Serviço temporariamente indisponível.')).toBeVisible({ timeout: 5000 });

    // Retry button present (facade.retry() → facade.load())
    await expect(page.getByRole('button', { name: 'Tentar novamente' })).toBeVisible();

    // Shell chrome INTACT: sidebar visible (error is local to content, not full-screen)
    await expect(page.locator('app-sidebar')).toBeVisible();

    // No modal overlay (SPEC-07: DASH never uses modal for errors)
    await expect(page.locator('[role="dialog"]')).not.toBeVisible();

    // No patient heading (nothing loaded — error state)
    await expect(page.getByRole('heading', { name: /Olá,/i })).not.toBeVisible();
  });

  // ── Scenario 2: 403 forbidden-scope → banner inline sem revelar existência ─
  // BDD: "403 → banner 'Você não tem acesso a este paciente.' inline"
  //
  // UNBLOCKED via __DASH_E2E_SUMMARY_403__: DashSummaryMockAdapter throws
  // { status:403, message:'Você não tem acesso a este paciente.' }.
  // DashHomeFacade.mapError() preserves the message → error div shows it inline.
  // Message is generic (does not reveal patient existence by name).
  // Shell chrome must stay intact (sidebar, topbar).
  test('G8 — 403 forbidden-scope → banner inline com mensagem genérica, shell intacto', async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem('__DASH_E2E_SUMMARY_403__', 'true');
    });
    await page.goto('/');

    // After ~400ms: 403 error state
    await expect(
      page.getByText('Você não tem acesso a este paciente.'),
    ).toBeVisible({ timeout: 5000 });

    // Retry button present (wave-0: no 403-specific CTA, generic retry)
    await expect(page.getByRole('button', { name: 'Tentar novamente' })).toBeVisible();

    // Shell chrome INTACT during 403
    await expect(page.locator('app-sidebar')).toBeVisible();

    // No modal
    await expect(page.locator('[role="dialog"]')).not.toBeVisible();

    // Message does not reveal patient's NAME (generic access denial message)
    await expect(page.getByText(/Márcio Amaral/i)).not.toBeVisible();
  });

  // ── Scenario 3: Shell chrome intacto durante 403 + retry navega para home ─
  // BDD: "Shell chrome intacto durante banner 403" + confirmação de retry
  //
  // After 403, clicking "Tentar novamente" re-calls load().
  // With __DASH_E2E_SUMMARY_403__ still set → same error (confirms retry pathway works).
  // Ensures no full-page override (shell chrome survives error + retry cycle).
  test('Shell chrome sobrevive ciclo 403 + retry sem perder sidebar', async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem('__DASH_E2E_SUMMARY_403__', 'true');
    });
    await page.goto('/');

    // Initial 403 error state
    await expect(page.getByText('Você não tem acesso a este paciente.')).toBeVisible({ timeout: 5000 });

    // Sidebar visible before retry
    await expect(page.locator('app-sidebar')).toBeVisible();

    // Click retry
    await page.getByRole('button', { name: 'Tentar novamente' }).click();

    // After retry (400ms delay, same flag): 403 error reappears
    await expect(page.getByText('Você não tem acesso a este paciente.')).toBeVisible({ timeout: 5000 });

    // Sidebar still visible after retry cycle (shell chrome never hidden)
    await expect(page.locator('app-sidebar')).toBeVisible();
  });

  // ── Scenario 4: SSE fallback não quebra UI das notificações ───────────────
  // BDD: "Sem stream disponível a entrega usa o long-poll de fallback"
  //
  // In the e2e environment: DashStreamClient.open('/panel/stream') creates EventSource
  // pointing to localhost:4200/panel/stream — which returns 404 (no SSE server).
  // DashStreamClient.onerror fires → after 3 attempts → fallback mode active.
  // Long-poll: DashNotificationsMockAdapter.poll() → returns [] after 400ms.
  // Notification list still shows catch-up HTTP result ("Ajuda" from listUnacknowledged).
  // UI must not crash during SSE retry/fallback cycle.
  //
  // Wave-0 note: SSE failure in test env is expected — EventSource 404 triggers fallback
  // before any notification events could arrive. The UI stays functional via catch-up HTTP.
  test('SSE fallback — UI de notificações permanece estável sem stream ativo', async ({ page }) => {
    await page.goto('/notificacoes');

    // NotificationCenterComponent.ngOnInit() calls facade.load():
    //   1. listUnacknowledged() → 400ms → 1 notification (catch-up HTTP)
    //   2. streamClient.open('/panel/stream') → EventSource → likely 404/error in test env
    //   → After 3 retries (with exponential backoff): fallback mode active
    //   → poll() called every 30s — no impact in < 5s test window

    // Notification list from catch-up HTTP is visible (not dependent on SSE)
    await expect(page.getByRole('heading', { name: 'Notificações' })).toBeVisible({ timeout: 6000 });
    await expect(page.getByText('"Ajuda"')).toBeVisible({ timeout: 5000 });

    // No crash: page is stable
    await expect(page.locator('app-sidebar')).toBeVisible();
    await expect(page.locator('[role="alert"]')).not.toBeVisible();

    // Acknowledge still works (catch-up HTTP path, not SSE)
    await page.getByRole('button', { name: 'Revisei' }).click();
    await expect(page.getByText('Tudo revisado')).toBeVisible({ timeout: 3000 });
  });

});
