import { test, expect } from '@playwright/test';

/**
 * autoria-e-aprovacao-cuidadora — TREI e2e gate (P6 / FE-TREI-002)
 * Source: docs/.../e2e/autoria-e-aprovacao-cuidadora.feature
 * Status: stub scaffolded (D-494 promote). Implement in Layer 2 (/fe-validator TREI-FE-013).
 *
 * Covers: send turn → assistant responds → materialize → approve caregiver side.
 */

test.describe('Autoria e aprovação da cuidadora', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/treinamento');
    // Start session
    await page.getByTestId('start-session-btn').click();
    await page.getByTestId('chat-composer').waitFor();
  });

  test('envia turno e o assistente responde com propostas', async ({ page }) => {
    await page.getByTestId('composer-input').fill('phrases for asking for water');
    await page.getByTestId('send-btn').click();
    // Assistant thinking indicator appears
    await expect(page.getByTestId('assistant-thinking')).toBeVisible();
    // Then proposals appear (mock ~1200ms)
    await expect(page.getByTestId('proposals-list')).toBeVisible({ timeout: 3000 });
  });

  test('exibe badge de divulgação de IA nas mensagens do assistente (TECH-007)', async ({ page }) => {
    await page.getByTestId('composer-input').fill('everyday phrases');
    await page.getByTestId('send-btn').click();
    await expect(page.getByTestId('proposals-list')).toBeVisible({ timeout: 3000 });
    // AI disclosure badge
    await expect(page.getByText('Gerado por IA — verifique antes de aprovar')).toBeVisible();
  });

  test('aprova proposta pelo lado da cuidadora (INV-1: apenas cuidadora)', async ({ page }) => {
    // Navigate to proposal review
    await page.getByTestId('composer-input').fill('test');
    await page.getByTestId('send-btn').click();
    await page.getByTestId('proposals-list').waitFor({ timeout: 3000 });
    // Link to proposal review
    await page.getByTestId('review-proposals-link').click();
    // Approve button should be visible
    // TODO[FE-TREI-013]: after materialize is wired, verify approval updates confirmadoCuidadora
  });
});
