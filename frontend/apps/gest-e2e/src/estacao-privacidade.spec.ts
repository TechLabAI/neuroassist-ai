/**
 * E2E spec: estacao-privacidade
 * Cenário: E8 da SPEC-09 §5 (Invariante I1)
 *
 * E8 — Nenhum quadro/landmark/campo não declarado em qualquer requisição
 *   Afirma: I1 — nenhum quadro de vídeo, landmark cru ou campo não declarado sai
 *   do dispositivo. O backend (EVNT-023) rejeita corpos com campos extras.
 *
 * Campos proibidos em qualquer requisição saindo do cliente:
 *   - rawFramePayload (ou qualquer variante: frame, videoFrame, imageData, landmarks)
 *   - deviceTenant (sobrescrito pelo servidor com o tenant_id do JWT)
 *   - Campos não listados nos DTOs canônicos de SPEC-09 §2
 *
 * Estratégia:
 *   Monitorar TODAS as requisições da página durante um ciclo completo
 *   (inicialização + 1 gesto + 1 heartbeat) e verificar que nenhuma contém
 *   os campos proibidos.
 *
 * Conferência por mutação (a ser executada pelo validator):
 *   Adicionar `rawFramePayload: '<base64>'` ao corpo do relato em
 *   EstacaoComponent.handleGesture() → o campo aparece no body capturado,
 *   o teste fica vermelho.
 */

import { test, expect } from '@playwright/test';
import {
  scriptSemearSessaoDispositivo,
  scriptSkipDetector,
  mockPerfilDisponivel,
  gotoEstacao,
  triggerGesto,
} from './estacao-e2e-helpers';

/** Campos que NUNCA devem aparecer em nenhuma requisição de saída. */
const CAMPOS_PROIBIDOS = [
  'rawFramePayload',
  'frame',
  'videoFrame',
  'imageData',
  'landmarks',
  'faceLandmarks',
  'deviceTenant',
] as const;

test.describe('estacao-privacidade', () => {

  // ── E8 ─────────────────────────────────────────────────────────────────────
  test('E8 — nenhuma requisição contém quadro, landmark ou campo não declarado', async ({ page }) => {
    // Coletar todos os request bodies enviados durante o teste.
    const allRequestBodies: Array<{ url: string; body: Record<string, unknown> }> = [];

    await page.addInitScript(scriptSemearSessaoDispositivo());
    await page.addInitScript(scriptSkipDetector());

    await mockPerfilDisponivel(page);

    // Stub heartbeat e relatos (registrados ANTES do catch-all, portanto executados
    // DEPOIS na cadeia do Playwright — last-registered runs first).
    await page.route('**/api/v1/edge/heartbeat', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ status:'ok', serverTime: new Date().toISOString(), currentProfileVersion:1, profileDrift:false }),
      });
    });
    await page.route('**/api/v1/reports', async (route) => {
      await route.fulfill({ status: 201, contentType: 'application/json', body: '{}' });
    });

    // Interceptar TODAS as requisições POST para APIs da aplicação.
    // DEVE ser registrado POR ÚLTIMO para que seja executado PRIMEIRO na cadeia
    // do Playwright (last-registered = first-executed). Isso garante que o body
    // é capturado antes que os stubs mais específicos acima consumam a requisição.
    //
    // IMPORTANTE: usar route.fallback() e NÃO route.continue().
    //   route.continue() → envia para a REDE (o backend não existe em e2e → erro).
    //   route.fallback() → passa para o próximo handler registrado (os stubs acima).
    // Sem fallback(), os stubs de heartbeat e reports nunca são alcançados.
    await page.route('**/*', async (route, request) => {
      if (request.method() === 'POST' || request.method() === 'PUT') {
        try {
          const body = await request.postDataJSON() as Record<string, unknown>;
          allRequestBodies.push({ url: request.url(), body });
        } catch {
          // Corpo não é JSON — ignorar (ex.: multipart)
        }
      }
      await route.fallback();
    });

    await gotoEstacao(page);
    await expect(page.locator('text=VIGILANDO')).toBeVisible({ timeout: 8_000 });

    // Disparar um gesto para gerar um relato.
    await triggerGesto(page);
    await page.waitForTimeout(1_000);

    // Aguardar pelo menos 1 heartbeat.
    await page.waitForTimeout(2_000);

    // Verificar que NENHUM request body contém campos proibidos.
    const violacoes: Array<{ url: string; campo: string }> = [];

    for (const { url, body } of allRequestBodies) {
      for (const campo of CAMPOS_PROIBIDOS) {
        if (campo in body) {
          violacoes.push({ url, campo });
        }
      }
    }

    // Nenhuma violação encontrada.
    expect(violacoes).toStrictEqual([]);

    // Verificar especificamente o corpo do relato (POST /api/v1/reports).
    const relatosEnviados = allRequestBodies.filter(r => r.url.includes('/api/v1/reports'));
    expect(relatosEnviados.length).toBeGreaterThanOrEqual(1);

    for (const { body } of relatosEnviados) {
      // Campos obrigatórios presentes.
      expect(body).toHaveProperty('reportId');
      expect(body).toHaveProperty('pacienteId');
      expect(body).toHaveProperty('commandRef');
      expect(body).toHaveProperty('primitives');
      expect(body).toHaveProperty('confidence');
      expect(body).toHaveProperty('profileVersion');
      expect(body).toHaveProperty('recognizedAt');
      expect(body).toHaveProperty('deviceId');

      // Campos proibidos ausentes.
      for (const campo of CAMPOS_PROIBIDOS) {
        expect(body).not.toHaveProperty(campo);
      }
    }

    // Verificar especificamente o corpo do heartbeat (POST /api/v1/edge/heartbeat).
    const heartbeatsEnviados = allRequestBodies.filter(r => r.url.includes('/api/v1/edge/heartbeat'));
    expect(heartbeatsEnviados.length).toBeGreaterThanOrEqual(1);

    for (const { body } of heartbeatsEnviados) {
      // Corpo deve ter exatamente 3 campos (invariante de privacidade nº 6).
      const campos = Object.keys(body);
      expect(campos).toContain('agentVersion');
      expect(campos).toContain('runningProfileVersion');
      expect(campos).toContain('deviceId');

      // Nenhum campo extra.
      const camposExtras = campos.filter(c => !['agentVersion', 'runningProfileVersion', 'deviceId'].includes(c));
      expect(camposExtras).toStrictEqual([]);
    }
  });

});
