/**
 * E2E spec: estacao-fila-offline
 * Cenário: E6 da SPEC-09 §5
 *
 * E6 — Rede caída → relato enfileirado; rede volta → reenviado (uma vez)
 *   Afirma: o ReportQueueService enfileira relatos que falham por erro de rede e
 *   os reprocessa quando a conectividade é restaurada. O reportId idempotente garante
 *   que o reenvio não gera duplicata no backend.
 *
 * Técnica de offline:
 *   `page.route()` com `route.abort('failed')` simula falha de rede para relatos.
 *   Isso aciona o caminho `catch` do reportAdapter.submit(), que chama
 *   `reportQueue.enqueue(request)`.
 *
 *   IMPORTANTE: `context.setOffline(true)` NÃO interrompe requisições interceptadas
 *   por `page.route()` — o mock responde antes de chegar à rede, então offline não
 *   tem efeito sobre rotas mockadas. Para simular falha de rede em rotas mockadas
 *   usamos um flag JS e `route.abort()` condicionalmente.
 *
 *   `context.setOffline(false)` é necessário para que o browser dispare o evento
 *   `online` (que `ReportQueueService` ouve para acionar `processQueue()`).
 *   `context.setOffline(true)` é chamado antes do gesto para que `setOffline(false)`
 *   dispare o evento `online` corretamente.
 *
 * Conferência por mutação (a ser executada pelo validator):
 *   Remover o `window.addEventListener('online', ...)` em ReportQueueService →
 *   a fila não processa ao restaurar a rede, o segundo POST nunca ocorre,
 *   o teste fica vermelho.
 */

import { test, expect } from '@playwright/test';
import {
  scriptSemearSessaoDispositivo,
  scriptSkipDetector,
  mockPerfilDisponivel,
  mockHeartbeat,
  gotoEstacao,
  triggerGesto,
  DEVICE_ID_FIXTURE,
  ENGINE_FEATURE_REF_FIXTURE,
} from './estacao-e2e-helpers';

test.describe('estacao-fila-offline', () => {

  // ── E6 ─────────────────────────────────────────────────────────────────────
  test('E6 — rede caída → relato enfileirado; volta → reenviado uma vez', async ({ page, context }) => {
    const reportPostLog: Array<{ reportId: string; timestamp: number }> = [];

    // Flag controlado pelo spec — abortamos a rota de relatos quando offline.
    // `context.setOffline` não bloqueia rotas mockadas via page.route().
    let reportsSimulacaoOffline = false;

    await page.addInitScript(scriptSemearSessaoDispositivo());
    await page.addInitScript(scriptSkipDetector());

    await mockPerfilDisponivel(page);

    // Heartbeat: sempre fulfillado — o serviço absorve erros (heartbeat nunca para).
    await page.route('**/api/v1/edge/heartbeat', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          status: 'ok',
          serverTime: new Date().toISOString(),
          currentProfileVersion: 1,
          profileDrift: false,
        }),
      });
    });

    // Rota de relatos: abortar quando simulando offline, capturar e fulfillr quando online.
    await page.route('**/api/v1/reports', async (route, request) => {
      if (reportsSimulacaoOffline) {
        // Simular falha de rede — aciona o catch em reportAdapter.submit() → enqueue().
        await route.abort('failed');
        return;
      }
      try {
        const body = await request.postDataJSON() as Record<string, unknown>;
        reportPostLog.push({ reportId: body['reportId'] as string, timestamp: Date.now() });
      } catch {}
      await route.fulfill({ status: 201, contentType: 'application/json', body: '{}' });
    });

    await gotoEstacao(page);
    await expect(page.locator('text=VIGILANDO')).toBeVisible({ timeout: 8_000 });

    // ── 1. Colocar offline ───────────────────────────────────────────────────
    // Ambos necessários: o flag faz o route.abort() funcionar; o setOffline(true)
    // permite que o setOffline(false) mais adiante dispare o evento 'online' no browser.
    reportsSimulacaoOffline = true;
    await context.setOffline(true);

    // ── 2. Disparar gesto com rede offline ─────────────────────────────────
    // O reportAdapter.submit() falha com network error → enqueue().
    await triggerGesto(page);
    await page.waitForTimeout(1_000);

    // Nenhum POST chegou ainda (route.abort()).
    expect(reportPostLog.length).toBe(0);

    // A tela deve mostrar "relato(s) aguardando envio" (contador de pendentes).
    await expect(page.locator('text=aguardando envio')).toBeVisible({ timeout: 4_000 });

    // ── 3. Restaurar rede ────────────────────────────────────────────────────
    reportsSimulacaoOffline = false;
    await context.setOffline(false);

    // O evento 'online' dispara processQueue(). Aguardar até 5 s para o reenvio.
    await page.waitForTimeout(5_000);

    // Deve haver exatamente 1 POST (o relato reenviado).
    expect(reportPostLog.length).toBe(1);

    // O reportId deve conter deviceId e engineFeatureRef (formato canônico).
    const { reportId } = reportPostLog[0];
    expect(reportId).toContain(DEVICE_ID_FIXTURE);
    expect(reportId).toContain(ENGINE_FEATURE_REF_FIXTURE);

    // O contador de pendentes deve ter sumido da tela.
    await expect(page.locator('text=aguardando envio')).not.toBeVisible({ timeout: 3_000 });
  });

});
