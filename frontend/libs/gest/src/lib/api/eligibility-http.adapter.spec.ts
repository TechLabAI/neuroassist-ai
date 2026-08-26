import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  provideHttpClientTesting,
  HttpTestingController,
} from '@angular/common/http/testing';
import { EligibilityHttpAdapter, EMPRESA_CONTEXT_TOKEN } from './eligibility-http.adapter';
import { GEST_SERVICE_BASE_URL } from './gest-service.config';

// ---------------------------------------------------------------------------
// EligibilityHttpAdapter — unit tests (GEST-FE-008 + P1 multi-empresa wiring)
// Critical path: fail-closed on emergencia when backend unreachable.
//
// Path construction after ADR-008 / E2E-014 / FE-E2E-004 (option a):
//   adapter.check() posts to ${GEST_SERVICE_BASE_URL}/eligibility
//   In 'integrated' profile: GEST_SERVICE_BASE_URL = '/api/v1/gest'
//                            → endpoint '/api/v1/gest/eligibility'
//   In tests with a custom baseUrl ('http://test-gest:8084')
//                            → endpoint 'http://test-gest:8084/eligibility'
//
// Multi-empresa: header is sent for traceability when empresa is resolved;
//   NOT sent when id is null (empresa not yet resolved).
//   Isolation is enforced by JWT claim — these tests do NOT assert isolation.
// ---------------------------------------------------------------------------

const BASE_URL = 'http://test-gest:8084';
const ELIGIBILITY_ENDPOINT = `${BASE_URL}/eligibility`;

// Integrated-profile scenario: baseUrl = '/api/v1/gest' (set by app.config.ts).
const INTEGRATED_BASE_URL = '/api/v1/gest';
const INTEGRATED_ELIGIBILITY_ENDPOINT = `${INTEGRATED_BASE_URL}/eligibility`;

describe('EligibilityHttpAdapter', () => {
  let adapter: EligibilityHttpAdapter;
  let httpMock: HttpTestingController;

  function setupWithEmpresaId(id: string | null): void {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        EligibilityHttpAdapter,
        { provide: GEST_SERVICE_BASE_URL, useValue: BASE_URL },
        { provide: EMPRESA_CONTEXT_TOKEN, useValue: { id } },
      ],
    });
    adapter = TestBed.inject(EligibilityHttpAdapter);
    httpMock = TestBed.inject(HttpTestingController);
  }

  afterEach(() => {
    httpMock.verify();
    TestBed.resetTestingModule();
  });

  describe('basic HTTP contract', () => {
    beforeEach(() => setupWithEmpresaId(null));

    it('should POST to ${baseUrl}/eligibility with urgencyLevel in body', async () => {
      const promise = adapter.check('rotina');

      const req = httpMock.expectOne(ELIGIBILITY_ENDPOINT);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ urgencyLevel: 'rotina' });

      req.flush({ eligible: true, missingStates: [] });
      await promise;
    });

    it('should return eligible:true with null reason when backend responds eligible', async () => {
      const promise = adapter.check('rotina');
      const req = httpMock.expectOne(ELIGIBILITY_ENDPOINT);
      req.flush({ eligible: true, missingStates: [] });

      const result = await promise;
      expect(result.eligible).toBe(true);
      expect(result.reason).toBeNull();
      expect(result.unsupportedPrimitiveIds).toEqual([]);
    });

    it('should return eligible:false with reason listing missing states when backend responds ineligible', async () => {
      const promise = adapter.check('emergencia');
      const req = httpMock.expectOne(ELIGIBILITY_ENDPOINT);
      req.flush({ eligible: false, missingStates: ['piscar-lento', 'desvio-olho'] });

      const result = await promise;
      expect(result.eligible).toBe(false);
      expect(result.reason).toContain('piscar-lento');
      expect(result.reason).toContain('desvio-olho');
      expect(result.unsupportedPrimitiveIds).toEqual(['piscar-lento', 'desvio-olho']);
    });

    it('should map unsupportedPrimitiveIds from backend missingStates', async () => {
      const promise = adapter.check('rotina');
      const req = httpMock.expectOne(ELIGIBILITY_ENDPOINT);
      req.flush({ eligible: false, missingStates: ['abc', 'def', 'ghi'] });

      const result = await promise;
      expect(result.unsupportedPrimitiveIds).toHaveLength(3);
      expect(result.unsupportedPrimitiveIds).toContain('abc');
    });
  });

  // -------------------------------------------------------------------------
  // CRÍTICO: fail-closed / fail-open (ADR-005 honest-degraded)
  // -------------------------------------------------------------------------

  describe('fail-closed / fail-open on network error', () => {
    beforeEach(() => setupWithEmpresaId(null));

    it('[CRÍTICO] should return eligible:false (fail-closed) when backend unreachable and urgencia=emergencia', async () => {
      const promise = adapter.check('emergencia');
      const req = httpMock.expectOne(ELIGIBILITY_ENDPOINT);
      req.error(new ProgressEvent('NetworkError'));

      const result = await promise;
      expect(result.eligible).toBe(false);
      expect(result.reason).toContain('indisponível');
      expect(result.unsupportedPrimitiveIds).toEqual([]);
    });

    it('should return eligible:true (fail-open) when backend unreachable and urgencia=rotina', async () => {
      const promise = adapter.check('rotina');
      const req = httpMock.expectOne(ELIGIBILITY_ENDPOINT);
      req.error(new ProgressEvent('NetworkError'));

      const result = await promise;
      expect(result.eligible).toBe(true);
      expect(result.reason).toBeNull();
      expect(result.unsupportedPrimitiveIds).toEqual([]);
    });
  });

  // -------------------------------------------------------------------------
  // P1 multi-empresa wiring (GEST-FE-009 / GEST-INT-002)
  //
  // X-Empresa-Id is sent for traceability in logs/debug when empresa is resolved.
  // Tenant isolation is enforced by the JWT claim (tenant_id) — these tests
  // do NOT assert isolation; they assert header presence/absence.
  // -------------------------------------------------------------------------

  describe('X-Empresa-Id header wiring', () => {
    it('should NOT send X-Empresa-Id when empresaContext.id is null', async () => {
      setupWithEmpresaId(null);
      const promise = adapter.check('rotina');

      const req = httpMock.expectOne(ELIGIBILITY_ENDPOINT);
      expect(req.request.headers.has('X-Empresa-Id')).toBe(false);
      req.flush({ eligible: true, missingStates: [] });
      await promise;
    });

    it('should send X-Empresa-Id when empresaContext.id is resolved', async () => {
      setupWithEmpresaId('emp-abc-123');
      const promise = adapter.check('rotina');

      const req = httpMock.expectOne(ELIGIBILITY_ENDPOINT);
      expect(req.request.headers.get('X-Empresa-Id')).toBe('emp-abc-123');
      req.flush({ eligible: true, missingStates: [] });
      await promise;
    });

    it('should send correct empresa id in header for any urgencia', async () => {
      setupWithEmpresaId('emp-xyz-999');
      const promise = adapter.check('emergencia');

      const req = httpMock.expectOne(ELIGIBILITY_ENDPOINT);
      expect(req.request.headers.get('X-Empresa-Id')).toBe('emp-xyz-999');
      req.flush({ eligible: true, missingStates: [] });
      await promise;
    });
  });
});

