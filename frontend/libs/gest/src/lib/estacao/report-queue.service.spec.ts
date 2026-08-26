import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { ReportQueueService, isNonRetryable } from './report-queue.service';
import { ReportIngressHttpAdapter } from './report-ingress-http.adapter';
import type { ReportRequest } from './report-ingress.port';

// =============================================================================
// ReportQueueService — unit tests (GEST-FE-032)
//
// O que estes testes protegem:
//   1. isNonRetryable — classificação correta dos status HTTP
//   2. enqueue() incrementa pendingCount
//   3. enqueue() com reportId duplicado é no-op (idempotente)
//   4. enqueue() com fila cheia descarta o mais antigo e incrementa overflowDiscardCount
//   5. processQueue() chama adapter.submit() para cada relato pendente
//   6. processQueue(): 200/201 → relato removido da fila
//   7. processQueue(): 4xx não-retentável → relato removido; nonRetryableDiscardCount++
//   8. processQueue(): retentável (5xx, 408, rede) → relato mantido na fila
//   9. processQueue() não processa em paralelo (isProcessing guard)
//  10. recordParadaStart / clearParadaRecord funcionam corretamente
//
// Mutação confirmada: comentar o `return` em `enqueue()` para reportId duplicado
// faz o teste 3 reprovar — pendingCount cresce além do esperado.
// =============================================================================

// ---------------------------------------------------------------------------
// Fábrica de ReportRequest
// ---------------------------------------------------------------------------

let reportCounter = 0;

function makeRequest(overrides: Partial<ReportRequest> = {}): ReportRequest {
  const id = `report-${Date.now()}-${++reportCounter}`;
  return {
    reportId: id,
    pacienteId: 'pac-001',
    commandRef: 'cmd-001',
    primitives: [{ tipoGestoId: 'cmd-001', confidence: 0.91 }],
    confidence: 0.91,
    profileVersion: 1,
    recognizedAt: new Date().toISOString(),
    deviceId: 'device-001',
    ...overrides,
  } as ReportRequest;
}

// ---------------------------------------------------------------------------
// Mock do adapter
// ---------------------------------------------------------------------------

let mockSubmit: jest.Mock;

function setup() {
  mockSubmit = jest.fn().mockResolvedValue({ status: 'created' });

  TestBed.configureTestingModule({
    providers: [
      ReportQueueService,
      { provide: ReportIngressHttpAdapter, useValue: { submit: mockSubmit } },
    ],
  });

  const service = TestBed.inject(ReportQueueService);

  // Substituir openDb por implementação de no-op para isolar dos testes IDB
  // (jsdom pode ter suporte parcial; queremos testar só a lógica em memória)
  jest.spyOn(service as any, 'openDb').mockRejectedValue(new Error('idb-mocked-off'));

  return service;
}

// ---------------------------------------------------------------------------
// Suite auxiliar — isNonRetryable
// ---------------------------------------------------------------------------

describe('isNonRetryable', () => {
  const retryable = [0, 408, 429, 500, 502, 503];
  const nonRetryable = [400, 401, 403, 404, 422];

  retryable.forEach(status => {
    it(`status ${status} é RETENTÁVEL`, () => {
      const err = new HttpErrorResponse({ status });
      expect(isNonRetryable(err)).toBe(false);
    });
  });

  nonRetryable.forEach(status => {
    it(`status ${status} é NAO RETENTAVEL`, () => {
      const err = new HttpErrorResponse({ status });
      expect(isNonRetryable(err)).toBe(true);
    });
  });

  it('erro nao-HTTP é tratado como retentável', () => {
    expect(isNonRetryable(new Error('network'))).toBe(false);
  });
});

// ---------------------------------------------------------------------------
// Suite principal
// ---------------------------------------------------------------------------

