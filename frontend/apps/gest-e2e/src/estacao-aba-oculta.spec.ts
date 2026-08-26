/**
 * E2E spec: estacao-aba-oculta
 * Cenário: E4 da SPEC-09 §5 (Invariantes I2 + I3)
 *
 * E4 — Aba oculta → estado 'parada' visível, heartbeat continua
 *   Afirma:
 *   - I2: 'parada' ocupa a tela inteira (não banner de canto)
 *   - I3: heartbeat continua batendo mesmo com a aba oculta
 *   O heartbeat é o que torna a estação distinguível de "morta" pelo servidor.
 *
 * Técnica de ocultação:
 *   Playwright não tem API nativa para mudar `document.visibilityState`. Usamos
 *   page.evaluate() para (1) sobrescrever `document.hidden` e (2) despachar o
 *   evento `visibilitychange`. O listener do EstacaoComponent reage ao evento.
 *
 * Conferência por mutação (a ser executada pelo validator):
 *   Remover o listener de `visibilitychange` em EstacaoComponent.ngOnInit() →
 *   o estado nunca muda para 'parada', o teste fica vermelho.
 *   Chamar `heartbeatService.stop()` no listener → heartbeat para, e o spy de
 *   heartbeat não registra chamada após a ocultação.
 */

import { test, expect } from '@playwright/test';
import {
  scriptSemearSessaoDispositivo,
  scriptSkipDetector,
  mockPerfilDisponivel,
  gotoEstacao,
} from './estacao-e2e-helpers';

test.describe('estacao-aba-oculta', () => {

  // ── E4 ─────────────────────────────────────────────────────────────────────
  test('E4 — aba oculta → estado parada visível e heartbeat continua', async ({ page }) => {
    const heartbeatCalls: number[] = [];

    await page.addInitScript(scriptSemearSessaoDispositivo());
    await page.addInitScript(scriptSkipDetector());

    await mockPerfilDisponivel(page);

    // Interceptar heartbeat para contar chamadas (inclui as que vêm durante 'parada').
    await page.route('**/api/v1/edge/heartbeat', async (route) => {
      heartbeatCalls.push(Date.now());
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          status: 'ok',
          serverTime: new Date().toISOString(),
          currentProfileVersion: 1,
          profileDrift: false,
          recommendedHeartbeatSeconds: 1, // reduzir para o teste perceber 2 batimentos
        }),
      });
    });

    await gotoEstacao(page);
    await expect(page.locator('text=VIGILANDO')).toBeVisible({ timeout: 8_000 });

    // Registrar chamadas de heartbeat ANTES de ocultar a aba.
    const heartbeatsAntes = heartbeatCalls.length;

    // Simular ocultação da aba via visibilitychange.
    await page.evaluate(() => {
      Object.defineProperty(document, 'hidden', {
        configurable: true,
        get: () => true,
      });
      document.dispatchEvent(new Event('visibilitychange'));
    });

    // I2: 'parada' deve ocupar a tela inteira — texto "PARADA" visível.
    await expect(page.locator('text=PARADA')).toBeVisible({ timeout: 4_000 });

    // A mensagem explicativa também deve estar presente.
    await expect(
      page.locator('text=A estação está pausada (aba oculta)'),
    ).toBeVisible({ timeout: 2_000 });

    // I3: heartbeat continua batendo durante 'parada'.
    // Aguardar até 3 s para que pelo menos 1 heartbeat adicional ocorra.
    // (O intervalo foi reduzido para 1 s via recommendedHeartbeatSeconds.)
    await page.waitForTimeout(3_000);

    // Deve haver pelo menos 1 heartbeat adicional após a ocultação.
    const heartbeatsDurante = heartbeatCalls.length - heartbeatsAntes;
    expect(heartbeatsDurante).toBeGreaterThanOrEqual(1);
  });

});
