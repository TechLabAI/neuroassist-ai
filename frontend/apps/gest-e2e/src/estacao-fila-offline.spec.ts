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
 *   `context.setOffline(true)` faz as requisições HTTP falharem com ERR_INTERNET_DISCONNECTED.
 *   Isso simula falha de rede e aciona o caminho `catch` do reportAdapter.submit(),
 *   que chama `reportQueue.enqueue(request)`.
 *
 *   Quando `context.setOffline(false)` é chamado, o event listener 'online' do
 *   ReportQueueService dispara processQueue(), que reenvia cada item da fila.
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

    await page.addInitScript(scriptSemearSessaoDispositivo());
    await page.addInitScript(scriptSkipDetector());

    await mockPerfilDisponivel(page);
    // Heartbeat pode falhar offline — o serviço absorve o erro (heartbeat nunca para).
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

    // Rota de relatos: capturar POSTs com o reportId
    await page.route('**/api/v1/reports', async (route, request) => {
      try {
        const body = await request.postDataJSON() as Record<string, unknown>;
        reportPostLog.push({ reportId: body['reportId'] as string, timestamp: Date.now() });
      } catch {}
      await route.fulfill({ status: 201, contentType: 'application/json', body: '{}' });
    });

    await gotoEstacao(page);
    await expect(page.locator('text=VIGILANDO')).toBeVisible({ timeout: 8_000 });

    // ── 1. Colocar offline ───────────────────────────────────────────────────
    await context.setOffline(true);

    // ── 2. Disparar gesto com rede offline ─────────────────────────────────
    // O reportAdapter.submit() falha com ERR_INTERNET_DISCONNECTED → enqueue().
    await triggerGesto(page);
    await page.waitForTimeout(1_000);

    // Nenhum POST chegou ainda (offline).
    expect(reportPostLog.length).toBe(0);

    // A tela deve mostrar "relato(s) aguardando envio" (contador de pendentes).
    await expect(page.locator('text=aguardando envio')).toBeVisible({ timeout: 4_000 });

    // ── 3. Restaurar rede ────────────────────────────────────────────────────
    await context.setOffline(false);

    // O event 'online' dispara processQueue(). Aguardar até 5 s para o reenvio.
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
