import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { EstacaoComponent } from './estacao.component';
import { DeviceSessionStore } from './device-session.store';
import { DetectionProfileStore } from './detection-profile.store';
import { DetectionProfileHttpAdapter } from './detection-profile-http.adapter';
import { ReportIngressHttpAdapter } from './report-ingress-http.adapter';
import { DeviceAuthHttpAdapter } from './device-auth-http.adapter';
import { HeartbeatService } from './heartbeat.service';
import { WakeLockService } from './wake-lock.service';
import { ReportQueueService } from './report-queue.service';
import type { PrimitivoResolvido } from './detection-profile.store';
import type { EngineFeatureRef } from '@neuroassist/shared/contracts';
import type { ReportRequest } from './report-ingress.port';

// =============================================================================
// EstacaoComponent — unit tests (GEST-FE-030)
//
// O que estes testes protegem:
//   1. computeReportId: mesmo reconhecimento → mesmo id (reenvio seguro)
//   2. computeReportId: reconhecimentos diferentes → ids diferentes (pedido repetido conta)
//   3. handleGesture: gesto sem primitivo no perfil → descartado silenciosamente
//   4. handleGesture: gesto com primitivo → relatório submetido com campos corretos
//   5. handleGesture: rawFramePayload e deviceTenant ausentes do relatório
//   6. handleGesture: pedido repetido gera DOIS relatos — nunca engolido
//   7. inicializarLoop: estado 'sem-vinculo' quando semVinculo = true
//   8. inicializarLoop: estado 'degradada' quando perfilIndisponivel = true
//
// Abordagem: sobrescrever as dependências via TestBed.overrideProvider ANTES da
// criação do componente. As stores e o adapter ficam totalmente substituídos
// por stubs, evitando o chain de injeção real (WASM, HTTP, câmera).
//
// Mutação confirmada: fazer computeReportId ignorar o instante do reconhecimento
// derruba "reconhecimentos DIFERENTES do mesmo gesto geram ids diferentes".
// =============================================================================

const THRESHOLDS_MEDIA = {
  earClose: 0.22, earOpen: 0.27, earOtherMax: 0.30,
  blinkMinFrames: 2, doubleBlinkWindowMs: 2000,
  briRaiseOn: 0.30, briRaiseOff: 0.22, briOtherMax: 0.25, browMinFrames: 5,
};

const PRIMITIVO_PISCAR: PrimitivoResolvido = {
  tipoGestoId: 'd0000001-0000-0000-0000-000000000001',
  nome: 'Piscar duplo',
  engineFeatureRef: 'blink_double_v1' as EngineFeatureRef,
  thresholds: THRESHOLDS_MEDIA,
};

// ---------------------------------------------------------------------------
// Stubs de store para override
// ---------------------------------------------------------------------------

const mockDeviceStore = {
  autenticado: signal(true).asReadonly(),
  estado: signal('autenticado').asReadonly(),
  deviceId: signal('dev-uuid-001').asReadonly(),
  tenantId: signal('tenant-001').asReadonly(),
};

const mockPrimitivos = signal<readonly PrimitivoResolvido[]>([]);
const mockSemVinculo = signal(false);
const mockPerfilIndisponivel = signal(false);
const mockPacienteId = signal<string | null>('pac-uuid-001');
const mockProfileVersion = signal<number | null>(7);
let mockLoadProfile = async () => Promise.resolve();

const mockProfileStore = {
  semVinculo: mockSemVinculo.asReadonly(),
  perfilIndisponivel: mockPerfilIndisponivel.asReadonly(),
  pacienteId: mockPacienteId.asReadonly(),
  profileVersion: mockProfileVersion.asReadonly(),
  primitivos: mockPrimitivos.asReadonly(),
  thresholdsGlobaisMedia: signal(THRESHOLDS_MEDIA).asReadonly(),
  loadProfile: () => mockLoadProfile(),
};

let mockSubmitFn: (req: Record<string, unknown>) => Promise<{ status: string }> =
  async () => ({ status: 'created' });

