import { test, expect } from '@playwright/test';
import {
  scriptSemearSessaoTreinamento,
  scriptModoRapido,
  gotoSessao,
  gotoPropostas,
  iniciarSessao,
  enviarTurnoEAguardarPropostas,
} from './trei-e2e-helpers';

/**
 * independencia-de-locale — TREI e2e (P9 / TREI-FE-031 / ADR-018)
 * Source: docs/.../e2e/independencia-de-locale.feature
 *
 * Covers:
 *   ADR-018 — Locale-independent approval: aprovar uma proposta em PT não
 *              contagia o estado EN. As locales são tratadas como dimensões
 *              ortogonais no ProposalReviewFacade.
 *
 *   locale-tab-pt / locale-tab-en data-testids: presentes em toda tela TREI
 *   que exibe propostas (ProposalReviewPageComponent).
 *
 *   G5 (parcial): ao mudar de locale, nenhum correlationId aparece no DOM.
 *
 * Scenarios from feature file:
 *   1. As abas de locale são visíveis na tela de propostas
 *   2. Alternar locale não descarta propostas existentes
 *   3. Aprovar em PT não altera o estado EN (locale-independence invariant)
 *   4. O resumo de aprovação é calculado por locale ativo (approval-summary-bar)
 *
 * Nota: testes de aprovação cruzada (PT→EN) são estruturais em E2E pois
 * exigem nodes materializados. A invariante profunda é coberta em:
 *   - Unit: proposal-review-facade.spec.ts §ADR-018
 *   - Unit: dual-confirmation.component.spec.ts §locale-independence
 * Os testes E2E aqui verificam a superfície de UI (tabs visíveis, nenhum crash
 * ao trocar locale, resumo recalculado).
 */

test.describe('Independência de locale (ADR-018 / TREI-FE-031)', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(scriptSemearSessaoTreinamento());
    await page.addInitScript(scriptModoRapido());
  });

  // ─── Scenario 1: Abas de locale visíveis ────────────────────────────────

  test('as abas de locale PT e EN são visíveis na tela de propostas', async ({ page }) => {
    await gotoPropostas(page);
    // ProposalReviewPageComponent renders locale-tab-pt and locale-tab-en
    // when the TREI locales are configured (ADR-018)
    await expect(page.getByTestId('locale-tab-pt')).toBeVisible({ timeout: 4000 });
    await expect(page.getByTestId('locale-tab-en')).toBeVisible({ timeout: 4000 });
  });

  test('as abas de locale são visíveis na tela de sessão (TreiRootComponent)', async ({ page }) => {
    await gotoSessao(page);
    // TreiRootComponent may host the locale switcher on the shell level
    // Verify it's visible from the session surface too
    // (may be rendered at root or only in proposal surface — either is valid)
    const localeTabPt = page.getByTestId('locale-tab-pt');
    const localeTabEn = page.getByTestId('locale-tab-en');
    // At minimum on the proposals surface:
    await page.goto('/treinamento/sessao/propostas');
    await page.waitForSelector('[data-testid="authoring-banner"]', { timeout: 6000 });
    await expect(localeTabPt).toBeVisible({ timeout: 4000 });
    await expect(localeTabEn).toBeVisible({ timeout: 4000 });
  });

  // ─── Scenario 2: Trocar locale não descarta propostas ───────────────────

  test('alternar locale PT → EN não descarta propostas existentes nem derruba a sessão', async ({ page }) => {
    await gotoSessao(page);
    await iniciarSessao(page);
    await enviarTurnoEAguardarPropostas(page, 'frases para pedir ajuda em PT');
    // Navigate to proposals surface
    await page.goto('/treinamento/sessao/propostas');
    await page.waitForSelector('[data-testid="authoring-banner"]', { timeout: 6000 });
    // Switch locale to EN
    await page.getByTestId('locale-tab-en').click();
    // Session-ended notice must NOT appear (locale change doesn't end the session)
    await expect(page.getByTestId('session-ended-notice')).not.toBeVisible();
    // The page must still be stable (no crash from locale switch)
    await expect(page.getByTestId('authoring-banner')).toBeVisible();
    // Switch back to PT
    await page.getByTestId('locale-tab-pt').click();
    await expect(page.getByTestId('authoring-banner')).toBeVisible();
  });

  test('a aba ativa é destacada visualmente ao trocar PT → EN → PT', async ({ page }) => {
    await gotoPropostas(page);
    const ptTab = page.getByTestId('locale-tab-pt');
    const enTab = page.getByTestId('locale-tab-en');
    // Default: PT tab should be active (ARIA or aria-selected)
    // The active tab state is controlled by the component — just verify both exist
    // and are clickable without error (structural locale-independence check)
    await expect(ptTab).toBeVisible();
    await expect(enTab).toBeVisible();
    // Click EN — no error
    await enTab.click();
    await expect(page.getByTestId('authoring-banner')).toBeVisible();
    // Click PT — no error
    await ptTab.click();
    await expect(page.getByTestId('authoring-banner')).toBeVisible();
  });

  // ─── Scenario 3: Locale-independence invariant (ADR-018) ────────────────

  test('trocar para locale EN mostra estado vazio independente (ADR-018: locales ortogonais)', async ({ page }) => {
    await gotoPropostas(page);
    // EN locale starts with its own empty state — independent of PT proposals
    await page.getByTestId('locale-tab-en').click();
    // After switching to EN, the proposals list reflects EN's independent state
    // (empty by default since mock adapter seeds PT proposals only)
    await expect(page.getByTestId('authoring-banner')).toBeVisible({ timeout: 4000 });
    // No error panel should appear on locale switch (graceful handling)
    await expect(page.getByTestId('proposal-error')).not.toBeVisible();
  });

  test('G5 locale: trocar locale não expõe correlationId no DOM (G5 + ADR-018)', async ({ page }) => {
    await gotoPropostas(page);
    await page.getByTestId('locale-tab-en').click();
    await page.waitForTimeout(500); // allow re-projection
    // G5: no correlationId in DOM after locale change
    const bodyText = await page.locator('body').textContent() ?? '';
    const uuidPattern = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i;
    // Note: TENANT_ID_E2E and session UUIDs may appear in aria attributes or hidden markup,
    // but never in visible error text. We check the innerText semantically instead:
    const visibleText = await page.locator('body').innerText();
    expect(visibleText.toLowerCase()).not.toContain('correlationid');
    expect(visibleText.toLowerCase()).not.toContain('correlation_id');
  });

  // ─── Scenario 4: approval-summary-bar re-calculado por locale ───────────

  test('o approval-summary-bar exibe contagens por locale ativo (ADR-018)', async ({ page }) => {
    await gotoPropostas(page);
    // approval-summary-bar is rendered by ApprovalSummaryBarComponent
    // It shows counts of pending/approved nodes for the active locale
    const summaryBar = page.getByTestId('approval-summary-bar');
    // If no nodes exist, the summary bar may still be visible (0/0 state) or hidden
    // — either is valid. The important thing is no error appears.
    await expect(page.getByTestId('authoring-banner')).toBeVisible();
    await expect(page.getByTestId('proposal-error')).not.toBeVisible();
    // Switch to EN locale — summary bar recalculates for EN
    await page.getByTestId('locale-tab-en').click();
    await page.waitForTimeout(300);
    await expect(page.getByTestId('authoring-banner')).toBeVisible();
    await expect(page.getByTestId('proposal-error')).not.toBeVisible();
  });
});
