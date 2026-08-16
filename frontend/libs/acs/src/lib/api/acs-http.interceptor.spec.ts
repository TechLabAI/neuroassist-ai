import { TestBed } from '@angular/core/testing';
import {
  HttpClient,
  HttpContext,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { acsHttpInterceptorFn, SKIP_EMPRESA_ID_HEADER } from './acs-http.interceptor';
import { AcsEmpresaContextService } from './acs-empresa-context.service';

// =============================================================================
// acs-http.interceptor.spec.ts — INT-FE-ACS-001
//
// Verifies that acsHttpInterceptorFn:
//   1. Adds X-Empresa-Id when empresa context is set
//   2. Omits X-Empresa-Id when empresa context is null (pre-provisioning)
//   3. Omits X-Empresa-Id when SKIP_EMPRESA_ID_HEADER context token is true
//      (tenant provisioning routes: POST /tenants, POST /tenants/:id/provisioning:retry)
// =============================================================================

describe('acsHttpInterceptorFn', () => {
  let http: HttpClient;
  let controller: HttpTestingController;
  let empresaContextService: AcsEmpresaContextService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([acsHttpInterceptorFn])),
        provideHttpClientTesting(),
        AcsEmpresaContextService,
      ],
    });

    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
    empresaContextService = TestBed.inject(AcsEmpresaContextService);
  });

  afterEach(() => {
    controller.verify();
  });

  it('should add X-Empresa-Id header when empresa context is set', () => {
    empresaContextService.setActiveEmpresaId('tenant-abc-123');

    http.get('/api/v1/acs/caregivers').subscribe();

    const req = controller.expectOne('/api/v1/acs/caregivers');
    expect(req.request.headers.get('X-Empresa-Id')).toBe('tenant-abc-123');
    req.flush([]);
  });

  it('should NOT add X-Empresa-Id header when empresa context is null (pre-provisioning)', () => {
    // No setActiveEmpresaId called — empresaId() starts as null

    http.get('/api/v1/acs/caregivers').subscribe();

    const req = controller.expectOne('/api/v1/acs/caregivers');
    expect(req.request.headers.has('X-Empresa-Id')).toBeFalsy();
    req.flush([]);
  });

  it('should NOT add X-Empresa-Id header when SKIP_EMPRESA_ID_HEADER context token is true', () => {
    empresaContextService.setActiveEmpresaId('tenant-abc-123');

    const ctx = new HttpContext().set(SKIP_EMPRESA_ID_HEADER, true);
    http.post('/api/v1/acs/tenants', {}, { context: ctx }).subscribe();

    const req = controller.expectOne('/api/v1/acs/tenants');
    expect(req.request.headers.has('X-Empresa-Id')).toBeFalsy();
    req.flush({});
  });

  it('should add X-Empresa-Id to caregiver requests when empresa is set', () => {
    empresaContextService.setActiveEmpresaId('empresa-xyz');

    http.post('/api/v1/acs/caregivers', { nome: 'Jamilly', email: 'j@test.com' }).subscribe();

    const req = controller.expectOne('/api/v1/acs/caregivers');
    expect(req.request.headers.get('X-Empresa-Id')).toBe('empresa-xyz');
    req.flush({});
  });

  it('should add X-Empresa-Id to link requests when empresa is set', () => {
    empresaContextService.setActiveEmpresaId('empresa-xyz');

    http.post('/api/v1/acs/caregivers/c1/links', { pacienteId: 'p1' }).subscribe();

    const req = controller.expectOne('/api/v1/acs/caregivers/c1/links');
    expect(req.request.headers.get('X-Empresa-Id')).toBe('empresa-xyz');
    req.flush({});
  });

  it('should NOT add X-Empresa-Id to tenant retry-provisioning (excluded by context token)', () => {
    empresaContextService.setActiveEmpresaId('tenant-abc-123');

    const ctx = new HttpContext().set(SKIP_EMPRESA_ID_HEADER, true);
    http.post('/api/v1/acs/tenants/t1/provisioning:retry', {}, { context: ctx }).subscribe();

    const req = controller.expectOne('/api/v1/acs/tenants/t1/provisioning:retry');
    expect(req.request.headers.has('X-Empresa-Id')).toBeFalsy();
    req.flush({});
  });
});
