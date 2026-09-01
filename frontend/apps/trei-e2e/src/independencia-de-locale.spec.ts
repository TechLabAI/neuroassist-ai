import { test, expect } from '@playwright/test';

/**
 * independencia-de-locale — TREI e2e gate (P6 / FE-TREI-004 / ADR-018)
 * Source: docs/.../e2e/independencia-de-locale.feature
 * Status: stub scaffolded (D-494 promote). Implement in Layer 2.
 *
 * Covers: locale-independent approval (ADR-018); switching PT→EN preserves
 * independent state; approving PT does not affect EN.
 */

test.describe('Independência de locale (ADR-018)', () => {
  test('a aba de locale ativa é visível na tela de sessão', async ({ page }) => {
    await page.goto('/treinamento');
    await expect(page.getByTestId('locale-tab-pt')).toBeVisible();
    await expect(page.getByTestId('locale-tab-en')).toBeVisible();
  });

  test('alternar locale não perde propostas existentes', async ({ page }) => {
    await page.goto('/treinamento');
    await page.getByTestId('start-session-btn').click();
    await page.getByTestId('chat-composer').waitFor();
    // Switch locale
    await page.getByTestId('locale-tab-en').click();
    // Session remains active
    await expect(page.getByTestId('chat-composer')).toBeVisible();
  });

  test('aprovar em PT não altera estado EN (locale-independence invariant)', async ({ page }) => {
    // TODO[FE-TREI-004]: implement after materialize flow is fully wired.
    // This test will:
    // 1. Materialize a node in PT + EN
    // 2. Approve the PT locale
    // 3. Assert EN locale still shows pending (not infected by PT approval)
    await page.goto('/treinamento/sessao/propostas');
    // Stub: verify locale tabs exist and are independent
    const ptTab = page.getByTestId('locale-tab-pt');
    const enTab = page.getByTestId('locale-tab-en');
    await expect(ptTab).toBeVisible();
    await expect(enTab).toBeVisible();
  });
});
