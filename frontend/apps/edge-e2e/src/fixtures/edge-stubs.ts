/**
 * edge-stubs.ts — Shared Playwright route-intercept stubs for EDGE e2e.
 *
 * These stubs intercept HTTP calls and apply when the Angular app is configured
 * with `provideEdge({ adapter: 'http' })`.
 *
 * In the default development build (`adapter: 'mock'`), the mock adapters return
 * data in-memory (no HTTP calls), so route intercepts have no effect. The mock
 * adapter seed data is documented in `device-mock.adapter.ts` / `pairing-mock.adapter.ts`.
 *
 * The fixtures use a closure-based "flippable" stub pattern so individual tests
 * can control what the next response returns (e.g., initially 'not-paired', then
 * 'paired' after a heartbeat flip).
 *
 * FE-EDGE-021 through FE-EDGE-026 (P9 — E2E + Hardening).
 */
import { Page, Route } from '@playwright/test';

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

export const EDGE_DEVICE_ID = 'edge-test-01';

/**
 * Paciente ativo DA APP EM EXECUÇÃO — não um identificador escolhido pela suíte.
 *
 * <p>Era `'pac-test'`, e por isso todo `page.route('**\/patients/pac-test/...')`
 * jamais casava: quem decide o paciente ativo é o shell
 * (`AppShellComponent` → `DashHomeFacade.patient().patientId`), e o mock do DASH
 * responde `'pac-marcio'`. A app pedia `/patients/pac-marcio/device`, ninguém
 * interceptava, a chamada vazava para o dev-server e a página inteira virava um
 * banner de erro. Um stub endereçado a uma URL que o produto nunca chama não
 * testa nada — só falha de um jeito que parece defeito de tela.
 */
export const EDGE_PATIENT_ID = 'pac-marcio';
export const EDGE_BASE = '/api/v1/edge';

// ---------------------------------------------------------------------------
// Fixture factories
// ---------------------------------------------------------------------------

/** Build a DispositivoEdgeView JSON payload. */
export function makeDevicePayload(
  overrides: Record<string, unknown> = {},
): Record<string, unknown> {
  const now = Date.now();
  return {
    deviceId: EDGE_DEVICE_ID,
    tenantRef: 'tenant-familia-test',
    pacienteId: EDGE_PATIENT_ID,
    pairingState: 'paired',
    status: 'online',
    ultimaSincronizacao: new Date(now - 10_000).toISOString(), // 10s ago → fresh (< 45s staleness)
    versao: '1.4.2',
    runningProfileVersion: 3,
    currentProfileVersion: 3,
    profileDrift: false,
    stalenessThresholdSeconds: 90,
    serverTime: new Date(now).toISOString(),
    ...overrides,
  };
}

/** Build a RegisterDeviceResult JSON payload. */
export function makeRegisterPayload(
  overrides: Record<string, unknown> = {},
): Record<string, unknown> {
  return {
    deviceId: EDGE_DEVICE_ID,
    pairingState: 'registered',
    label: 'Dispositivo E2E Test',
    enrollmentToken: 'E2E-TOKEN-ABCD-1234',
    enrollmentExpiresAt: new Date(Date.now() + 5 * 60_000).toISOString(), // 5 min TTL
    createdAt: new Date().toISOString(),
    ...overrides,
  };
}

/** Build a LivenessView JSON payload. */
export function makeLivenessPayload(
  overrides: Record<string, unknown> = {},
): Record<string, unknown> {
  const now = Date.now();
  return {
    deviceId: EDGE_DEVICE_ID,
    pacienteId: EDGE_PATIENT_ID,
    status: 'online',
    ultimaSincronizacao: new Date(now - 10_000).toISOString(),
    versao: '1.4.2',
    stalenessThresholdSeconds: 90,
    serverTime: new Date(now).toISOString(),
    ...overrides,
  };
}

/**
 * `type` tal como o edge-service REALMENTE emite.
 *
 * <p>O api-contract.md §8 documenta a forma bare (`/errors/device-retired`) e a
 * suíte foi escrita a partir do documento. O código do produtor
 * (`ProblemDetailFactory.TYPE_URI_TEMPLATE`) emite
 * `https://neuroassist-ai.com/errors/edge.device-retired` — URI absoluta e com
 * prefixo `edge.`. As duas exceções são `/errors/optimistic-lock` e
 * `/errors/forbidden`, escritas à mão no `GlobalExceptionHandler`, que saem
 * mesmo na forma bare.
 *
 * <p>Os cenários chamam pela forma bare (legível); aqui traduzimos para a forma
 * do produtor, de modo que o teste exercite o que o backend manda e não o que o
 * frontend gostaria que ele mandasse. Divergência doc × código relatada.
 */
const TIPOS_BARE_NO_PRODUTOR = new Set([
  '/errors/optimistic-lock',
  '/errors/forbidden',
]);

export function tipoComoOProdutorEmite(typeBare: string): string {
  if (TIPOS_BARE_NO_PRODUTOR.has(typeBare)) return typeBare;
  const codigo = typeBare.replace('/errors/', '');
  return `https://neuroassist-ai.com/errors/edge.${codigo}`;
}

