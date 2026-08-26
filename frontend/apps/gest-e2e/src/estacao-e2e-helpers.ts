/**
 * Fixtures e helpers para os cenários E2E da estação do paciente (GEST-FE-033).
 *
 * Estes utilitários configuram o ambiente Playwright para simular:
 *   - Sessão de dispositivo autenticada (via sessionStorage seed, lido pelo
 *     DeviceSessionStore em dev mode antes do bootstrap Angular)
 *   - Perfil de detecção (via page.route interceptando GET /api/v1/edge/profile)
 *   - Heartbeat (via page.route interceptando POST /api/v1/edge/heartbeat)
 *   - Relatos (via page.route interceptando POST /api/v1/reports — capturados)
 *   - Motor desativado (via addInitScript + __gest_e2e_skip_detector, dev mode only)
 *
 * REGRA: todos os `addInitScript` devem ser chamados ANTES de `page.goto('/estacao')`.
 * O DeviceSessionStore e outros stores lêm o sessionStorage no construtor Angular —
 * semear depois do goto não tem efeito.
 */

import type { Page, Route, Request } from '@playwright/test';

// ---------------------------------------------------------------------------
// Constantes de fixture
// ---------------------------------------------------------------------------

/** Token de dispositivo fictício — não é JWT válido; basta para o DeviceSessionStore. */
export const DEVICE_TOKEN_FIXTURE = 'mock-device-token-e2e';

/** deviceId derivado do claim `sub` — fixture determinística. */
export const DEVICE_ID_FIXTURE = 'dev-device-e2e-001';

/** tenantId do dispositivo. */
export const TENANT_ID_FIXTURE = 'tenant-e2e-001';

/** pacienteId vinculado ao dispositivo de fixture. */
export const PACIENTE_ID_FIXTURE = 'paciente-e2e-001';

/** engineFeatureRef usado em cenários de gesto — deve existir em ENGINE_FEATURE. */
export const ENGINE_FEATURE_REF_FIXTURE = 'blink_double_v1';

/** Confiança simulada para o gesto de fixture. */
export const CONFIDENCE_FIXTURE = 0.92;

/** Nome legível do gesto de fixture. */
export const NOME_GESTO_FIXTURE = 'Piscar duas vezes';

/** tipoGestoId — UUID do catálogo. */
export const TIPO_GESTO_ID_FIXTURE = '11111111-1111-1111-1111-111111111111';

// ---------------------------------------------------------------------------
// Semente de sessão de dispositivo
// ---------------------------------------------------------------------------

/**
 * Script `addInitScript` que semeia a sessão de dispositivo no sessionStorage.
 *
 * O DeviceSessionStore lê `__gest.e2e.device-session` no construtor em dev mode
 * e arranca com `estado = 'autenticado'`. Isso faz o `estacaoDeviceGuard` conceder
 * acesso à rota `/estacao` sem chamar POST /oauth2/token/device.
 *
 * Deve ser chamado com `await page.addInitScript(scriptSemearSessaoDispositivo())`
 * ANTES do `page.goto('/estacao')`.
 */
export function scriptSemearSessaoDispositivo(opts?: {
  token?: string;
  deviceId?: string;
  tenantId?: string;
}): string {
  const data = {
    token:    opts?.token    ?? DEVICE_TOKEN_FIXTURE,
    deviceId: opts?.deviceId ?? DEVICE_ID_FIXTURE,
    tenantId: opts?.tenantId ?? TENANT_ID_FIXTURE,
  };
  return `(() => {
  try {
    sessionStorage.setItem('__gest.e2e.device-session', ${JSON.stringify(JSON.stringify(data))});
  } catch { /* sessionStorage bloqueado — o guard falhará e isso é informação */ }
})()`;
}

/**
 * Script que ativa o modo de bypass do detector (dev mode only).
 *
 * Quando `__gest_e2e_skip_detector = true`, o EstacaoComponent pula a inicialização
 * do GestureDetector (que exige modelos MediaPipe e câmera real) e vai direto para
 * o estado 'vigiando'. O gancho `__gest_e2e_trigger_gesture` é registrado para
 * permitir simulação de detecção por gesto.
 */
export function scriptSkipDetector(): string {
  return `(() => {
  try { (window).__gest_e2e_skip_detector = true; } catch {}
})()`;
}

// ---------------------------------------------------------------------------
// Roteamento de API (perfil + heartbeat + relatos)
// ---------------------------------------------------------------------------

/** Perfil de detecção com um único gesto disponível (caminho feliz). */
export const PERFIL_FIXTURE = {
  version: 1,
  catalogoDegradado: false,
  etag: '"etag-fixture-001"',
  thresholds: {
    baixa:  { earClose:0.25, earOpen:0.30, earOtherMax:0.75, blinkMinFrames:3,
               doubleBlinkWindowMs:2500, briRaiseOn:0.30, briRaiseOff:0.15, briOtherMax:0.20, browMinFrames:7 },
    media:  { earClose:0.22, earOpen:0.27, earOtherMax:0.75, blinkMinFrames:2,
               doubleBlinkWindowMs:2000, briRaiseOn:0.25, briRaiseOff:0.12, briOtherMax:0.18, browMinFrames:5 },
    alta:   { earClose:0.18, earOpen:0.23, earOtherMax:0.75, blinkMinFrames:2,
               doubleBlinkWindowMs:1800, briRaiseOn:0.20, briRaiseOff:0.10, briOtherMax:0.15, browMinFrames:4 },
  },
  primitives: [
    {
      tipoGestoId: TIPO_GESTO_ID_FIXTURE,
      nome: NOME_GESTO_FIXTURE,
      engineFeatureRef: ENGINE_FEATURE_REF_FIXTURE,
      sensibilidade: 'media',
      origem: 'PACIENTE',
    },
  ],
  pacienteId: PACIENTE_ID_FIXTURE,
};