// =============================================================================
// P2 FE-E2E-004: integrated-profile base URL override (/api/v1/gest via GWY)
//
// Isolated top-level describe to avoid TestBed re-configuration issues when
// nested inside the base-URL describe above.
//
// Verifies that when GEST_SERVICE_BASE_URL = '/api/v1/gest' (as provided by
// app.config.ts in the 'integrated' Nx configuration), the adapter posts to
// /api/v1/gest/eligibility — the GWY-normalized path (ADR-008 / E2E-014).
// =============================================================================

describe('EligibilityHttpAdapter (integrated profile — GEST_SERVICE_BASE_URL = /api/v1/gest)', () => {
  let adapter: EligibilityHttpAdapter;
  let httpMock: HttpTestingController;

  const INTEGRATED_BASE_URL = '/api/v1/gest';
  const INTEGRATED_ENDPOINT = `${INTEGRATED_BASE_URL}/eligibility`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        EligibilityHttpAdapter,
        { provide: GEST_SERVICE_BASE_URL, useValue: INTEGRATED_BASE_URL },
      ],
    });
    adapter = TestBed.inject(EligibilityHttpAdapter);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    TestBed.resetTestingModule();
  });

  it('should POST to /api/v1/gest/eligibility (GWY route, ADR-008 / E2E-014)', async () => {
    const promise = adapter.check('rotina');

    const req = httpMock.expectOne(INTEGRATED_ENDPOINT);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ urgencyLevel: 'rotina' });

    req.flush({ eligible: true, missingStates: [] });
    const result = await promise;
    expect(result.eligible).toBe(true);
    expect(result.reason).toBeNull();
  });

  it('[CRÍTICO] should fail-closed on emergencia via GWY path', async () => {
    const promise = adapter.check('emergencia');
    const req = httpMock.expectOne(INTEGRATED_ENDPOINT);
    req.error(new ProgressEvent('NetworkError'));

    const result = await promise;
    expect(result.eligible).toBe(false);
    expect(result.reason).toContain('indisponível');
    expect(result.unsupportedPrimitiveIds).toEqual([]);
  });
});
