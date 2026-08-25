import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { HeartbeatService, AGENT_VERSION } from './heartbeat.service';
import { DeviceSessionStore } from './device-session.store';
import { DetectionProfileStore } from './detection-profile.store';

// =============================================================================
// HeartbeatService — unit tests (GEST-FE-031)
//
// O que estes testes protegem:
//   1. Corpo tem exatamente 3 campos (agentVersion, runningProfileVersion, deviceId)
//   2. Valores corretos no corpo (deviceId = deviceStore.deviceId(), etc.)
//   3. profileDrift: true → forceReload() é chamado
//   4. profileDrift: false → forceReload() NÃO é chamado
//   5. Resposta com recommendedHeartbeatSeconds → currentIntervalMs atualizado
//   6. Falha de rede não lança e não interrompe o ciclo
//   7. start() é idempotente — segunda chamada não duplica o timer
//   8. stop() cancela o timer; nenhuma emissão após stop
//
// Mutação confirmada (invariante do teste 1): alterar o número de campos no corpo
// faz o teste "exatamente 3 campos" reprovar — sem mutação, o teste sempre passa.
// =============================================================================

// ---------------------------------------------------------------------------
// Stubs de store
// ---------------------------------------------------------------------------

const DEVICE_ID = 'device-abc-001';
const PROFILE_VERSION = 7;

const mockDeviceStore = {
  deviceId: signal<string | null>(DEVICE_ID).asReadonly(),
  estado: signal('autenticado' as const).asReadonly(),
  autenticado: signal(true).asReadonly(),
  tenantId: signal<string | null>('tenant-001').asReadonly(),
  token: signal<string | null>('tok').asReadonly(),
};

let mockForceReloadFn: jest.Mock;

function makeProfileStore() {
  mockForceReloadFn = jest.fn().mockResolvedValue(undefined);
  return {
    profileVersion: signal<number | null>(PROFILE_VERSION).asReadonly(),
    forceReload: () => mockForceReloadFn() as Promise<void>,
    semVinculo: signal(false).asReadonly(),
    perfilIndisponivel: signal(false).asReadonly(),
    primitivos: signal([]).asReadonly(),
    thresholdsGlobaisMedia: signal({}).asReadonly(),
    loadProfile: jest.fn().mockResolvedValue(undefined),
    carregando: signal(false).asReadonly(),
    degradacao: signal(null).asReadonly(),
    pacienteId: signal<string | null>(null).asReadonly(),
  };
}

// ---------------------------------------------------------------------------
// Constantes das respostas de teste
// ---------------------------------------------------------------------------

const OK_RESPONSE = {
  status: 'ok',
  serverTime: '2026-08-25T10:00:00Z',
  currentProfileVersion: PROFILE_VERSION,
  profileDrift: false,
};

function setup(): {
  service: HeartbeatService;
  httpTesting: HttpTestingController;
} {
  const profileStoreMock = makeProfileStore();

  TestBed.configureTestingModule({
    providers: [
      HeartbeatService,
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: DeviceSessionStore, useValue: mockDeviceStore },
      { provide: DetectionProfileStore, useValue: profileStoreMock },
    ],
  });

  return {
    service: TestBed.inject(HeartbeatService),
    httpTesting: TestBed.inject(HttpTestingController),
  };
}

// ---------------------------------------------------------------------------
// Testes
// ---------------------------------------------------------------------------

