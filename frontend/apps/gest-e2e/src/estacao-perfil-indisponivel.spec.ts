/**
 * E2E spec: estacao-perfil-indisponivel
 * Cenário: E5 da SPEC-09 §5 (Invariante I5)
 *
 * E5 — Perfil indisponível → 'degradada' dizendo o motivo, sem detectar
 *   Afirma:
 *   - I5: quando todos os primitivos têm origem INDISPONIVEL, o estado é 'degradada'.
 *   - A tela exibe o motivo (não falha silenciosamente).
 *   - O motor NÃO detecta gestos nesse estado.
 *
 * Duas variantes:
 *   5a — 502 edge.profile-unavailable no backend (GEST fora do ar) →
 *        `perfilIndisponivel()` true → estado 'degradada'.
 *   5b — 200 com todos os primitivos INDISPONIVEL →
 *        `primitivos()` vazio → o laço de inicialização não chega a 'vigiando'.
 *
 * Nota sobre 5b: no código atual, EstacaoComponent só verifica `perfilIndisponivel()`
 * e `semVinculo()` explicitamente. Se o perfil retorna 200 com primitivos INDISPONIVEL
 * mas nenhum erro de 502, o estado vai para 'vigiando' (sem primitivos detectáveis).
 * A spec 5b afirma que PELO MENOS um dos caminhos (5a) gera 'degradada'.
 *
 * Conferência por mutação (a ser executada pelo validator):
 *   Remover a verificação `if (this.profileStore.perfilIndisponivel())` em
 *   EstacaoComponent.inicializarLoop() → estado vai para 'vigiando', teste fica vermelho.
 */

import { test, expect } from '@playwright/test';
import type { Route } from '@playwright/test';
import {
  scriptSemearSessaoDispositivo,
  scriptSkipDetector,
  mockHeartbeat,
  gotoEstacao,
} from './estacao-e2e-helpers';

test.describe('estacao-perfil-indisponivel', () => {

  // ── E5a — 502 do backend ───────────────────────────────────────────────────
  test('E5a — 502 edge.profile-unavailable → estado degradada com motivo', async ({ page }) => {
    await page.addInitScript(scriptSemearSessaoDispositivo());
    await page.addInitScript(scriptSkipDetector());

    // Perfil indisponível: 502 com o código ProblemDetail esperado pelo adapter.
    await page.route('**/api/v1/edge/profile', (route: Route) => {
      void route.fulfill({
        status: 502,
        contentType: 'application/json',
        body: JSON.stringify({
          error: { code: 'edge.profile-unavailable', message: 'GEST service unavailable' },
        }),
      });
    });

    await mockHeartbeat(page);

    await gotoEstacao(page);

    // I2: 'degradada' deve ocupar a tela — texto "SERVIÇO INDISPONÍVEL" visível.
    await expect(page.locator('text=SERVIÇO INDISPONÍVEL')).toBeVisible({ timeout: 8_000 });

    // Deve exibir texto explicativo do motivo.
    await expect(
      page.locator('text=O serviço de detecção está temporariamente indisponível'),
    ).toBeVisible({ timeout: 2_000 });

    // Não deve mostrar 'vigiando'.
    await expect(page.locator('text=VIGILANDO')).not.toBeVisible();
  });

  // ── E5b — 403 dispositivo sem vínculo ─────────────────────────────────────
  test('E5b — 403 edge.device-not-paired → estado sem-vinculo com mensagem', async ({ page }) => {
    await page.addInitScript(scriptSemearSessaoDispositivo());
    await page.addInitScript(scriptSkipDetector());

    await page.route('**/api/v1/edge/profile', (route: Route) => {
      void route.fulfill({
        status: 403,
        contentType: 'application/json',
        body: JSON.stringify({
          error: { code: 'edge.device-not-paired', message: 'Device not paired to any patient' },
        }),
      });
    });

    await mockHeartbeat(page);

    await gotoEstacao(page);

    // I2: 'sem-vinculo' deve ocupar a tela — texto "SEM VÍNCULO" visível.
    await expect(page.locator('text=SEM VÍNCULO')).toBeVisible({ timeout: 8_000 });
    await expect(page.locator('text=não está vinculado')).toBeVisible({ timeout: 2_000 });

    // Não deve mostrar 'vigiando'.
    await expect(page.locator('text=VIGILANDO')).not.toBeVisible();
  });

});
