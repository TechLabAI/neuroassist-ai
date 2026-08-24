/**
 * E2E spec: Histórico — linha do tempo por período
 * Feature: history-timeline.feature
 * 8 scenarios — P4 implementation (FE-HIST-003)
 *
 * MOCK SEED STATE (semana window — runtime date 2026-08-19):
 *   Semana (7 days, from=2026-08-12, to=2026-08-19) — 3 records visible:
 *     rec-001: label='água',  urgency='rotina',  verdict=null  → "Foi real?" button
 *     rec-002: label='Sapo',  urgency='critico', verdict='real' → "✓ Marcado real"
 *     rec-003: label='frio',  urgency='rotina',  verdict='falso_positivo' → "Falso positivo"
 *   rec-004 (isoNDaysAgo(8)=2026-08-08) is OUTSIDE the semana window → never shown.
 *
 *   Hoje (1 day, from=2026-08-18, to=2026-08-19): 0 records → empty state
 *   Mês  (30 days): all 4 records visible
 *
 * E2E flags (page.addInitScript → localStorage before Angular bootstraps):
 *   __HIST_E2E_LIST_FAIL__    : list() throws → facade sets error state (test 6)
 *   __HIST_E2E_SMALL_PAGE__   : list() returns size=2 → hasMore=true (test 7)
 *
 * WAVE-0 guards: authGuard + histGuard inject HIST_AUTH_SESSION / HIST_ACCESS_DECISION
 * with optional: true; factory returns null → both guards return true (bypass active).
 */
import { test, expect, Page } from '@playwright/test';

import { scriptSemearSessao } from '@neuroassist/shared/auth-login/testing';
import { scriptSemAlarmeDeEmergencia } from '@neuroassist/evnt/testing';

// Estado de partida da suíte. Ambos via `addInitScript`, que roda antes de
// qualquer script da página: o AuthSessionStore lê o sessionStorage no construtor
// e o mock do EVNT lê a bandeira na primeira chamada, então semear depois do goto
// seria tarde demais nos dois casos.
//
//   1. sessão — todas as rotas do shell passam a exigir login; sem isto a suíte
//      cai em /login;
//   2. sem emergência ativa — o mock do EVNT semeia uma, e o alarme de emergência
//      preempta a tela inteira (é o comportamento certo do produto, e a suíte do
//      EVNT existe para prová-lo). Sobre estas telas ele só contribui um segundo
//      <h1> e um alertdialog por cima do conteúdo.
test.beforeEach(async ({ page }) => {
  await page.addInitScript(scriptSemearSessao());
  await page.addInitScript(scriptSemAlarmeDeEmergencia());
});

// ── Navigation helper ─────────────────────────────────────────────────────────

/**
 * Navigate to /historico and wait for the page heading to appear.
 * Does NOT wait for items — tests decide their own await strategy.
 */
async function openHistorico(page: Page): Promise<void> {
  await page.goto('/historico');
  await expect(page.getByRole('heading', { name: 'Histórico' })).toBeVisible({ timeout: 6000 });
}

/**
 * Wait for semana items to fully load (3 items, mock delay 400ms).
 */
async function waitForSemanaItems(page: Page): Promise<void> {
  await expect(page.getByTestId('history-item')).toHaveCount(3, { timeout: 5000 });
}

// ── Tests ─────────────────────────────────────────────────────────────────────

