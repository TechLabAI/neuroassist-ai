/**
 * E2E spec: estacao-guard
 * Cenário: E7 da SPEC-09 §5
 *
 * E7 — Sessão de usuário tentando /estacao → recusada pelo guard
 *   Afirma: o `estacaoDeviceGuard` exige sessão de DISPOSITIVO (token com edge.device),
 *   não de usuário. Uma sessão de cuidadora/coordenadora não dá acesso à rota.
 *
 * Variantes:
 *   7a — Sem sessão alguma → redireciona para /
 *   7b — Sessão de usuário (coordenadora) sem device session → redireciona para /
 *
 * Importante: o guard lê `DeviceSessionStore.autenticado()`, que é `false` quando
 * `_estado ≠ 'autenticado'`. A sessão de usuário (AuthSessionStore) não afeta
 * o DeviceSessionStore — são dois contextos de identidade distintos.
 *
 * Conferência por mutação (a ser executada pelo validator):
 *   Remover o `if (!deviceStore.autenticado()) return router.createUrlTree(['/'])`
 *   do estacaoDeviceGuard → qualquer rota alcança /estacao, teste fica vermelho.
 */

import { test, expect } from '@playwright/test';
import { scriptSemearSessao } from '@neuroassist/shared/auth-login/testing';

test.describe('estacao-guard', () => {

  // ── E7a — Sem sessão alguma ────────────────────────────────────────────────
  test('E7a — sem sessão de dispositivo → redireciona para fora de /estacao', async ({ page }) => {
    // Nenhum addInitScript de sessão de dispositivo.
    // Também não semeamos sessão de usuário para isolar o guard.
    await page.goto('/estacao');

    // O guard redireciona para /. O sessaoRequeridaGuard pode levar para /login.
    // Em qualquer caso, a rota /estacao não é acessível.
    await page.waitForURL((url) => !url.pathname.startsWith('/estacao'), { timeout: 5_000 });

    // Confirmar que o componente da estação NÃO montou.
    await expect(page.locator('na-estacao')).not.toBeVisible({ timeout: 2_000 });
  });

  // ── E7b — Sessão de USUÁRIO (sem device session) ──────────────────────────
  test('E7b — sessão de usuário sem device session → redireciona para fora de /estacao', async ({ page }) => {
    // Semear sessão de usuário (coordenadora) — mas SEM a device session.
    // O AuthSessionStore ficará populado, mas o DeviceSessionStore permanece 'desconhecido'.
    await page.addInitScript(scriptSemearSessao());

    await page.goto('/estacao');

    // O estacaoDeviceGuard verifica DeviceSessionStore.autenticado(), que é false.
    // Redireciona para /.
    await page.waitForURL((url) => !url.pathname.startsWith('/estacao'), { timeout: 5_000 });

    await expect(page.locator('na-estacao')).not.toBeVisible({ timeout: 2_000 });
  });

  // ── E7c — Sessão de dispositivo válida → acesso concedido ─────────────────
  test('E7c (positivo) — com device session → guard concede acesso a /estacao', async ({ page }) => {
    // Semear device session + perfil + heartbeat para o componente montar.
    await page.addInitScript(`(() => {
      try {
        sessionStorage.setItem('__gest.e2e.device-session', JSON.stringify({
          token: 'mock-device-token-e2e',
          deviceId: 'dev-device-e2e-001',
          tenantId: 'tenant-e2e-001',
        }));
      } catch {}
      try { window.__gest_e2e_skip_detector = true; } catch {}
    })()`);

    await page.route('**/api/v1/edge/profile', (route) => {
      void route.fulfill({
        status: 200,
        contentType: 'application/json',
        headers: { ETag: '"etag-001"' },
        body: JSON.stringify({
          version: 1,
          catalogoDegradado: false,
          etag: '"etag-001"',
          thresholds: { baixa: { earClose:0.25,earOpen:0.30,earOtherMax:0.75,blinkMinFrames:3,doubleBlinkWindowMs:2500,briRaiseOn:0.30,briRaiseOff:0.15,briOtherMax:0.20,browMinFrames:7 }, media: { earClose:0.22,earOpen:0.27,earOtherMax:0.75,blinkMinFrames:2,doubleBlinkWindowMs:2000,briRaiseOn:0.25,briRaiseOff:0.12,briOtherMax:0.18,browMinFrames:5 }, alta: { earClose:0.18,earOpen:0.23,earOtherMax:0.75,blinkMinFrames:2,doubleBlinkWindowMs:1800,briRaiseOn:0.20,briRaiseOff:0.10,briOtherMax:0.15,browMinFrames:4 } },
          primitives: [{ tipoGestoId:'11111111-1111-1111-1111-111111111111', nome:'Piscar duas vezes', engineFeatureRef:'blink_double_v1', sensibilidade:'media', origem:'PACIENTE' }],
          pacienteId: 'paciente-e2e-001',
        }),
      });
    });
    await page.route('**/api/v1/edge/heartbeat', (route) => {
      void route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ status:'ok', serverTime: new Date().toISOString(), currentProfileVersion:1, profileDrift:false }) });
    });

    await page.goto('/estacao');

    // O guard concede acesso — o componente monta.
    await expect(page.locator('na-estacao')).toBeVisible({ timeout: 8_000 });
    // URL permanece em /estacao.
    expect(page.url()).toContain('/estacao');
  });

});
