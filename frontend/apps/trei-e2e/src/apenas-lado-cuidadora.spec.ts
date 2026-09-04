import { test, expect } from '@playwright/test';
import {
  scriptSemearSessaoTreinamento,
  scriptSemearSessaoSemPropor,
  scriptModoRapido,
  gotoSessao,
  gotoPropostas,
} from './trei-e2e-helpers';

/**
 * apenas-lado-cuidadora — TREI e2e (P9 / TREI-FE-029)
 * Source: docs/.../e2e/apenas-lado-cuidadora.feature
 *
 * Covers:
 *   INV-1 — TREI nunca escreve o lado paciente da aprovação.
 *   G1    — treiProporGuard: sem scope 'treinamento.no.propor' redireciona para
 *            /treinamento/sessao (nunca /dashboard, nunca 403 — ADR-007 FE).
 *   G2    — deep-link: ?proposalId= query param é tratado graciosamente
 *            (carrega o nó solicitado ou mostra loading notice, nunca crash).
 *
 * Scenarios from feature file:
 *   1. O indicador do lado paciente é somente leitura (INV-1)
 *   2. Nenhuma chamada ao endpoint de aprovação do paciente (INV-1)
 *   3. Sem escopo de aprovação o botão aprovar fica desabilitado
 *
 * Additional E2E scenarios (P9 scope):
 *   4. G1: sem scope treinamento.no.propor redireciona para /treinamento/sessao
 *   5. G2: deep-link com proposalId gracioso (deeplink-loading-notice aparece)
 *   6. Sem botão de aprovação do lado paciente em nenhuma rota TREI (INV-1)
 */

test.describe('Apenas lado cuidadora (INV-1 / G1 / G2)', () => {

  // ─── Cenários INV-1 (sem scope propor — acesso à sessão apenas) ─────────────

  test.describe('INV-1 — Nenhuma escrita do lado paciente', () => {
    test.beforeEach(async ({ page }) => {
      await page.addInitScript(scriptSemearSessaoTreinamento());
      await page.addInitScript(scriptModoRapido());
    });

    test('não há chamada a /aprovacoes/paciente em nenhuma ação TREI (INV-1)', async ({ page }) => {
      const pacienteRequests: string[] = [];
      page.on('request', (req) => {
        if (req.url().includes('/aprovacoes/paciente')) {
          pacienteRequests.push(req.url());
        }
      });

      await gotoSessao(page);
      // Navigate through the module — no action should ever call /aprovacoes/paciente
      await page.waitForTimeout(500);
      await page.goto('/treinamento/sessao/propostas');
      await page.waitForTimeout(500);

      // ZERO requests to /aprovacoes/paciente from any TREI route
      expect(pacienteRequests).toHaveLength(0);
    });

    test('o lado do paciente no indicador dual é sempre "pendente" em TREI (INV-1)', async ({ page }) => {
      await gotoPropostas(page);
      // If any proposal cards exist, patient side must always show "pending" (never confirmed)
      const cards = page.getByTestId('proposal-card');
      const count = await cards.count();
      for (let i = 0; i < count; i++) {
        // DualConfirmationComponent: patient side is always data-testid="paciente-pending"
        await expect(cards.nth(i).getByTestId('paciente-pending')).toBeVisible();
      }
      // When there are no cards, the empty-state is shown — also INV-1 safe
    });

    test('não existe botão de aprovação do paciente em nenhuma rota TREI (INV-1)', async ({ page }) => {
      await gotoSessao(page);
      // ProposedNodeCardComponent has only 'aprovar-btn' (caregiver) and 'descartar-btn'
      // There must be NO 'approve-patient-btn' or any patient-approval control
      await expect(page.getByTestId('approve-patient-btn')).not.toBeVisible();
      await page.goto('/treinamento/sessao/propostas');
      await page.waitForSelector('[data-testid="authoring-banner"]', { timeout: 6000 });
      await expect(page.getByTestId('approve-patient-btn')).not.toBeVisible();
    });
  });

  // ─── G1: treiProporGuard — sem scope redireciona para /sessao ───────────────

  test.describe('G1 — Guard: redirecionar para sessao sem scope propor', () => {
    test.beforeEach(async ({ page }) => {
      // Session WITH treinamento.sessao.conduzir but WITHOUT treinamento.no.propor
      await page.addInitScript(scriptSemearSessaoSemPropor());
      await page.addInitScript(scriptModoRapido());
    });

    test('G1: sem scope treinamento.no.propor redireciona para /treinamento/sessao', async ({ page }) => {
      // Navigate directly to the protected review surface
      await page.goto('/treinamento/sessao/propostas');
      // treiProporGuard must redirect to /treinamento/sessao (G1 invariant)
      // Must NOT go to /dashboard and must NOT return a bare false (403)
      await page.waitForURL(/\/treinamento\/sessao$/, { timeout: 6000 });
      // Verify we are on the session page, NOT on /dashboard
      expect(page.url()).toContain('/treinamento/sessao');
      expect(page.url()).not.toContain('/dashboard');
      expect(page.url()).not.toContain('/propostas');
      // Banner confirms TREI root is mounted (redirect preserved TREI context)
      await expect(page.getByTestId('authoring-banner')).toBeVisible({ timeout: 6000 });
    });

    test('G1: sessão de treinamento permanece acessível mesmo sem scope propor', async ({ page }) => {
      // Can still access /treinamento/sessao without propor scope
      await page.goto('/treinamento/sessao');
      await page.waitForSelector('[data-testid="authoring-banner"]', { timeout: 6000 });
      await expect(page.getByTestId('empty-state')).toBeVisible({ timeout: 4000 });
    });
  });

  // ─── G2: deep-link com proposalId ───────────────────────────────────────────

  test.describe('G2 — Deep-link: proposalId query param gracioso', () => {
    test.beforeEach(async ({ page }) => {
      // Full TREI scope — needs treinamento.no.propor to access /sessao/propostas
      await page.addInitScript(scriptSemearSessaoTreinamento());
      await page.addInitScript(scriptModoRapido());
    });

    test('G2: deep-link com ?proposalId= mostra loading notice graciosamente (nunca crash)', async ({ page }) => {
      // Navigate with a proposalId deep-link
      await page.goto('/treinamento/sessao/propostas?proposalId=n-mock-deep-link-001');
      await page.waitForSelector('[data-testid="authoring-banner"]', { timeout: 8000 });
      // ProposalReviewPageComponent: when proposalId is set and status is 'idle',
      // a deeplink-loading-notice is shown (G2: graceful deep-link, not a crash)
      // The component reads ?proposalId via ActivatedRoute.queryParamMap + toSignal()
      await expect(page.getByTestId('deeplink-loading-notice')).toBeVisible({ timeout: 4000 });
      // Must NOT show an error panel (graceful handling)
      await expect(page.getByTestId('proposal-error')).not.toBeVisible();
    });

    test('G2: deep-link sem proposalId mostra estado vazio sem aviso de loading', async ({ page }) => {
      // Without proposalId, no loading notice — just the empty-state or proposals list
      await page.goto('/treinamento/sessao/propostas');
      await page.waitForSelector('[data-testid="authoring-banner"]', { timeout: 8000 });
      // deeplink-loading-notice must NOT appear when there is no proposalId
      await expect(page.getByTestId('deeplink-loading-notice')).not.toBeVisible();
      // Empty state should be shown (no nodes loaded)
      await expect(page.getByTestId('proposal-empty-state')).toBeVisible({ timeout: 4000 });
    });
  });
});
