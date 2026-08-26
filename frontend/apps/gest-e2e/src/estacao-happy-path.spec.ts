/**
 * E2E spec: estacao-happy-path
 * Cenários: E1 e E2 da SPEC-09 §5
 * Issue: GEST-FE-033
 *
 * E1 — Dispositivo pareado → estação chega a 'vigiando'
 *   Afirma: caminho feliz de inicialização
 *
 * E2 — Gesto reconhecido → POST /api/v1/reports com engineFeatureRef certo
 *   Afirma: a cadeia gesto → relato fecha
 *
 * Estratégia de fixture:
 *   - Sessão de dispositivo semeada via sessionStorage (DeviceSessionStore lê em dev mode).
 *   - Perfil interceptado via page.route (GET /api/v1/edge/profile → 200).
 *   - Heartbeat interceptado via page.route (POST /api/v1/edge/heartbeat → 200).
 *   - Detector desativado via __gest_e2e_skip_detector (isDevMode() guard).
 *   - Gesto simulado via __gest_e2e_trigger_gesture (registrado após vigiando).
 *   - Relatos capturados via page.route (POST /api/v1/reports → 201).
 *
 * Conferência por mutação (a ser executada pelo validator):
 *   E1: remover scriptSemearSessaoDispositivo() → guard redireciona, 'VIGILANDO' nunca aparece
 *   E2: alterar ENGINE_FEATURE_REF_FIXTURE para ref inválida → handleGesture() descarta,
 *       report nunca é chamado
 */

import { test, expect } from '@playwright/test';
import {
  scriptSemearSessaoDispositivo,
  scriptSkipDetector,
  mockPerfilDisponivel,
  mockHeartbeat,
  mockReports,
  gotoEstacao,
  triggerGesto,
  ENGINE_FEATURE_REF_FIXTURE,
  NOME_GESTO_FIXTURE,
  PACIENTE_ID_FIXTURE,
  DEVICE_ID_FIXTURE,
  TIPO_GESTO_ID_FIXTURE,
} from './estacao-e2e-helpers';

test.describe('estacao-happy-path', () => {

  // ── E1 ─────────────────────────────────────────────────────────────────────
  test('E1 — dispositivo pareado → estação chega a vigiando', async ({ page }) => {
    // Todos os addInitScript ANTES do goto — stores leem sessionStorage no construtor.
    await page.addInitScript(scriptSemearSessaoDispositivo());
    await page.addInitScript(scriptSkipDetector());

    await mockPerfilDisponivel(page);
    await mockHeartbeat(page);

    await gotoEstacao(page);

    // Verificar estado 'vigiando': texto "VIGILANDO" ocupa a tela
    await expect(page.locator('gest-estacao').locator('text=VIGILANDO')).toBeVisible({ timeout: 8_000 });

    // Nenhum estado de erro deve estar presente
    await expect(page.locator('text=PARADA')).not.toBeVisible();
    await expect(page.locator('text=SERVIÇO INDISPONÍVEL')).not.toBeVisible();
    await expect(page.locator('text=SEM VÍNCULO')).not.toBeVisible();
    await expect(page.locator('text=CREDENCIAL INVÁLIDA')).not.toBeVisible();
  });

  // ── E2 ─────────────────────────────────────────────────────────────────────
  test('E2 — gesto reconhecido → POST /api/v1/reports com engineFeatureRef certo', async ({ page }) => {
    const capturedBodies: Record<string, unknown>[] = [];

    await page.addInitScript(scriptSemearSessaoDispositivo());
    await page.addInitScript(scriptSkipDetector());

    await mockPerfilDisponivel(page);
    await mockHeartbeat(page);
    await mockReports(page, (body) => capturedBodies.push(body));

    await gotoEstacao(page);

    // Aguardar vigiando
    await expect(page.locator('text=VIGILANDO')).toBeVisible({ timeout: 8_000 });

    // Simular gesto reconhecido
    await triggerGesto(page);

    // Aguardar o rótulo do último comando aparecer
    await expect(page.locator(`text=${NOME_GESTO_FIXTURE}`)).toBeVisible({ timeout: 4_000 });

    // Verificar que o relato foi enviado
    expect(capturedBodies.length).toBeGreaterThanOrEqual(1);
    const relato = capturedBodies[capturedBodies.length - 1];

    // engineFeatureRef correto via commandRef (tipoGestoId do catálogo)
    expect(relato['commandRef']).toBe(TIPO_GESTO_ID_FIXTURE);
    expect(relato['pacienteId']).toBe(PACIENTE_ID_FIXTURE);
    expect(relato['deviceId']).toBe(DEVICE_ID_FIXTURE);

    // Primitives: deve ter o tipoGestoId certo
    const primitives = relato['primitives'] as Array<Record<string, unknown>>;
    expect(Array.isArray(primitives)).toBe(true);
    expect(primitives[0]['tipoGestoId']).toBe(TIPO_GESTO_ID_FIXTURE);

    // I1: nenhum campo de frame/landmark/campo não declarado
    expect(relato).not.toHaveProperty('rawFramePayload');
    expect(relato).not.toHaveProperty('deviceTenant');
  });

});