/**
 * Build a ProblemDetail JSON payload — na forma do produtor.
 *
 * <p>`correlationId` vai ANINHADO em `error`, como o `ProblemDetailFactory` faz;
 * só os três handlers escritos à mão o põem no topo. A suíte usava a forma do
 * documento (topo), que o serviço não emite para nenhum destes tipos.
 */
export function makeProblemDetail(
  type: string,
  status: number,
  detail: string,
): Record<string, unknown> {
  const tipoReal = tipoComoOProdutorEmite(type);
  const codigo = type.replace('/errors/', '');
  return {
    type: tipoReal,
    status,
    title: codigo,
    detail,
    instance: '/api/v1/edge',
    error: {
      code: `edge.${codigo}`,
      category: 'conflict',
      correlationId: 'e2e-test-correlation-id',
      timestamp: new Date().toISOString(),
      context: {},
    },
  };
}

// ---------------------------------------------------------------------------
// Flippable stub pattern
// Allows a test to control what the next route response returns.
// Usage:
//   const stub = createFlippableStub();
//   await page.route('**/liveness', stub.handler);
//   // ...test first state...
//   stub.flip({ ultimaSincronizacao: new Date().toISOString() });
//   // next request returns updated payload
// ---------------------------------------------------------------------------

export interface FlippableStub {
  /** Playwright route handler — pass directly to page.route(). */
  handler: (route: Route) => Promise<void>;
  /** Update the response body for subsequent requests. */
  flip(overrides: Record<string, unknown>): void;
  /** Total number of requests matched by this stub. */
  callCount(): number;
}

export function createFlippableLivenessStub(
  initial: Record<string, unknown> = {},
): FlippableStub {
  let currentOverrides: Record<string, unknown> = { ...initial };
  let count = 0;

  return {
    handler: async (route) => {
      count++;
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(makeLivenessPayload(currentOverrides)),
      });
    },
    flip(overrides) {
      currentOverrides = { ...overrides };
    },
    callCount() {
      return count;
    },
  };
}

export function createFlippableDeviceStub(
  initial: Record<string, unknown> = {},
): FlippableStub {
  let currentOverrides: Record<string, unknown> = { ...initial };
  let count = 0;

  return {
    handler: async (route) => {
      count++;
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(makeDevicePayload(currentOverrides)),
      });
    },
    flip(overrides) {
      currentOverrides = { ...overrides };
    },
    callCount() {
      return count;
    },
  };
}

// ---------------------------------------------------------------------------
// Simple one-shot stub helpers (for non-flippable routes)
// ---------------------------------------------------------------------------

/** Stub the POST /api/v1/edge/devices endpoint (register device). */
export async function fulfillRegister(
  route: Route,
  overrides: Record<string, unknown> = {},
): Promise<void> {
  await route.fulfill({
    // 201 + Location, como o DeviceRegistrationController devolve.
    status: 201,
    headers: { Location: `${EDGE_BASE}/devices/${EDGE_DEVICE_ID}` },
    contentType: 'application/json',
    body: JSON.stringify(makeRegisterPayload(overrides)),
  });
}

/** Fulfill a route with a ProblemDetail error response. */
export async function fulfillError(
  route: Route,
  type: string,
  status: number,
  detail: string,
): Promise<void> {
  await route.fulfill({
    status,
    contentType: 'application/problem+json',
    body: JSON.stringify(makeProblemDetail(type, status, detail)),
  });
}

/**
 * Script para `page.addInitScript()` que põe a app em modo HTTP.
 *
 * <p>Precisa rodar ANTES de qualquer código da app: `provideEdge()` lê a global
 * uma única vez, no bootstrap. Semear depois do `goto` não tem efeito nenhum, e
 * o sintoma seria a app seguir usando o mock enquanto os `page.route` deste
 * arquivo nunca são chamados — falha que se parece com "o intercept não pegou".
 */
export function scriptAdaptadorHttp(): string {
  return `(() => { window.__EDGE_E2E_ADAPTER = 'http'; })()`;
}

/**
 * Põe a página em modo HTTP e instala os stubs de base.
 *
 * <p>Chamar num `test.beforeEach` de bloco HTTP, ANTES do `goto`.
 *
 * <p>Os dois stubs de base não são conveniência: o shell resolve o dispositivo
 * do paciente ativo (`AppShellComponent` → `startConnectivityForActivePatient`)
 * em TODA rota, inclusive fora do EDGE. Sem interceptá-los, essas duas chamadas
 * vazam para o dev-server, o `DeviceFacade` — que é `providedIn:'root'` e
 * portanto compartilhado com o chip da topbar — registra o erro, e a página de
 * dispositivo troca o conteúdo inteiro pelo banner de erro. O sintoma é uma tela
 * vazia num cenário que não tem nada a ver com o shell.
 *
 * <p>Registrar aqui e não em cada cenário: o Playwright casa rotas na ordem
 * INVERSA de registro, então qualquer `page.route` do próprio teste, registrado
 * depois, continua tendo precedência sobre estes.
 */
export async function aplicarModoHttp(page: Page): Promise<void> {
  await page.addInitScript(scriptAdaptadorHttp());

  await page.route(`**${EDGE_BASE}/patients/*/device`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(makeDevicePayload()),
    });
  });

  await page.route(`**${EDGE_BASE}/patients/*/liveness`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(makeLivenessPayload()),
    });
  });
}
