import { test, expect } from '@playwright/test';

/**
 * erros-e-degradacao — TREI e2e gate (P6 / FE-TREI-006)
 * Source: docs/.../e2e/erros-e-degradacao.feature
 * Status: stub scaffolded (D-494 promote). Implement in Layer 2.
 *
 * Covers: SPEC-07 error states (patient-not-found 404, llm-unavailable 503,
 * ConversationErrorPanel, correlationId display, discard-conflict 409).
 */

test.describe('Erros e degradação (SPEC-07)', () => {
  test('exibe ConversationErrorPanel quando o assistente retorna 503', async ({ page }) => {
    // Inject e2e flag to trigger mock 503 response
    await page.addInitScript(() => {
      localStorage.setItem('__TREI_E2E_LLM_FAIL__', 'true');
    });

    await page.goto('/treinamento');
    await page.getByTestId('start-session-btn').click();
    await page.getByTestId('chat-composer').waitFor();
    await page.getByTestId('composer-input').fill('test');
    await page.getByTestId('send-btn').click();

    // Error state should appear
    await expect(page.getByTestId('session-error')).toBeVisible({ timeout: 3000 });
  });

  test('exibe estado de erro de sessão quando patient-not-found (SPEC-07 gap G4)', async ({ page }) => {
    // TODO[FE-TREI-006]: wire patient-not-found mock flag and verify banner shown
    // on /treinamento with message, NOT creating a session (SPEC-07 gap from M3.5)
    await page.goto('/treinamento');
    await expect(page.getByTestId('empty-state')).toBeVisible();
  });

  test('descartar nó ativo retorna erro 409 sem remover da lista', async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem('__TREI_E2E_DISCARD_FAIL__', 'true');
    });
    await page.goto('/treinamento/sessao/propostas');
    // TODO[FE-TREI-006]: after materialize is wired, verify error shown on discard of ativo node
  });
});