/** Perfil com todos os primitivos INDISPONIVEL — força estado 'degradada' (E5). */
export const PERFIL_INDISPONIVEL_FIXTURE = {
  ...PERFIL_FIXTURE,
  primitives: [
    {
      tipoGestoId: TIPO_GESTO_ID_FIXTURE,
      nome: NOME_GESTO_FIXTURE,
      engineFeatureRef: ENGINE_FEATURE_REF_FIXTURE,
      sensibilidade: 'media',
      origem: 'INDISPONIVEL',
    },
  ],
};

/**
 * Intercepta GET /api/v1/edge/profile e responde com o perfil de fixture.
 * Chame ANTES do page.goto().
 */
export async function mockPerfilDisponivel(page: Page): Promise<void> {
  await page.route('**/api/v1/edge/profile', (route: Route) => {
    void route.fulfill({
      status: 200,
      contentType: 'application/json',
      headers: { ETag: '"etag-fixture-001"' },
      body: JSON.stringify(PERFIL_FIXTURE),
    });
  });
}

/**
 * Intercepta GET /api/v1/edge/profile e responde com 502 edge.profile-unavailable.
 * Causa estado 'degradada' no EstacaoComponent (E5).
 */
export async function mockPerfilIndisponivel(page: Page): Promise<void> {
  await page.route('**/api/v1/edge/profile', (route: Route) => {
    void route.fulfill({
      status: 502,
      contentType: 'application/json',
      body: JSON.stringify({
        error: { code: 'edge.profile-unavailable', message: 'GEST service unavailable' },
      }),
    });
  });
}

/**
 * Intercepta POST /api/v1/edge/heartbeat e responde com sucesso.
 * Captura os requests para que specs possam assertar o corpo enviado.
 */
export async function mockHeartbeat(
  page: Page,
  onRequest?: (body: Record<string, unknown>) => void,
): Promise<void> {
  await page.route('**/api/v1/edge/heartbeat', async (route: Route, request: Request) => {
    if (onRequest) {
      try {
        const body = await request.postDataJSON() as Record<string, unknown>;
        onRequest(body);
      } catch { /* corpo não disponível */ }
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        status: 'ok',
        serverTime: new Date().toISOString(),
        currentProfileVersion: 1,
        profileDrift: false,
        recommendedHeartbeatSeconds: 30,
      }),
    });
  });
}

/**
 * Intercepta POST /api/v1/reports e responde 201.
 * Captura os requests para assertar corpo (E2, E3, E8).
 */
export async function mockReports(
  page: Page,
  onRequest?: (body: Record<string, unknown>) => void,
): Promise<void> {
  await page.route('**/api/v1/reports', async (route: Route, request: Request) => {
    if (onRequest) {
      try {
        const body = await request.postDataJSON() as Record<string, unknown>;
        onRequest(body);
      } catch { /* corpo não disponível */ }
    }
    await route.fulfill({
      status: 201,
      contentType: 'application/json',
      body: JSON.stringify({ status: 'created' }),
    });
  });
}

// ---------------------------------------------------------------------------
// Navegação para /estacao
// ---------------------------------------------------------------------------

/**
 * Navega para /estacao e aguarda o componente estabilizar no estado esperado.
 *
 * Presume que TODOS os addInitScript e mocks de rota já foram configurados antes
 * desta chamada. O seletor `gest-estacao` é o host element do EstacaoComponent.
 */
export async function gotoEstacao(page: Page): Promise<void> {
  await page.goto('/estacao');
  // Aguardar o host element — prova que o componente montou.
  await page.waitForSelector('gest-estacao', { timeout: 10_000 });
}

/**
 * Aguarda que o estado visível da estação contenha o texto dado.
 * Timeout generoso porque o laço de inicialização é assíncrono.
 */
export async function aguardarEstado(page: Page, texto: string, timeout = 8_000): Promise<void> {
  await page.locator('gest-estacao').locator(`text=${texto}`).waitFor({ timeout });
}

// ---------------------------------------------------------------------------
// Trigger de gesto via gancho E2E
// ---------------------------------------------------------------------------

/**
 * Aciona um gesto via o gancho `window.__gest_e2e_trigger_gesture`.
 *
 * O gancho é registrado pelo EstacaoComponent depois de atingir 'vigiando'
 * (em dev mode via __gest_e2e_skip_detector). Chamar antes de 'vigiando'
 * resulta em no-op (o window.fn não existe ainda).
 */
export async function triggerGesto(
  page: Page,
  engineFeatureRef: string = ENGINE_FEATURE_REF_FIXTURE,
  confidence: number = CONFIDENCE_FIXTURE,
): Promise<void> {
  await page.evaluate(
    ([ref, conf]) => {
      const fn = (window as unknown as Record<string, unknown>)['__gest_e2e_trigger_gesture'];
      if (typeof fn === 'function') {
        (fn as (r: string, c: number) => void)(ref as string, conf as number);
      }
    },
    [engineFeatureRef, confidence] as [string, number],
  );
}
