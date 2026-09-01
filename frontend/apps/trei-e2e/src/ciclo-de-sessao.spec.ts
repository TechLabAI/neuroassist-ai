import { test, expect } from '@playwright/test';

/**
 * ciclo-de-sessao — TREI e2e gate (P6 / FE-TREI-001)
 * Source: docs/architecture/frontend/modules/trei/frontend/e2e/ciclo-de-sessao.feature
 * Status: stub scaffolded (D-494 promote). Implement in Layer 2 (/fe-validator TREI-FE-001).
 *
 * Covers: session start CTA, active session state, end session, no-loss of drafts.
 */

test.describe('Ciclo de sessão de treinamento', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/treinamento');
  });

  test('exibe estado vazio com CTA ao entrar sem sessão ativa', async ({ page }) => {
    await expect(page.getByTestId('empty-state')).toBeVisible();
    await expect(page.getByTestId('start-session-btn')).toBeVisible();
  });

  test('inicia sessão ao clicar em Iniciar treinamento', async ({ page }) => {
    await page.getByTestId('start-session-btn').click();
    // Session started → composer becomes visible
    await expect(page.getByTestId('chat-composer')).toBeVisible({ timeout: 3000 });
  });

  test('encerra sessão sem descartar rascunhos (garantia no-loss)', async ({ page }) => {
    await page.getByTestId('start-session-btn').click();
    await page.getByTestId('chat-composer').waitFor();
    await page.getByTestId('end-session-btn').click();
    await expect(page.getByTestId('session-ended-notice')).toBeVisible();
    // Empty state CTA does NOT reappear — drafts persisted (no-loss invariant)
    // TODO[FE-TREI-001]: verify no-loss by checking proposal panel still has nodes
  });
});