const mockReportAdapter = {
  submit: (req: unknown) => mockSubmitFn(req as Record<string, unknown>),
};

// ---------------------------------------------------------------------------
// Stubs P5 — HeartbeatService, WakeLockService, ReportQueueService
// ---------------------------------------------------------------------------

const mockHeartbeatService = {
  start: jest.fn(),
  stop: jest.fn(),
  emit: jest.fn().mockResolvedValue(undefined),
};

const mockWakeLockService = {
  acquire: jest.fn().mockResolvedValue(undefined),
  release: jest.fn(),
  reacquire: jest.fn().mockResolvedValue(undefined),
  ativo: signal(false).asReadonly(),
  apiIndisponivel: signal(false).asReadonly(),
  isSupported: true,
};

let mockEnqueuedReports: ReportRequest[] = [];
const mockReportQueueService = {
  enqueue: jest.fn((req: ReportRequest) => { mockEnqueuedReports.push(req); }),
  processQueue: jest.fn().mockResolvedValue(undefined),
  recordParadaStart: jest.fn(),
  clearParadaRecord: jest.fn(),
  pendingCount: signal(0).asReadonly(),
  overflowDiscardCount: signal(0).asReadonly(),
  nonRetryableDiscardCount: signal(0).asReadonly(),
  totalDiscardCount: signal(0).asReadonly(),
  lastParadaSince: signal<string | null>(null).asReadonly(),
};

function configureTestBed(): void {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      // Adapters reais mas substituídos via overrideProvider para evitar chains HTTP
      DeviceAuthHttpAdapter,
      DetectionProfileHttpAdapter,
      ReportIngressHttpAdapter,
    ],
  });

  // Sobrescrever as stores e o adapter com os stubs ANTES da criação do componente.
  // Isso garante que inject() resolva para os stubs e não para as instâncias reais.
  TestBed.overrideProvider(DeviceSessionStore, { useValue: mockDeviceStore });
  TestBed.overrideProvider(DetectionProfileStore, { useValue: mockProfileStore });
  TestBed.overrideProvider(ReportIngressHttpAdapter, { useValue: mockReportAdapter });

  // P5: sobrescrever os serviços de heartbeat, wake lock e fila offline
  TestBed.overrideProvider(HeartbeatService, { useValue: mockHeartbeatService });
  TestBed.overrideProvider(WakeLockService, { useValue: mockWakeLockService });
  TestBed.overrideProvider(ReportQueueService, { useValue: mockReportQueueService });
}

function createComponent(): EstacaoComponent {
  return TestBed.runInInjectionContext(() => new EstacaoComponent());
}