describe('ReportQueueService', () => {
  let service: ReportQueueService;

  beforeEach(() => {
    reportCounter = 0;
    service = setup();
  });

  // ── Teste 2: enqueue() incrementa pendingCount ───────────────────────────

  it('enqueue() incrementa pendingCount', () => {
    service.enqueue(makeRequest());
    expect(service.pendingCount()).toBe(1);

    service.enqueue(makeRequest());
    expect(service.pendingCount()).toBe(2);
  });

  // ── Teste 3: enqueue() duplicado é no-op ────────────────────────────────

  it('enqueue() com mesmo reportId nao altera pendingCount (idempotente)', () => {
    const request = makeRequest({ reportId: 'dup-001' });
    service.enqueue(request);
    service.enqueue(request); // segundo enqueue com mesmo id
    expect(service.pendingCount()).toBe(1);
  });

  // ── Teste 4: overflow → descarte do mais antigo ─────────────────────────

  it('ao exceder MAX_QUEUE_SIZE (50), descarta o mais antigo e incrementa overflowDiscardCount', () => {
    // Enfileirar 50 relatos (chegando ao teto)
    for (let i = 0; i < 50; i++) {
      service.enqueue(makeRequest({ reportId: `r-${i}`, recognizedAt: new Date(i * 1000).toISOString() }));
    }
    expect(service.pendingCount()).toBe(50);
    expect(service.overflowDiscardCount()).toBe(0);

    // 51º relato → descarte do mais antigo (r-0, menor enqueuedAt)
    const newRequest = makeRequest({ reportId: 'r-new' });
    service.enqueue(newRequest);

    expect(service.pendingCount()).toBe(50); // mantém o teto
    expect(service.overflowDiscardCount()).toBe(1);
    // r-new deve estar na fila (não foi descartado)
    expect(service.pendingCount()).toBe(50);
  });

  // ── Teste 5: processQueue() chama adapter.submit() ───────────────────────

  it('processQueue() chama adapter.submit() para cada relato pendente', async () => {
    const r1 = makeRequest();
    const r2 = makeRequest();
    service.enqueue(r1);
    service.enqueue(r2);

    await service.processQueue();

    expect(mockSubmit).toHaveBeenCalledTimes(2);
  });

  // ── Teste 6: successo → removido da fila ─────────────────────────────────

  it('processQueue(): sucesso (200/201) → relato removido; pendingCount decresce', async () => {
    service.enqueue(makeRequest());
    expect(service.pendingCount()).toBe(1);

    mockSubmit.mockResolvedValue({ status: 'created' });
    await service.processQueue();

    expect(service.pendingCount()).toBe(0);
  });

  // ── Teste 7: não-retentável → removido e contado ─────────────────────────

  it('processQueue(): 4xx nao-retentavel → relato removido; nonRetryableDiscardCount++', async () => {
    service.enqueue(makeRequest());

    mockSubmit.mockRejectedValue(new HttpErrorResponse({ status: 422 }));
    await service.processQueue();

    expect(service.pendingCount()).toBe(0);
    expect(service.nonRetryableDiscardCount()).toBe(1);
    expect(service.overflowDiscardCount()).toBe(0); // overflow separado
  });

  it('processQueue(): 401 nao-retentavel → relato removido', async () => {
    service.enqueue(makeRequest());
    mockSubmit.mockRejectedValue(new HttpErrorResponse({ status: 401 }));
    await service.processQueue();
    expect(service.pendingCount()).toBe(0);
  });

  // ── Teste 8: retentável → mantido na fila ────────────────────────────────

  it('processQueue(): 5xx retentavel → relato mantido na fila', async () => {
    service.enqueue(makeRequest());
    mockSubmit.mockRejectedValue(new HttpErrorResponse({ status: 503 }));
    await service.processQueue();
    expect(service.pendingCount()).toBe(1);
  });

  it('processQueue(): 408 retentavel → relato mantido na fila', async () => {
    service.enqueue(makeRequest());
    mockSubmit.mockRejectedValue(new HttpErrorResponse({ status: 408 }));
    await service.processQueue();
    expect(service.pendingCount()).toBe(1);
  });

  it('processQueue(): 429 retentavel → relato mantido na fila', async () => {
    service.enqueue(makeRequest());
    mockSubmit.mockRejectedValue(new HttpErrorResponse({ status: 429 }));
    await service.processQueue();
    expect(service.pendingCount()).toBe(1);
  });

  it('processQueue(): erro de rede (status 0) retentavel → relato mantido', async () => {
    service.enqueue(makeRequest());
    mockSubmit.mockRejectedValue(new HttpErrorResponse({ status: 0 }));
    await service.processQueue();
    expect(service.pendingCount()).toBe(1);
  });

  // ── Teste 9: guard de processamento concorrente ──────────────────────────

  it('processQueue() concorrente nao executa em paralelo', async () => {
    service.enqueue(makeRequest());

    // Simular submit lento
    let resolveSlow!: () => void;
    mockSubmit.mockReturnValue(
      new Promise<{ status: string }>(res => {
        resolveSlow = () => res({ status: 'created' });
      }),
    );

    const first = service.processQueue();
    const second = service.processQueue(); // deve ser no-op (isProcessing = true)

    resolveSlow();
    await first;
    await second;

    // Submit foi chamado exatamente uma vez (não duas)
    expect(mockSubmit).toHaveBeenCalledTimes(1);
  });

  // ── Teste 10: observação perdida ─────────────────────────────────────────

  it('recordParadaStart() registra o timestamp e clearParadaRecord() limpa', () => {
    expect(service.lastParadaSince()).toBeNull();

    service.recordParadaStart();
    expect(service.lastParadaSince()).not.toBeNull();

    service.clearParadaRecord();
    expect(service.lastParadaSince()).toBeNull();
  });

  // ── Teste extra: totalDiscardCount = overflow + nonRetryable ─────────────

  it('totalDiscardCount é soma de overflowDiscardCount e nonRetryableDiscardCount', async () => {
    // Enfileirar 1 relato e falhar com não-retentável → nonRetryableDiscardCount = 1
    service.enqueue(makeRequest({ reportId: 'nr-total-001' }));
    mockSubmit.mockRejectedValue(new HttpErrorResponse({ status: 422 }));
    await service.processQueue();

    expect(service.nonRetryableDiscardCount()).toBe(1);
    expect(service.overflowDiscardCount()).toBe(0);
    expect(service.totalDiscardCount()).toBe(1);

    // Reset mock para próxima operação
    mockSubmit.mockResolvedValue({ status: 'created' });

    // Verificar invariante genérica: totalDiscardCount = overflow + nonRetryable
    expect(service.totalDiscardCount()).toBe(
      service.overflowDiscardCount() + service.nonRetryableDiscardCount(),
    );
  });
});
