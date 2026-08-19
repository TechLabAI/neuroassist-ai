/**
 * E2E spec: DASH — Notification delivery (SSE + lista + acknowledge)
 * Issues: FE-DASH-026 (G5)
 * 5 scenarios — P6 implementation
 *
 * MOCK SEED STATE (DashNotificationsMockAdapter):
 *   pending: [{ dispatchId: 'd-mock-001', commandLabel: 'Ajuda', urgencia: 'emergencia',
 *               tier: 'full_screen_alarm', patientId: 'pac-marcio', raisedAt: 20min ago }]
 *
 * SSE stream (DashStreamClient):
 *   NotificationsFacade.load() calls streamClient.open('/panel/stream') after catch-up.
 *   In Wave-0 / mock env: EventSource connects to /panel/stream but no real events arrive;
 *   DashStreamClient.onerror triggers after 3 retries → fallback to long-poll.
 *   Long-poll mock returns empty (cursor='mock-cursor') → list remains as catch-up HTTP result.
 *   SSE event simulation requires a real event source or JS injection — deferred to P7.
 *
 * E2E flags (page.addInitScript → localStorage before Angular bootstraps):
 *   __DASH_E2E_NOTIFY_FAIL__  : listUnacknowledged() throws 503 → error state (G5b)
 *   __DASH_E2E_NOTIFY_EMPTY__ : listUnacknowledged() returns [] → "Tudo revisado" (G5a)
 *
 * WAVE-0: authGuard + dashGuard bypass active; /notificacoes accessible without auth.
 * acknowledge() is non-optimistic: evicts from list ONLY after port confirms (300ms mock).
 */
import { test, expect, Page } from '@playwright/test';

// ── Navigation helpers ─────────────────────────────────────────────────────────

/** Navigate to /notificacoes and wait for the heading. */
async function openNotifications(page: Page): Promise<void> {
  await page.goto('/notificacoes');
  await expect(page.getByRole('heading', { name: 'Notificações' })).toBeVisible({ timeout: 6000 });
}

/** Wait for the notification list to finish loading (mock 400ms delay). */
async function waitForList(page: Page): Promise<void> {
  // The "Ajuda" notification text appears after listUnacknowledged() completes (400ms mock)
  await expect(page.getByText('"Ajuda"')).toBeVisible({ timeout: 5000 });
}

// ── Tests ─────────────────────────────────────────────────────────────────────