describe('HeartbeatService', () => {
  let service: HeartbeatService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    const ctx = setup();
    service = ctx.service;
    httpTesting = ctx.httpTesting;
  });

  afterEach(() => {
    service.stop();
    httpTesting.verify();
  });

  // ── Teste 1: corpo tem exatamente 3 campos ─────────────────────────────────

  it('emite exatamente 3 campos no corpo: agentVersion, runningProfileVersion, deviceId', async () => {
    const emitPromise = service.emit();

    const req = httpTesting.expectOne('/api/v1/edge/heartbeat');
    const body = req.request.body as Record<string, unknown>;

    expect(Object.keys(body).sort()).toEqual(
      ['agentVersion', 'deviceId', 'runningProfileVersion'].sort(),
    );

    req.flush(OK_RESPONSE);
    await emitPromise;
  });

  // ── Teste 2: valores corretos no corpo ────────────────────────────────────

  it('inclui agentVersion correto (0.1.0), deviceId e profileVersion do store', async () => {
    const emitPromise = service.emit();

    const req = httpTesting.expectOne('/api/v1/edge/heartbeat');
    const body = req.request.body as Record<string, unknown>;

    expect(body['agentVersion']).toBe(AGENT_VERSION);
    expect(body['deviceId']).toBe(DEVICE_ID);
    expect(body['runningProfileVersion']).toBe(PROFILE_VERSION);

    req.flush(OK_RESPONSE);
    await emitPromise;
  });

  // ── Teste 3: profileDrift: true → forceReload() ───────────────────────────

  it('chama forceReload() quando a resposta tem profileDrift: true', async () => {
    const emitPromise = service.emit();

    httpTesting.expectOne('/api/v1/edge/heartbeat').flush({
      ...OK_RESPONSE,
      profileDrift: true,
      currentProfileVersion: 8,
    });

    await emitPromise;
    expect(mockForceReloadFn).toHaveBeenCalledTimes(1);
  });

  // ── Teste 4: profileDrift: false → forceReload() NÃO é chamado ───────────

  it('NAO chama forceReload() quando profileDrift é false', async () => {
    const emitPromise = service.emit();

    httpTesting.expectOne('/api/v1/edge/heartbeat').flush({
      ...OK_RESPONSE,
      profileDrift: false,
    });

    await emitPromise;
    expect(mockForceReloadFn).not.toHaveBeenCalled();
  });

  // ── Teste 5: recommendedHeartbeatSeconds atualiza o intervalo ─────────────

  it('atualiza currentIntervalMs com o valor recomendado pelo servidor', async () => {
    const emitPromise = service.emit();

    httpTesting.expectOne('/api/v1/edge/heartbeat').flush({
      ...OK_RESPONSE,
      recommendedHeartbeatSeconds: 60,
    });

    await emitPromise;

    // Verificar via reflexão de teste
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    expect((service as any).currentIntervalMs).toBe(60_000);
  });

  it('nao atualiza o intervalo quando recommendedHeartbeatSeconds esta ausente', async () => {
    const emitPromise = service.emit();
    httpTesting.expectOne('/api/v1/edge/heartbeat').flush(OK_RESPONSE); // sem recommendedHeartbeatSeconds
    await emitPromise;

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    expect((service as any).currentIntervalMs).toBe(30_000); // default mantido
  });

  // ── Teste 6: falha de rede → sem exceção, ciclo continua ─────────────────

  it('nao lanca excecao quando a rede falha', async () => {
    const emitPromise = service.emit();

    httpTesting.expectOne('/api/v1/edge/heartbeat').error(
      new ProgressEvent('error'),
    );

    await expect(emitPromise).resolves.toBeUndefined();
  });

  it('nao chama forceReload() quando a rede falha', async () => {
    const emitPromise = service.emit();
    httpTesting.expectOne('/api/v1/edge/heartbeat').error(new ProgressEvent('error'));
    await emitPromise;

    expect(mockForceReloadFn).not.toHaveBeenCalled();
  });

  // ── Teste 7: start() é idempotente ────────────────────────────────────────

  it('start() idempotente — segunda chamada nao duplica emissoes', fakeAsync(() => {
    service.start();
    service.start(); // segunda chamada deve ser ignorada

    // A chamada imediata de emit() dispara 1 requisição
    httpTesting.expectOne('/api/v1/edge/heartbeat').flush(OK_RESPONSE);

    // Avançar 30 s → deve haver exatamente 1 nova emissão (não 2)
    tick(30_000);
    httpTesting.expectOne('/api/v1/edge/heartbeat').flush(OK_RESPONSE);

    service.stop();
    tick(60_000); // nenhuma emissão após stop
    httpTesting.expectNone('/api/v1/edge/heartbeat');
  }));

  // ── Teste 8: stop() cancela o ciclo ──────────────────────────────────────

  it('stop() cancela o timer — nenhuma emissao apos stop', fakeAsync(() => {
    service.start();

    // Absorver a emissão imediata
    httpTesting.expectOne('/api/v1/edge/heartbeat').flush(OK_RESPONSE);

    service.stop();
    tick(120_000); // 2 minutos sem emissão
    httpTesting.expectNone('/api/v1/edge/heartbeat');
  }));
});
