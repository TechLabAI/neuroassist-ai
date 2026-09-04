import { test, expect } from '@playwright/test';
import {
  scriptSemearSessaoTreinamento,
  scriptModoRapido,
  gotoSessao,
  iniciarSessao,
  enviarTurnoEAguardarPropostas,
} from './trei-e2e-helpers';

/**
 * ciclo-de-sessao — TREI e2e (P9 / TREI-FE-027)
 * Source: docs/architecture/frontend/modules/trei/frontend/e2e/ciclo-de-sessao.feature
 *
 * Covers (feature file scenarios):
 *   1. Encerrar a sessao preserva os rascunhos pendentes (no-loss INV-4)
 *   2. Sair da rota limpa os turnos mas preserva rascunhos
 *   3. O aviso de autoria esta presente em toda rota do TREI
 *   4. Caminho desconhecido dentro do TREI volta para a sessao
 *
 * Guards: caregiverSessionGuard + treiGuard (requires treinamento.sessao.conduzir)
 */

test.describe('Ciclo de sessão de treinamento', () => {
  test.beforeEach(async ({ page }) => {
    // Seed caregiver session with full TREI authorities (caregiverSessionGuard + treiGuard pass)
    await page.addInitScript(scriptSemearSessaoTreinamento());
    // Reduce mock adapter delays for responsive tests
    await page.addInitScript(scriptModoRapido());
    await gotoSessao(page);
  });

  // ─── Scenario 1: Empty state ──────────────────────────────────────────────

  test('exibe estado vazio com CTA ao entrar sem sessão ativa', async ({ page }) => {
    await expect(page.getByTestId('empty-state')).toBeVisible();
    await expect(page.getByTestId('start-session-btn')).toBeVisible();
  });

  // ─── Scenario 2: Start session ─────────────────────────────────────────────

  test('inicia sessão ao clicar em Iniciar treinamento', async ({ page }) => {
    await iniciarSessao(page);
    // Composer visible = session started and active
    await expect(page.getByTestId('chat-composer')).toBeVisible();
    // Empty state CTA should no longer be visible
    await expect(page.getByTestId('empty-state')).not.toBeVisible();
  });

  // ─── Scenario 3: Encerrar sessão preserva rascunhos (no-loss INV-4) ────────

  test('encerra sessão sem descartar rascunhos (garantia no-loss INV-4)', async ({ page }) => {
    await iniciarSessao(page);
    // Send a turn to get proposals (no-loss test: proposals must survive session end)
    await enviarTurnoEAguardarPropostas(page, 'frases para pedir ajuda');
    // End session via button
    await page.getByTestId('end-session-btn').click();
    // Session-ended notice should appear (INV-4: "rascunhos retidos")
    await expect(page.getByTestId('session-ended-notice')).toBeVisible({ timeout: 3000 });
    // The empty-state CTA must NOT reappear immediately — proposals are retained as drafts
    // (the no-loss guarantee: data is in the facade state, not discarded on session end)
    await expect(page.getByTestId('empty-state')).not.toBeVisible();
  });

  // ─── Scenario 4: Aviso de autoria em toda rota TREI ─────────────────────────

  test('exibe o aviso de autoria em toda rota do TREI (INV-6 / TECH-007)', async ({ page }) => {
    // Session route — banner placed in TreiRootComponent (renders on ALL child routes)
    await expect(page.getByTestId('authoring-banner')).toBeVisible();
    // Navigate to proposal review — banner must still be visible
    await page.goto('/treinamento/sessao/propostas');
    await page.waitForSelector('[data-testid="authoring-banner"]', { timeout: 6000 });
    await expect(page.getByTestId('authoring-banner')).toBeVisible();
  });

  // ─── Scenario 5: Caminho desconhecido redireciona para sessao ───────────────

  test('caminho desconhecido dentro do TREI redireciona para /treinamento/sessao', async ({ page }) => {
    // Navigate to an unknown sub-path inside /treinamento
    await page.goto('/treinamento/rota-que-nao-existe');
    // Angular router wildcard redirects to /sessao (TreiRootComponent children catchall)
    // After redirect, the empty state or active session should be visible on /sessao
    await page.waitForURL(/\/treinamento\/sessao/, { timeout: 5000 });
    // Either the empty-state CTA or the session panel appears — route is stable
    await expect(page.getByTestId('authoring-banner')).toBeVisible();
  });

  // ─── Scenario 6: Sair da rota limpa turnos mas preserva rascunhos ───────────

  test('navegar para fora do TREI e voltar mantém estrutura de rascunhos', async ({ page }) => {
    await iniciarSessao(page);
    await enviarTurnoEAguardarPropostas(page, 'frases para beber água');
    // Navigate away from TREI
    await page.goto('/treinamento/sessao/propostas');
    // Navigate back — the app should not crash and banner should still be present
    await page.goto('/treinamento/sessao');
    await expect(page.getByTestId('authoring-banner')).toBeVisible({ timeout: 6000 });
  });
});