test.describe('DASH — Notification delivery: lista + acknowledge (FE-DASH-026)', () => {

  // ── Scenario 1: Lista carrega com notificação pendente ─────────────────────
  // BDD: "Ao conectar o painel abre o stream e recupera notificações não reconhecidas"
  //
  // NotificationsFacade.load() → listUnacknowledged() → 400ms → 1 notification.
  // Template: @for (notification of facade.notifications()) → renders notification card
  // with commandLabel '"Ajuda"' and button "Revisei".
  test('Lista carrega com 1 notificação pendente e botão Revisei visível', async ({ page }) => {
    await openNotifications(page);
    await waitForList(page);

    // Notification card shows commandLabel in quotes: '"Ajuda"'
    await expect(page.getByText('"Ajuda"')).toBeVisible();

    // Urgency badge "emergencia"
    await expect(page.getByText('emergencia').first()).toBeVisible();

    // Acknowledge button
    await expect(page.getByRole('button', { name: 'Revisei' })).toBeVisible();

    // No "Tudo revisado" empty state (list has items)
    await expect(page.getByText('Tudo revisado')).not.toBeVisible();
  });

  // ── Scenario 2: Acknowledge remove notificação da lista ───────────────────
  // BDD: "Marcar notificação como lida reverte ao falhar o acknowledge" (positive case)
  //
  // Non-optimistic: clicking "Revisei" → facade.acknowledge('d-mock-001')
  //   → port.acknowledge() → 300ms mock delay → resolves → #notifications updated
  //   → list becomes empty → status='empty' → "Tudo revisado" rendered.
  test('Acknowledge remove notificação e mostra lista vazia', async ({ page }) => {
    await openNotifications(page);
    await waitForList(page);

    // Click "Revisei" → facade.acknowledge() → non-optimistic eviction
    await page.getByRole('button', { name: 'Revisei' }).click();

    // After 300ms mock delay + Angular change detection: notification removed
    // status='empty' → "Tudo revisado" visible (template @else if status==='empty')
    await expect(page.getByText('Tudo revisado')).toBeVisible({ timeout: 3000 });

    // "Ajuda" card no longer present
    await expect(page.getByText('"Ajuda"')).not.toBeVisible();

    // "Revisei" button gone (no more items)
    await expect(page.getByRole('button', { name: 'Revisei' })).not.toBeVisible();
  });

  // ── Scenario 3: Acknowledge idempotente (Wave-0) ──────────────────────────
  // BDD: "Reconhecer o mesmo dispatchId duas vezes é idempotente"
  //
  // After first acknowledge, the notification is removed from the list.
  // A second click cannot happen (button is gone). The mock already filters
  // this.pending by dispatchId — second ack would find no item and resolve
  // cleanly (filter is idempotent). Wave-0: only verifies UI stability.
  test('Idempotência: após Revisei o botão desaparece e a UI não crasha', async ({ page }) => {
    await openNotifications(page);
    await waitForList(page);

    // Verify button is present before click
    await expect(page.getByRole('button', { name: 'Revisei' })).toBeVisible();

    // Click — non-optimistic acknowledge
    await page.getByRole('button', { name: 'Revisei' }).click();

    // After eviction: empty state shows (confirms button rendered once, now gone)
    await expect(page.getByText('Tudo revisado')).toBeVisible({ timeout: 3000 });

    // Button is definitively absent (prevents a second click)
    await expect(page.getByRole('button', { name: 'Revisei' })).not.toBeVisible();

    // No crash: page is stable
    await expect(page.getByRole('heading', { name: 'Notificações' })).toBeVisible();
  });

  // ── Scenario 4 (G5a): Lista vazia forçada → "Tudo revisado" ───────────────
  // BDD: "simular listagem vazia → verificar EmptyOrErrorStateComponent visível"
  //
  // UNBLOCKED via __DASH_E2E_NOTIFY_EMPTY__: listUnacknowledged() returns []
  // → NotificationsFacade sets status='empty' → template renders "Tudo revisado".
  test('G5a — Lista vazia forçada mostra estado tranquilizador Tudo revisado', async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem('__DASH_E2E_NOTIFY_EMPTY__', 'true');
    });
    await openNotifications(page);

    // After 400ms mock delay with empty result: status='empty' → empty state card
    await expect(page.getByText('Tudo revisado')).toBeVisible({ timeout: 5000 });

    // Reassuring sub-message
    await expect(page.getByText('Nenhuma notificação pendente no momento.')).toBeVisible();

    // No notification cards (empty list)
    await expect(page.getByRole('button', { name: 'Revisei' })).not.toBeVisible();
  });

  // ── Scenario 5 (G5b): Erro de rede → estado de erro inline ───────────────
  // BDD: "simular erro de rede em listagem → verificar estado de erro inline (Home intacto)"
  //
  // UNBLOCKED via __DASH_E2E_NOTIFY_FAIL__: listUnacknowledged() throws status=503
  // → NotificationsFacade catches → sets status='error' → template renders error
  // div (inline, no modal) with message and "Tentar novamente" button.
  // Shell chrome (sidebar, topbar) remains intact (error is local to content area).
  test('G5b — Erro de rede mostra estado de erro inline sem bloquear shell', async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem('__DASH_E2E_NOTIFY_FAIL__', 'true');
    });
    await openNotifications(page);

    // After ~400ms mock delay: error thrown → status='error'
    // Template: "Erro ao carregar notificações." (from facade.error()?.message)
    await expect(page.getByText(/Erro ao carregar notificações\./i)).toBeVisible({ timeout: 5000 });

    // Retry affordance: "Tentar novamente" (facade.retry())
    await expect(page.getByRole('button', { name: 'Tentar novamente' })).toBeVisible();

    // Shell chrome intact: sidebar still visible (error is inline content, not shell)
    await expect(page.locator('nav.sidebar')).toBeVisible();

    // No "Tudo revisado" (error state, not empty state)
    await expect(page.getByText('Tudo revisado')).not.toBeVisible();

    // No notification cards
    await expect(page.getByRole('button', { name: 'Revisei' })).not.toBeVisible();
  });

});