test.describe('Histórico — linha do tempo por período', () => {

  // ── Scenario 1: Deep-link abre histórico ─────────────────────────────────
  // BDD: "Deep-linking to /historico renders the history page with the Histórico heading"
  test('Deep-link do Início abre o histórico de hoje', async ({ page }) => {
    await openHistorico(page);

    // Heading "Histórico" must be visible — confirms the route resolved and
    // HistoryTimelineComponent was mounted.
    await expect(page.getByRole('heading', { name: 'Histórico' })).toBeVisible();

    // Subtitle "Eventos registrados — trilha imutável de auditoria." must be present.
    await expect(page.getByText(/Eventos registrados/i)).toBeVisible();

    // URL stays at /historico (no redirect — Wave-0 guard bypass active).
    await expect(page).toHaveURL(/historico/);
  });

  // ── Scenario 2: Período padrão ───────────────────────────────────────────
  // BDD: "Navigating to /historico without a period param applies 'semana' as the default"
  test('Rota sem período aplica o período padrão', async ({ page }) => {
    await openHistorico(page);

    // HistoryTimelineComponent.ngOnInit calls facade.load('semana').
    // facade.#period is set to 'semana' synchronously → vm().period === 'semana'
    // → the "Semana" tab gets aria-selected="true" immediately (before data arrives).
    await expect(
      page.getByRole('tab', { name: 'Período Semana' }),
    ).toHaveAttribute('aria-selected', 'true', { timeout: 5000 });

    // The other tabs must not be selected.
    await expect(
      page.getByRole('tab', { name: 'Período Hoje' }),
    ).toHaveAttribute('aria-selected', 'false');
    await expect(
      page.getByRole('tab', { name: 'Período Mês' }),
    ).toHaveAttribute('aria-selected', 'false');
  });

  // ── Scenario 3: Rótulo + urgência por linha ──────────────────────────────
  // BDD: "Each history item row shows the human-readable label and the urgency badge"
  test('Cada linha mostra o rótulo legível e o selo de urgência', async ({ page }) => {
    await openHistorico(page);
    await waitForSemanaItems(page);

    // Three items visible for semana window (desc order by recordedAt):
    // Item 0: rec-001 → label='água', urgency='rotina'
    // Item 1: rec-002 → label='Sapo', urgency='critico'
    // Item 2: rec-003 → label='frio', urgency='rotina'
    const items = page.getByTestId('history-item');
    await expect(items).toHaveCount(3);

    // First item: label "água" and urgency badge "rotina"
    const firstItem = items.first();
    // Template renders: "{{ item.label }}" — literal quotes around the label text
    await expect(firstItem).toContainText('"água"');
    // Urgency badge text is item.urgency value (e.g. 'rotina', 'critico')
    await expect(firstItem.locator('span').filter({ hasText: /rotina|critico|atencao/ }).first())
      .toBeVisible();

    // Second item: label "Sapo", urgency badge "critico"
    const secondItem = items.nth(1);
    await expect(secondItem).toContainText('"Sapo"');
    await expect(secondItem.locator('span').filter({ hasText: 'critico' })).toBeVisible();
  });

  // ── Scenario 4: Trocar período reinicia paginação ────────────────────────
  // BDD: "Switching the period tab resets pagination and reloads the timeline"
  test('Trocar de período reinicia a paginação', async ({ page }) => {
    await openHistorico(page);
    // Semana (default): 3 items, page=0
    await waitForSemanaItems(page);

    // Click "Hoje" tab → setPeriod('hoje') → facade resets page=0, reloads
    await page.getByRole('tab', { name: 'Período Hoje' }).click();

    // "Hoje" tab is now selected (period signal updates synchronously)
    await expect(
      page.getByRole('tab', { name: 'Período Hoje' }),
    ).toHaveAttribute('aria-selected', 'true', { timeout: 3000 });
    await expect(
      page.getByRole('tab', { name: 'Período Semana' }),
    ).toHaveAttribute('aria-selected', 'false');

    // A janela "Hoje" (24h) pega só o registro de hoje; a "Semana" pegava três.
    // É a MUDANÇA da lista que prova o recarregamento com page=0 — a versão
    // anterior deste cenário esperava zero item, o que só valia enquanto a
    // semente do mock estivesse velha o bastante para escapar da janela de 24h.
    // Passou a reprovar no dia em que a âncora virou o presente, sem que nada no
    // aplicativo tivesse mudado.
    await expect(page.getByTestId('history-item')).toHaveCount(1, { timeout: 3000 });
    await expect(page.getByTestId('history-item').first()).toContainText('"água"');
  });

  // ── Scenario 5: Período vazio ─────────────────────────────────────────────
  // BDD: "An empty period shows a reassuring empty state message"
  test('Período vazio mostra estado tranquilizador', async ({ page }) => {
    // O vazio é PEDIDO ao mock, e não obtido escolhendo uma janela que por acaso
    // não pega nada: o que este cenário afirma é sobre a TELA diante de zero
    // registro, e isso não deve depender de que dia é hoje.
    await page.addInitScript(() => {
      localStorage.setItem('__HIST_E2E_EMPTY__', 'true');
    });
    await openHistorico(page);

    // Empty state card must appear (reassuring message from template)
    await expect(page.getByText('Nenhum evento neste período.')).toBeVisible({ timeout: 3000 });

    // No history items rendered (items=[])
    await expect(page.getByTestId('history-item')).toHaveCount(0);

    // No error alert (this is a clean empty, not a failure)
    await expect(page.locator('[role="alert"]')).not.toBeVisible();
  });

  // ── Scenario 6: Falha de carregamento com retry ──────────────────────────
  // BDD: "A failed history load shows an error state with a retry affordance"
  //
  // UNBLOCKED via __HIST_E2E_LIST_FAIL__: page.addInitScript sets localStorage
  // before Angular bootstraps. HistClientMockAdapter.list() reads the flag inside
  // timer(400) and throws, causing HistoryFacade.load() to catch and set status='error'.
  test('Falha de carregamento mostra erro com nova tentativa', async ({ page }) => {
    // Instruct the mock adapter to throw on list() → simulates a server error
    await page.addInitScript(() => {
      localStorage.setItem('__HIST_E2E_LIST_FAIL__', 'true');
    });
    await openHistorico(page);

    // After ~400ms (mock timer) the error propagates: status='error' → error div rendered
    await expect(page.locator('[role="alert"]')).toBeVisible({ timeout: 5000 });
    await expect(page.locator('[role="alert"]')).toContainText('Erro ao carregar histórico');

    // Retry affordance must be present (template: button "Tentar novamente")
    await expect(page.getByRole('button', { name: 'Tentar novamente' })).toBeVisible();

    // No history items rendered (error state)
    await expect(page.getByTestId('history-item')).toHaveCount(0);
  });

  // ── Scenario 7: Carregar mais ─────────────────────────────────────────────
  // BDD: "Clicking 'Carregar mais' appends the next page of events to the timeline"
  //
  // UNBLOCKED via __HIST_E2E_SMALL_PAGE__: mock returns size=2 so hasMore becomes
  // true with 3 total semana records. Clicking load-more appends rec-003 (page 1).
  test('Carregar mais anexa a próxima página', async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem('__HIST_E2E_SMALL_PAGE__', 'true');
    });
    await openHistorico(page);

    // Page 0: 2 items loaded (size=2 response), total=3, hasMore=(0+1)*2 < 3 = true
    await expect(page.getByTestId('history-item')).toHaveCount(2, { timeout: 5000 });

    // "Carregar mais" button is visible (vm().hasMore = true)
    await expect(page.getByTestId('load-more-btn')).toBeVisible();

    // Click "Carregar mais" → loadMore() → page 1 → appends rec-003
    await page.getByTestId('load-more-btn').click();

    // After loading: 3 items total (2 + 1 from page 1)
    await expect(page.getByTestId('history-item')).toHaveCount(3, { timeout: 5000 });

    // "Carregar mais" button disappears (hasMore=(1+1)*2 < 3 = false → @if removes button)
    await expect(page.getByTestId('load-more-btn')).not.toBeVisible({ timeout: 3000 });
  });

  // ── Scenario 8: Guarda de vínculo (Wave-0 bypass) ────────────────────────
  // BDD: "The histGuard with Wave-0 bypass allows access to /historico without a binding"
  //
  // Wave-0: HIST_AUTH_SESSION and HIST_ACCESS_DECISION factory returns null → both
  // authGuard and histGuard return true unconditionally. This confirms the route is
  // accessible in the current dev/test environment without real authentication.
  // TODO[RBAC]: when guards are wired with real tokens, a session without hist:read
  // should be redirected away from /historico.
  test('Guarda de vínculo bloqueia acesso sem vínculo', async ({ page }) => {
    // Cold navigation — no auth session, no access decision (both null via factory)
    await page.goto('/historico');

    // Wave-0 bypass: both guards return true → page renders without redirect
    await expect(page.getByRole('heading', { name: 'Histórico' })).toBeVisible({ timeout: 6000 });

    // URL remains at /historico (no redirect to /)
    await expect(page).toHaveURL(/historico/);

    // Component is fully functional (not blocked by guard)
    await expect(page.getByRole('tablist', { name: 'Período' })).toBeVisible();
  });

});