describe('EstacaoComponent', () => {
  beforeEach(() => {
    // Reset dos stubs para cada teste
    mockPrimitivos.set([]);
    mockSemVinculo.set(false);
    mockPerfilIndisponivel.set(false);
    mockPacienteId.set('pac-uuid-001');
    mockProfileVersion.set(7);
    mockLoadProfile = async () => Promise.resolve();
    mockSubmitFn = async () => ({ status: 'created' });

    // Reset P5 mocks
    mockEnqueuedReports = [];
    mockHeartbeatService.start.mockClear();
    mockHeartbeatService.stop.mockClear();
    mockWakeLockService.acquire.mockClear();
    mockWakeLockService.release.mockClear();
    mockWakeLockService.reacquire.mockClear();
    mockReportQueueService.enqueue.mockClear();
    mockReportQueueService.processQueue.mockClear();
    mockReportQueueService.recordParadaStart.mockClear();
    mockReportQueueService.clearParadaRecord.mockClear();

    configureTestBed();
  });

  // ---------------------------------------------------------------------------
  // computeReportId — estabilidade e diferenciação
  // ---------------------------------------------------------------------------

  describe('computeReportId', () => {
    it('o MESMO reconhecimento gera o mesmo id — é o que torna o reenvio seguro', () => {
      // Idempotência existe para a fila da P5: reenviar o mesmo reconhecimento não
      // pode virar um segundo alarme. O backend responde 200 em vez de 201.
      const comp = createComponent();
      const t = '2026-08-25T20:00:00.000Z';
      expect(comp.computeReportId('dev-1', 'blink_double_v1', t)).toBe(
        comp.computeReportId('dev-1', 'blink_double_v1', t),
      );
    });

    it('reconhecimentos DIFERENTES do mesmo gesto geram ids diferentes', () => {
      // Este é o teste que impede a volta do defeito: o Márcio pedir ajuda, ninguém
      // vir, e ele pedir de novo. A segunda vez tem de virar um segundo alarme —
      // repetir o gesto é o sinal mais forte de que o primeiro não foi atendido.
      const comp = createComponent();
      const id1 = comp.computeReportId('dev-1', 'blink_double_v1', '2026-08-25T20:00:00.000Z');
      const id2 = comp.computeReportId('dev-1', 'blink_double_v1', '2026-08-25T20:00:10.000Z');
      expect(id1).not.toBe(id2);
    });

    it('deve gerar ids diferentes para engineFeatureRefs diferentes', () => {
      const comp = createComponent();
      const t = '2026-08-25T20:00:00.000Z';
      const id1 = comp.computeReportId('dev-1', 'blink_double_v1', t);
      const id2 = comp.computeReportId('dev-1', 'brow_raise_v1', t);
      expect(id1).not.toBe(id2);
    });
  });

  // ---------------------------------------------------------------------------
  // handleGesture — gesto sem primitivo no perfil
  // ---------------------------------------------------------------------------

  describe('handleGesture — primitivo não encontrado', () => {
    it('deve descartar silenciosamente gesto não presente no perfil', async () => {
      let submitCalled = false;
      mockPrimitivos.set([]); // perfil vazio
      mockSubmitFn = async () => { submitCalled = true; return { status: 'created' }; };

      const comp = createComponent();
      await comp.handleGesture('blink_double_v1' as EngineFeatureRef, 0.9);

      expect(submitCalled).toBe(false);
    });
  });

  // ---------------------------------------------------------------------------
  // handleGesture — submissão correta
  // ---------------------------------------------------------------------------

  describe('handleGesture — submissão correta', () => {
    it('deve submeter relatório com os campos obrigatórios corretos', async () => {
      let submittedBody: Record<string, unknown> | null = null;
      mockPrimitivos.set([PRIMITIVO_PISCAR]);
      mockSubmitFn = async (req) => { submittedBody = req; return { status: 'created' }; };

      const comp = createComponent();
      await comp.handleGesture('blink_double_v1' as EngineFeatureRef, 0.91);

      expect(submittedBody).not.toBeNull();
      expect(submittedBody!['pacienteId']).toBe('pac-uuid-001');
      expect(submittedBody!['deviceId']).toBe('dev-uuid-001');
      expect(submittedBody!['commandRef']).toBe('d0000001-0000-0000-0000-000000000001');
      expect(submittedBody!['confidence']).toBe(0.91);
      expect(submittedBody!['profileVersion']).toBe(7);
      expect(typeof submittedBody!['reportId']).toBe('string');
    });

    it('não deve incluir rawFramePayload no relatório', async () => {
      let submittedBody: Record<string, unknown> | null = null;
      mockPrimitivos.set([PRIMITIVO_PISCAR]);
      mockSubmitFn = async (req) => { submittedBody = req; return { status: 'created' }; };

      const comp = createComponent();
      await comp.handleGesture('blink_double_v1' as EngineFeatureRef, 0.91);

      expect(submittedBody!['rawFramePayload']).toBeUndefined();
    });

    it('não deve incluir deviceTenant no relatório', async () => {
      let submittedBody: Record<string, unknown> | null = null;
      mockPrimitivos.set([PRIMITIVO_PISCAR]);
      mockSubmitFn = async (req) => { submittedBody = req; return { status: 'created' }; };

      const comp = createComponent();
      await comp.handleGesture('blink_double_v1' as EngineFeatureRef, 0.91);

      expect(submittedBody!['deviceTenant']).toBeUndefined();
    });
  });

  // ---------------------------------------------------------------------------
  // handleGesture — idempotência de cooldown
  // ---------------------------------------------------------------------------

  describe('handleGesture — cooldown e idempotência', () => {
    it('PEDIDO REPETIDO NÃO É ENGOLIDO — dois reconhecimentos, dois relatos', async () => {
      // A primeira versão suprimia o segundo gesto dentro de uma janela de 30 s.
      // Num canal de pedido de ajuda isso é o avesso do certo: o detector já tem
      // cooldown próprio de 4 s, então cada chamada aqui É um reconhecimento
      // distinto — e alguém repetir o gesto quer dizer que ninguém veio na
      // primeira vez.
      const enviados: string[] = [];
      mockPrimitivos.set([PRIMITIVO_PISCAR]);
      mockSubmitFn = async (req: { reportId: string }) => {
        enviados.push(req.reportId);
        return { status: 'created' };
      };

      const comp = createComponent();
      await comp.handleGesture('blink_double_v1' as EngineFeatureRef, 0.91);
      await comp.handleGesture('blink_double_v1' as EngineFeatureRef, 0.93);

      expect(enviados).toHaveLength(2);
    });

    it('reenvio do mesmo reconhecimento repete o reportId (a fila da P5 depende disto)', () => {
      const comp = createComponent();
      const t = '2026-08-25T20:00:00.000Z';
      expect(comp.computeReportId('dev-uuid-001', 'blink_double_v1', t)).toBe(
        comp.computeReportId('dev-uuid-001', 'blink_double_v1', t),
      );
    });
  });

  // ---------------------------------------------------------------------------
  // inicializarLoop — estados de erro de perfil
  // ---------------------------------------------------------------------------

  describe('estados de erro de perfil', () => {
    it('deve definir estado sem-vinculo quando profileStore.semVinculo() é true', async () => {
      mockSemVinculo.set(true);
      const comp = createComponent();

      await comp.inicializarLoop();

      expect(comp.estado()).toBe('sem-vinculo');
    });

    it('deve definir estado degradada quando profileStore.perfilIndisponivel() é true', async () => {
      mockPerfilIndisponivel.set(true);
      const comp = createComponent();

      await comp.inicializarLoop();

      expect(comp.estado()).toBe('degradada');
    });
  });

  // ---------------------------------------------------------------------------
  // P5 — heartbeat, wake lock, fila offline
  // ---------------------------------------------------------------------------

  describe('P5 — HeartbeatService', () => {
    it('heartbeatService.start() é chamado em ngOnInit (quando sessão não é terminal)', async () => {
      // Usar semVinculo = true para que inicializarLoop retorne antes de tentar criar
      // GestureDetector + câmera (que dependem de hardware real e travariam o teste).
      mockSemVinculo.set(true);
      const comp = createComponent();
      await comp.ngOnInit();
      expect(mockHeartbeatService.start).toHaveBeenCalledTimes(1);
    });

    it('heartbeatService.stop() é chamado em ngOnDestroy', () => {
      // stop() é síncrono e não depende de ngOnInit — testar diretamente.
      const comp = createComponent();
      comp.ngOnDestroy();
      expect(mockHeartbeatService.stop).toHaveBeenCalledTimes(1);
    });

    it('heartbeat NAO é iniciado quando sessão é credencial-invalida', async () => {
      // Fornecer um store com estado terminal (credencial-invalida) antes de criar o componente.
      // ngOnInit retorna cedo sem chamar heartbeatService.start().
      const terminalStore = {
        ...mockDeviceStore,
        estado: () => 'credencial-invalida' as const,
      };
      TestBed.overrideProvider(DeviceSessionStore, { useValue: terminalStore });

      const comp = createComponent();
      await comp.ngOnInit();
      expect(mockHeartbeatService.start).not.toHaveBeenCalled();
    });
  });

  describe('P5 — WakeLockService', () => {
    it('wakeLock.release() é chamado em ngOnDestroy', async () => {
      const comp = createComponent();
      comp.ngOnDestroy();
      expect(mockWakeLockService.release).toHaveBeenCalledTimes(1);
    });
  });

  describe('P5 — fila offline (handleGesture)', () => {
    it('falha de rede → relato enfileirado via reportQueue.enqueue()', async () => {
      mockPrimitivos.set([PRIMITIVO_PISCAR]);
      mockSubmitFn = async () => { throw new Error('network error'); };

      const comp = createComponent();
      await comp.handleGesture('blink_double_v1' as EngineFeatureRef, 0.91);

      expect(mockReportQueueService.enqueue).toHaveBeenCalledTimes(1);
    });

    it('sucesso → reportQueue.enqueue() NAO é chamado', async () => {
      mockPrimitivos.set([PRIMITIVO_PISCAR]);
      mockSubmitFn = async () => ({ status: 'created' });

      const comp = createComponent();
      await comp.handleGesture('blink_double_v1' as EngineFeatureRef, 0.91);

      expect(mockReportQueueService.enqueue).not.toHaveBeenCalled();
    });
  });
  // ---------------------------------------------------------------------------
  // I3 — o heartbeat continua batendo quando a estação para
  // ---------------------------------------------------------------------------

  describe('[I3] heartbeat NÃO para quando a aba oculta', () => {
    // POR QUE ESTE TESTE EXISTE
    //
    // A invariante estava afirmada em três comentários do código e verificada por
    // NENHUM. É o mesmo padrão que já mordeu este repositório: o detector tinha um
    // comentário dizendo "estes ids devem casar com o catálogo do PAC", e eles não
    // casavam com nada — o comentário afirmava, o código não conferia.
    //
    // O risco aqui é concreto e tem cara de melhoria: alguém acrescenta
    // `if (estado === 'parada') return;` para "poupar requisição". A partir daí uma
    // estação parada fica indistinguível de uma estação MORTA para o servidor — que
    // é exatamente o que o heartbeat existe para diferenciar, e o pior modo de falha
    // deste produto segundo a ADR-007.

    function ocultarAba(): void {
      Object.defineProperty(document, 'hidden', { value: true, configurable: true });
      document.dispatchEvent(new Event('visibilitychange'));
    }

    afterEach(() => {
      Object.defineProperty(document, 'hidden', { value: false, configurable: true });
    });

    it('ocultar a aba leva a "parada" e NÃO chama heartbeatService.stop()', () => {
      const comp = createComponent();
      // ngOnInit NÃO é aguardado de propósito: o registro do listener e o
      // `heartbeatService.start()` acontecem de forma SÍNCRONA, antes do
      // `await inicializarLoop()`. E esse loop não resolve em jsdom — ele espera
      // câmera e o WASM do MediaPipe, que não existem aqui.
      //
      // Aguardar travaria o teste em 5 s de timeout sem medir nada. O alvo aqui é a
      // fiação, não o laço.
      void comp.ngOnInit();
      mockHeartbeatService.stop.mockClear();

      ocultarAba();

      expect(comp.estado()).toBe('parada');
      expect(mockHeartbeatService.stop).not.toHaveBeenCalled();

      comp.ngOnDestroy(); // remove o listener — senão vaza para os testes seguintes
    });

    it('o heartbeat começa no ngOnInit e não recebe o estado da estação', () => {
      // Independência por construção: se um dia `start()` passar a receber estado,
      // esta asserção cai e obriga a uma decisão consciente em vez de uma mudança
      // silenciosa.
      const comp = createComponent();
      void comp.ngOnInit(); // ver a nota acima sobre não aguardar

      expect(mockHeartbeatService.start).toHaveBeenCalledWith();

      comp.ngOnDestroy();
    });
  });
});
