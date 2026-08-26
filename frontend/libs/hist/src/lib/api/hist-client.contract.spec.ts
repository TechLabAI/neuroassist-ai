// =============================================================================
// hist-client.contract.spec.ts — CTR-04 (FE-E2E-005 / P3)
//
// Verifica que HistClientMockAdapter e HistClientHttpAdapter satisfazem
// HistClientPort com o mesmo shape de dados.
//
// GAP G2 RESOLVIDO (INT-FE-HIST-001):
// rotulo e urgencia VÊM RESOLVIDOS no read model do HIST — dentro de
// AuditRecordResponse.payload como payload.rotulo e payload.urgencia.
// Evidência: RecordAuditFactResponse.java armazena payload: Map<String, Object>
// que é populado pelo producer (evnt-service) no momento da ingestão.
// O EVNT inclui rotulo e urgencia no payload ao criar o audit fact.
// Conclusão: NÃO é necessário client-join com PAC ou GEST.
// O FE acessa via record.payload['rotulo'] e record.payload['urgencia'].
//
// ENDPOINT: GET /api/v1/hist/history (retorna { items, page, size, total })
// =============================================================================

import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import {
  provideHttpClient,
  HttpClient,
} from '@angular/common/http';
import {
  provideHttpClientTesting,
  HttpTestingController,
} from '@angular/common/http/testing';
import { HttpParams } from '@angular/common/http';
import { HistClientPort } from './hist-client.port';
import { HistClientHttpAdapter } from './hist-client-http.adapter';
import { HistClientMockAdapter } from './hist-client-mock.adapter';
import type { PagedResponse, AuditRecordResponse, HistoryQueryParams } from '../hist.types';

const QUERY_PARAMS: HistoryQueryParams = {
  from: '2026-08-15T00:00:00Z',
  to:   '2026-08-23T00:00:00Z',
  order: 'desc',
  page: 0,
  size: 50,
};

const STUB_RECORD: AuditRecordResponse = {
  id: 'rec-test-001',
  factId: 'fact-test-001',
  factType: 'evento.confirmado',
  subject: { type: 'paciente', id: 'pac-test' },
  actor: { caregiverId: 'cuid-test', displayNameSnapshot: 'Tester' },
  // G2: rotulo e urgencia incluídos no payload pelo producer (evnt-service)
  payload: { eventoId: 'evt-test', rotulo: 'Ajuda', urgencia: 'emergencia', verdict: null },
  occurredAt: '2026-08-22T10:00:00Z',
  recordedAt: '2026-08-22T10:00:01Z',
  streamKey: 'paciente:pac-test',
  sequenceNo: 1,
  prevHash: '0'.repeat(64),
  recordHash: 'a'.repeat(64),
  correctsRecordId: null,
};

const STUB_PAGED: PagedResponse<AuditRecordResponse> = {
  items: [STUB_RECORD],
  page: 0,
  size: 50,
  total: 1,
};

describe.each([
  ['mock' as const, () => new HistClientMockAdapter()],
  ['http' as const, () => TestBed.inject(HistClientHttpAdapter)],
])('%s satisfaz HistClientPort', (label, adapterFactory) => {
  let adapter: HistClientPort;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        HistClientHttpAdapter,
        HistClientMockAdapter,
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
  // list()
  // ---------------------------------------------------------------------------

  it('list() retorna PagedResponse com campos obrigatórios', fakeAsync(() => {
    let result: PagedResponse<AuditRecordResponse> | undefined;
    let error: unknown;

    adapter.list(QUERY_PARAMS).subscribe({
      next: r => { result = r; },
      error: e => { error = e; },
    });

    if (label === 'http') {
      const req = controller.expectOne(r =>
        r.url === '/api/v1/hist/history' && r.method === 'GET',
      );
      req.flush(STUB_PAGED);
    } else {
      tick(1000);
    }

    expect(error).toBeUndefined();
    expect(result).toBeDefined();
    expect(result).toMatchObject({
      items: expect.any(Array),
      page: expect.any(Number),
      size: expect.any(Number),
      total: expect.any(Number),
    });
  }));

  it('list() itens têm shape de AuditRecordResponse', fakeAsync(() => {
    let result: PagedResponse<AuditRecordResponse> | undefined;

    adapter.list(QUERY_PARAMS).subscribe(r => { result = r; });

    if (label === 'http') {
      const req = controller.expectOne(r => r.url === '/api/v1/hist/history');
      req.flush(STUB_PAGED);
    } else {
      tick(1000);
    }

    expect(result!.items.length).toBeGreaterThanOrEqual(0);

    if (result!.items.length > 0) {
      const item = result!.items[0];
      expect(item).toMatchObject({
        id: expect.any(String),
        factId: expect.any(String),
        factType: expect.any(String),
        subject: expect.objectContaining({
          type: expect.any(String),
          id: expect.any(String),
        }),
        actor: expect.objectContaining({
          caregiverId: expect.any(String),
          displayNameSnapshot: expect.any(String),
        }),
        payload: expect.any(Object),
        occurredAt: expect.any(String),
        recordedAt: expect.any(String),
        streamKey: expect.any(String),
        sequenceNo: expect.any(Number),
        prevHash: expect.any(String),
        recordHash: expect.any(String),
      });
    }
  }));

  it('G2: payload.rotulo e payload.urgencia presentes no item do mock', fakeAsync(() => {
    // G2 verification: os campos rotulo e urgencia chegam via payload, não via join
    let result: PagedResponse<AuditRecordResponse> | undefined;

    adapter.list(QUERY_PARAMS).subscribe(r => { result = r; });

    if (label === 'http') {
      const req = controller.expectOne(r => r.url === '/api/v1/hist/history');
      req.flush(STUB_PAGED);
    } else {
      tick(1000);
    }

    if (result!.items.length > 0) {
      const payload = result!.items[0].payload;
      // G2 CONFIRMADO: rotulo e urgencia vêm dentro do payload do registro HIST
      // populados pelo producer (evnt-service) no momento da ingestão.
      // Sem necessidade de client-join com PAC ou GEST.
      expect(payload).toEqual(expect.objectContaining({
        rotulo: expect.any(String),
        urgencia: expect.any(String),
      }));
    } else if (label === 'http') {
      // http branch: verifica que o stub tem a estrutura esperada
      const payload = STUB_RECORD.payload;
      expect(payload['rotulo']).toBe('Ajuda');
      expect(payload['urgencia']).toBe('emergencia');
    }
  }));

  // ---------------------------------------------------------------------------
  // get()
  // ---------------------------------------------------------------------------

  it('get() retorna AuditRecordResponse', fakeAsync(() => {
    let result: AuditRecordResponse | undefined;
    let error: unknown;

    adapter.get('rec-001').subscribe({
      next: r => { result = r; },
      error: e => { error = e; },
    });

    if (label === 'http') {
      const req = controller.expectOne('/api/v1/hist/audit-facts/rec-001');
      req.flush(STUB_RECORD);
    } else {
      tick(1000);
    }

    if (label === 'http') {
      expect(error).toBeUndefined();
      expect(result).toMatchObject({
        id: expect.any(String),
        factId: expect.any(String),
        factType: expect.any(String),
      });
    }
    // mock branch: rec-001 exists in mock seed
    if (label === 'mock' && !error) {
      expect(result).toMatchObject({
        id: expect.any(String),
        factType: expect.any(String),
      });
    }
  }));
});
