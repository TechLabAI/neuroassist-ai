// =============================================================================
// acs-cuidador.contract.spec.ts — CTR-01b (FE-E2E-005 / P3)
//
// Verifica que AcsCuidadorMockAdapter e AcsCuidadorHttpAdapter satisfazem
// AcsCuidadorPort com o mesmo shape de dados.
//
// NUANCE 1: ACCESS_HTTP_BASE é InjectionToken — provido com useValue: ''
// para URLs relativas. Base resultante: '/v1/acs/caregivers'
// =============================================================================

import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  provideHttpClientTesting,
  HttpTestingController,
} from '@angular/common/http/testing';
import { ACCESS_HTTP_BASE } from '@neuroassist/shared/access-http-base';
import { AcsCuidadorPort, CuidadorDto, CuidadorCreateDto } from './acs-cuidador.port';
import { AcsCuidadorHttpAdapter } from './acs-cuidador-http.adapter';
import { AcsCuidadorMockAdapter } from './acs-cuidador-mock.adapter';

const STUB_CUIDADOR: CuidadorDto = {
  id: 'cuid-test-001',
  tenantId: 'tenant-familia-amaral',
  nome: 'Cuidadora Teste',
  email: 'cuidadora@test.com',
  papel: 'cuidadora',
  status: 'active',
};

const CREATE_DTO: CuidadorCreateDto = {
  nome: 'Nova Cuidadora',
  email: 'nova@test.com',
  papel: 'cuidadora',
};

describe.each([
  ['mock' as const, () => new AcsCuidadorMockAdapter()],
  ['http' as const, () => TestBed.inject(AcsCuidadorHttpAdapter)],
])('%s satisfaz AcsCuidadorPort', (label, adapterFactory) => {
  let adapter: AcsCuidadorPort;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ACCESS_HTTP_BASE, useValue: '' },
        AcsCuidadorHttpAdapter,
        AcsCuidadorMockAdapter,
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

  it('list() retorna CuidadorDto[] com campos obrigatórios', fakeAsync(() => {
    let result: CuidadorDto[] | undefined;
    let error: unknown;

    adapter.list().subscribe({
      next: r => { result = r; },
      error: e => { error = e; },
    });

    if (label === 'http') {
      const req = controller.expectOne('/v1/acs/caregivers');
      expect(req.request.method).toBe('GET');
      req.flush([STUB_CUIDADOR]);
    } else {
      tick(1000);
    }

    expect(error).toBeUndefined();
    expect(Array.isArray(result)).toBe(true);
    if (result!.length > 0) {
      expect(result![0]).toMatchObject({
        id: expect.any(String),
        tenantId: expect.any(String),
        nome: expect.any(String),
        email: expect.any(String),
        papel: expect.stringMatching(/^(cuidadora|coordenador_clinica)$/),
        status: expect.stringMatching(/^(active|inactive)$/),
      });
    }
  }));

  // ---------------------------------------------------------------------------
  // get()
  // ---------------------------------------------------------------------------

  it('get() retorna CuidadorDto pelo id', fakeAsync(() => {
    let result: CuidadorDto | undefined;
    let error: unknown;

    adapter.get('cuid-jamilly').subscribe({
      next: r => { result = r; },
      error: e => { error = e; },
    });

    if (label === 'http') {
      const req = controller.expectOne('/v1/acs/caregivers/cuid-jamilly');
      expect(req.request.method).toBe('GET');
      req.flush(STUB_CUIDADOR);
    } else {
      tick(1000);
    }

    expect(error).toBeUndefined();
    expect(result).toMatchObject({
      id: expect.any(String),
      nome: expect.any(String),
      papel: expect.any(String),
      status: expect.any(String),
    });
  }));

  // ---------------------------------------------------------------------------
  // create()
  // ---------------------------------------------------------------------------

  it('create() retorna CuidadorDto criado', fakeAsync(() => {
    let result: CuidadorDto | undefined;
    let error: unknown;

    adapter.create(CREATE_DTO).subscribe({
      next: r => { result = r; },
      error: e => { error = e; },
    });

    if (label === 'http') {
      const req = controller.expectOne('/v1/acs/caregivers');
      expect(req.request.method).toBe('POST');
      req.flush({ ...STUB_CUIDADOR, nome: 'Nova Cuidadora', email: 'nova@test.com' });
    } else {
      tick(1000);
    }

    expect(error).toBeUndefined();
    expect(result).toMatchObject({
      id: expect.any(String),
      nome: expect.any(String),
      email: expect.any(String),
      papel: expect.any(String),
      status: expect.any(String),
    });
  }));

  // ---------------------------------------------------------------------------
  // deactivate()
  // ---------------------------------------------------------------------------

  it('deactivate() retorna CuidadorDto com status inactive', fakeAsync(() => {
    let result: CuidadorDto | undefined;
    let error: unknown;

    adapter.deactivate('cuid-jamilly').subscribe({
      next: r => { result = r; },
      error: e => { error = e; },
    });

    if (label === 'http') {
      const req = controller.expectOne('/v1/acs/caregivers/cuid-jamilly/deactivation');
      expect(req.request.method).toBe('POST');
      req.flush({ ...STUB_CUIDADOR, status: 'inactive' });
    } else {
      tick(1000);
    }

    expect(error).toBeUndefined();
    expect(result).toMatchObject({
      id: expect.any(String),
      status: expect.any(String),
    });
  }));
});
