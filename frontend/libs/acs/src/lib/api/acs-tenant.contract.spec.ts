// =============================================================================
// acs-tenant.contract.spec.ts — CTR-01a (FE-E2E-005 / P3)
//
// Verifica que AcsTenantMockAdapter e AcsTenantHttpAdapter satisfazem
// AcsTenantPort com o mesmo shape de dados.
//
// NUANCE 1: ACCESS_HTTP_BASE é InjectionToken — TestBed provê useValue: ''
// para que HttpTestingController receba URLs relativas testáveis.
// Com ACCESS_HTTP_BASE = '': base = '/v1/acs/tenants'
// =============================================================================

import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import {
  provideHttpClient,
} from '@angular/common/http';
import {
  provideHttpClientTesting,
  HttpTestingController,
} from '@angular/common/http/testing';
import { ACCESS_HTTP_BASE } from '@neuroassist/shared/access-http-base';
import { AcsTenantPort, TenantDto, TenantCreateDto } from './acs-tenant.port';
import { AcsTenantHttpAdapter } from './acs-tenant-http.adapter';
import { AcsTenantMockAdapter } from './acs-tenant-mock.adapter';
import { SKIP_EMPRESA_ID_HEADER } from './acs-http.interceptor';

const STUB_TENANT: TenantDto = {
  id: 'tenant-test-001',
  nome: 'Familia Teste',
  tipo: 'familia',
  provisioningStatus: 'active',
};

const CREATE_DTO: TenantCreateDto = {
  nome: 'Nova Clinica',
  tipo: 'clinica',
};

describe.each([
  ['mock' as const, () => new AcsTenantMockAdapter()],
  ['http' as const, () => TestBed.inject(AcsTenantHttpAdapter)],
])('%s satisfaz AcsTenantPort', (label, adapterFactory) => {
  let adapter: AcsTenantPort;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        // NUANCE 1: prover ACCESS_HTTP_BASE para que inject() no adapter funcione
        { provide: ACCESS_HTTP_BASE, useValue: '' },
        AcsTenantHttpAdapter,
        AcsTenantMockAdapter,
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
  // get()
  // ---------------------------------------------------------------------------

  it('get() retorna TenantDto com campos obrigatórios', fakeAsync(() => {
    let result: TenantDto | undefined;
    let error: unknown;

    adapter.get('tenant-familia-amaral').subscribe({
      next: r => { result = r; },
      error: e => { error = e; },
    });

    if (label === 'http') {
      const req = controller.expectOne('/v1/acs/tenants/tenant-familia-amaral');
      expect(req.request.method).toBe('GET');
      req.flush(STUB_TENANT);
    } else {
      tick(1000);
    }

    expect(error).toBeUndefined();
    expect(result).toMatchObject({
      id: expect.any(String),
      nome: expect.any(String),
      tipo: expect.stringMatching(/^(familia|clinica)$/),
      provisioningStatus: expect.stringMatching(/^(active|provisioning|failed)$/),
    });
  }));

  // ---------------------------------------------------------------------------
  // create()
  // ---------------------------------------------------------------------------

  it('create() retorna TenantDto criado', fakeAsync(() => {
    let result: TenantDto | undefined;
    let error: unknown;

    adapter.create(CREATE_DTO).subscribe({
      next: r => { result = r; },
      error: e => { error = e; },
    });

    if (label === 'http') {
      const req = controller.expectOne('/v1/acs/tenants');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toMatchObject({ nome: 'Nova Clinica', tipo: 'clinica' });
      req.flush({ ...STUB_TENANT, id: 'tenant-new', nome: 'Nova Clinica', tipo: 'clinica' });
    } else {
      tick(1000);
    }

    expect(error).toBeUndefined();
    expect(result).toMatchObject({
      id: expect.any(String),
      nome: expect.any(String),
      tipo: expect.any(String),
      provisioningStatus: expect.any(String),
    });
  }));

  // ---------------------------------------------------------------------------
  // retryProvisioning()
  // ---------------------------------------------------------------------------

  it('retryProvisioning() retorna TenantDto atualizado', fakeAsync(() => {
    let result: TenantDto | undefined;
    let error: unknown;

    adapter.retryProvisioning('tenant-familia-amaral').subscribe({
      next: r => { result = r; },
      error: e => { error = e; },
    });

    if (label === 'http') {
      const req = controller.expectOne('/v1/acs/tenants/tenant-familia-amaral/provisioning:retry');
      expect(req.request.method).toBe('POST');
      req.flush({ ...STUB_TENANT, provisioningStatus: 'active' });
    } else {
      tick(1000);
    }

    expect(error).toBeUndefined();
    expect(result).toMatchObject({
      id: expect.any(String),
      provisioningStatus: expect.any(String),
    });
  }));
});
