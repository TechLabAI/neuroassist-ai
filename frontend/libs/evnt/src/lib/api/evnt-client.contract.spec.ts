// =============================================================================
// evnt-client.contract.spec.ts — CTR-03 (FE-E2E-005 / P3)
//
// Verifica que EvntClientMockAdapter e EvntClientHttpAdapter satisfazem
// EvntClientPort com o mesmo shape de dados.
//
// Base path (normalizado em P2 / ADR-008): /api/v1/evnt/events
//
// Nota: openStream() usa fetch() + ReadableStream (não HttpClient) —
// não é interceptado pelo HttpTestingController. Testamos apenas os
// endpoints HTTP: listActive, get, confirm, ack, query.
// =============================================================================

import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  provideHttpClientTesting,
  HttpTestingController,
} from '@angular/common/http/testing';
import { EvntClientPort, ActiveEventsDto, EventoDto, ConfirmationResponseDto, EventoPageDto } from './evnt-client.port';
import { EvntClientHttpAdapter } from './evnt-client-http.adapter';
import { EvntClientMockAdapter } from './evnt-client-mock.adapter';
import { EVNT_EMPRESA_CONTEXT } from '../access/evnt-access-tokens';

const STUB_ACTIVE: ActiveEventsDto = {
  serverTime: '2026-08-22T10:00:00Z',
  activeEvent: null,
  events: [],
};

const STUB_EVENTO: EventoDto = {
  eventoId: 'evt-test-001',
  pacienteId: 'pac-marcio',
  comandoRef: 'cmd-ajuda',
  commandLabelSnapshot: 'Ajuda',
  urgenciaSnapshot: 'emergencia',
  timingWindow: { startMs: 1000000, endMs: 1000850, durationMs: 850 },
  feedback: 'nao_avaliado',
  recognizedAt: '2026-08-22T10:00:00Z',
};

const STUB_CONFIRMATION: ConfirmationResponseDto = {
  eventoId: 'evt-test-001',
  feedback: 'real',
  confirmadoPor: 'cuid-jamilly',
  confirmadoAt: '2026-08-22T10:01:00Z',
};

const STUB_PAGE: EventoPageDto = {
  content: [STUB_EVENTO],
  totalElements: 1,
  totalPages: 1,
  page: 0,
  size: 20,
};

describe.each([
  ['mock' as const, () => new EvntClientMockAdapter()],
  ['http' as const, () => TestBed.inject(EvntClientHttpAdapter)],
])('%s satisfaz EvntClientPort', (label, adapterFactory) => {
  let adapter: EvntClientPort;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        // EVNT_EMPRESA_CONTEXT: opcional, factory retorna null por padrão
        { provide: EVNT_EMPRESA_CONTEXT, useValue: null },
        EvntClientHttpAdapter,
        EvntClientMockAdapter,
      ],
    });
    controller = TestBed.inject(HttpTestingController);
    adapter = adapterFactory();
  });

  afterEach(() => {
    controller.verify();
    TestBed.resetTestingModule();
  });

  // ---------------------------------------------------------------------------
  // listActive()
  // ---------------------------------------------------------------------------

  it('listActive() retorna ActiveEventsDto com shape correto', fakeAsync(() => {
    let result: ActiveEventsDto | undefined;
    let error: unknown;

    adapter.listActive().subscribe({
      next: r => { result = r; },
      error: e => { error = e; },
    });

    if (label === 'http') {
      const req = controller.expectOne(r =>
        r.url.startsWith('/api/v1/evnt/events/active') && r.method === 'GET',
      );
      req.flush(STUB_ACTIVE);
    } else {
      tick(1000);
    }

    expect(error).toBeUndefined();
    expect(result).toMatchObject({
      serverTime: expect.any(String),
      events: expect.any(Array),
    });
    expect(result!.activeEvent === null || typeof result!.activeEvent === 'object').toBe(true);
  }));

  // ---------------------------------------------------------------------------
  // get()
  // ---------------------------------------------------------------------------

  it('get() retorna EventoDto com shape correto', fakeAsync(() => {
    let result: EventoDto | undefined;
    let error: unknown;

    adapter.get('evt-001').subscribe({
      next: r => { result = r; },
      error: e => { error = e; },
    });

    if (label === 'http') {
      const req = controller.expectOne('/api/v1/evnt/events/evt-001');
      expect(req.request.method).toBe('GET');
      req.flush(STUB_EVENTO);
    } else {
      tick(1000);
    }

    expect(error).toBeUndefined();
    expect(result).toMatchObject({
      eventoId: expect.any(String),
      pacienteId: expect.any(String),
      comandoRef: expect.any(String),
      commandLabelSnapshot: expect.any(String),
      urgenciaSnapshot: expect.stringMatching(/^(emergencia|rotina)$/),
      timingWindow: expect.objectContaining({
        startMs: expect.any(Number),
        endMs: expect.any(Number),
        durationMs: expect.any(Number),
      }),
      feedback: expect.stringMatching(/^(nao_avaliado|real|falso_positivo)$/),
      recognizedAt: expect.any(String),
    });
  }));

  // ---------------------------------------------------------------------------
  // confirm()
  // ---------------------------------------------------------------------------

  it('confirm() retorna ConfirmationResponseDto com shape correto', fakeAsync(() => {
    let result: ConfirmationResponseDto | undefined;
    let error: unknown;

    adapter.confirm('evt-001', 'real').subscribe({
      next: r => { result = r; },
      error: e => { error = e; },
    });

    if (label === 'http') {
      const req = controller.expectOne('/api/v1/evnt/events/evt-001/confirmation');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toMatchObject({ verdict: 'real' });
      req.flush(STUB_CONFIRMATION);
    } else {
      tick(1000);
    }

    expect(error).toBeUndefined();
    expect(result).toMatchObject({
      eventoId: expect.any(String),
      feedback: expect.stringMatching(/^(real|falso_positivo)$/),
      confirmadoPor: expect.any(String),
      confirmadoAt: expect.any(String),
    });
  }));

  // ---------------------------------------------------------------------------
  // query()
  // ---------------------------------------------------------------------------

  it('query() retorna EventoPageDto com shape correto', fakeAsync(() => {
    let result: EventoPageDto | undefined;
    let error: unknown;

    adapter.query({ page: 0, size: 20 }).subscribe({
      next: r => { result = r; },
      error: e => { error = e; },
    });

    if (label === 'http') {
      const req = controller.expectOne(r =>
        r.url === '/api/v1/evnt/events' && r.method === 'GET',
      );
      req.flush(STUB_PAGE);
    } else {
      tick(1000);
    }

    expect(error).toBeUndefined();
    expect(result).toMatchObject({
      content: expect.any(Array),
      totalElements: expect.any(Number),
      totalPages: expect.any(Number),
      page: expect.any(Number),
      size: expect.any(Number),
    });
  }));
});
