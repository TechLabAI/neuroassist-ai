// =============================================================================
// paciente.contract.spec.ts — CTR-06 (FE-E2E-005 / P3)
//
// Verifica que PacienteMockAdapter e PacienteHttpAdapter satisfazem
// PacPacientePort com o mesmo shape de dados.
//
// Base path (normalizado em P2 / ADR-008): /api/v1/pac/pacientes
// =============================================================================

import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient, HttpResponse } from '@angular/common/http';
import {
  provideHttpClientTesting,
  HttpTestingController,
} from '@angular/common/http/testing';
import { PacPacientePort, Paciente, PacienteCreate, Page } from './paciente.port';
import { PacienteHttpAdapter } from './paciente-http.adapter';
import { PacienteMockAdapter } from './paciente-mock.adapter';

const STUB_PACIENTE: Paciente = {
  id: 'pac-test-001',
  tenantId: 'tenant-familia-amaral',
  nome: 'Paciente Teste',
  dispositivo: null,
  catalogoVersao: 'v1',
};

const STUB_PAGE: Page<Paciente> = {
  content: [STUB_PACIENTE],
  totalElements: 1,
  totalPages: 1,
  number: 0,
  size: 20,
};

const CREATE_DTO: PacienteCreate = { nome: 'Novo Paciente' };

describe.each([
  ['mock' as const, () => new PacienteMockAdapter()],
  ['http' as const, () => TestBed.inject(PacienteHttpAdapter)],
])('%s satisfaz PacPacientePort', (label, adapterFactory) => {
  let adapter: PacPacientePort;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        PacienteHttpAdapter,
        PacienteMockAdapter,
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

  it('list() retorna Page<Paciente> com shape correto', fakeAsync(() => {
    let result: Page<Paciente> | undefined;
    let error: unknown;

    adapter.list().subscribe({
      next: r => { result = r; },
      error: e => { error = e; },
    });

    if (label === 'http') {
      const req = controller.expectOne(r =>
        r.url.startsWith('/api/v1/pac/pacientes') && r.method === 'GET',
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
      number: expect.any(Number),
      size: expect.any(Number),
    });
  }));

  it('list() content tem Paciente com shape correto', fakeAsync(() => {
    let result: Page<Paciente> | undefined;

    adapter.list().subscribe(r => { result = r; });

    if (label === 'http') {
      const req = controller.expectOne(r => r.url.startsWith('/api/v1/pac/pacientes'));
      req.flush(STUB_PAGE);
    } else {
      tick(1000);
    }

    if (result!.content.length > 0) {
      expect(result!.content[0]).toMatchObject({
        id: expect.any(String),
        tenantId: expect.any(String),
        nome: expect.any(String),
        // dispositivo: string | null — cannot use expect.any(String) directly
      });
      expect(typeof result!.content[0].dispositivo === 'string' ||
        result!.content[0].dispositivo === null).toBe(true);
    }
  }));

  // ---------------------------------------------------------------------------
  // get()
  // ---------------------------------------------------------------------------

  it('get() retorna Paciente pelo id', fakeAsync(() => {
    let result: Paciente | undefined;
    let error: unknown;

    adapter.get('pac-marcio').subscribe({
      next: r => { result = r; },
      error: e => { error = e; },
    });

    if (label === 'http') {
      const req = controller.expectOne('/api/v1/pac/pacientes/pac-marcio');
      expect(req.request.method).toBe('GET');
      req.flush(STUB_PACIENTE);
    } else {
      tick(1000);
    }

    expect(error).toBeUndefined();
    expect(result).toMatchObject({
      id: expect.any(String),
      tenantId: expect.any(String),
      nome: expect.any(String),
    });
  }));

  // ---------------------------------------------------------------------------
  // create()
  // ---------------------------------------------------------------------------

  it('create() retorna HttpResponse<Paciente> com body', fakeAsync(() => {
    let result: HttpResponse<Paciente> | undefined;
    let error: unknown;

    adapter.create(CREATE_DTO, 'idempotency-key-test').subscribe({
      next: r => { result = r; },
      error: e => { error = e; },
    });

    if (label === 'http') {
      const req = controller.expectOne('/api/v1/pac/pacientes');
      expect(req.request.method).toBe('POST');
      req.flush({ ...STUB_PACIENTE, nome: 'Novo Paciente' }, { status: 201, statusText: 'Created' });
    } else {
      tick(1000);
    }

    expect(error).toBeUndefined();
    expect(result).toBeDefined();
    expect(result!.body).toMatchObject({
      id: expect.any(String),
      nome: expect.any(String),
    });
  }));
});
