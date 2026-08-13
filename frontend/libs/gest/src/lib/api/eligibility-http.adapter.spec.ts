import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  provideHttpClientTesting,
  HttpTestingController,
} from '@angular/common/http/testing';
import { EligibilityHttpAdapter } from './eligibility-http.adapter';
import { GEST_SERVICE_BASE_URL } from './gest-service.config';

// ---------------------------------------------------------------------------
// EligibilityHttpAdapter — unit tests (GEST-FE-008)
// Critical path: fail-closed on emergencia when backend unreachable.
// ---------------------------------------------------------------------------

const BASE_URL = 'http://test-gest:8084';
const ELIGIBILITY_ENDPOINT = `${BASE_URL}/gest-service/eligibility`;

describe('EligibilityHttpAdapter', () => {
  let adapter: EligibilityHttpAdapter;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        EligibilityHttpAdapter,
        { provide: GEST_SERVICE_BASE_URL, useValue: BASE_URL },
        // EMPRESA_CONTEXT_SEAM has providedIn:'root' factory → no explicit provider needed;
        // the factory returns { id: null } (Wave-0 stub — no X-Empresa-Id header sent).
      ],
    });
    adapter = TestBed.inject(EligibilityHttpAdapter);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    TestBed.resetTestingModule();
  });

  it('should POST to /gest-service/eligibility with urgencyLevel in body', async () => {
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

  // -------------------------------------------------------------------------
  // CRÍTICO: fail-closed / fail-open (ADR-005 honest-degraded)
  // -------------------------------------------------------------------------

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

  it('should map unsupportedPrimitiveIds from backend missingStates', async () => {
    const promise = adapter.check('rotina');
    const req = httpMock.expectOne(ELIGIBILITY_ENDPOINT);
    req.flush({ eligible: false, missingStates: ['abc', 'def', 'ghi'] });

    const result = await promise;
    expect(result.unsupportedPrimitiveIds).toHaveLength(3);
    expect(result.unsupportedPrimitiveIds).toContain('abc');
  });

  // -------------------------------------------------------------------------
  // P5 preparatory: EmpresaContext injection seam (GEST-INT-002 / multi-empresa)
  // Verifies the injection point exists and the stub (id: null) does NOT change
  // HTTP call shape — no X-Empresa-Id header is sent while the stub is in effect.
  // -------------------------------------------------------------------------

  it('[P5-MULTI-EMPRESA] should POST without X-Empresa-Id header when empresaContext.id is null (Wave-0 stub behavior)', async () => {
    const promise = adapter.check('rotina');

    const req = httpMock.expectOne(ELIGIBILITY_ENDPOINT);
    // The X-Empresa-Id header should NOT be present — stub returns null
    expect(req.request.headers.has('X-Empresa-Id')).toBe(false);
    // Call still succeeds as normal
    req.flush({ eligible: true, missingStates: [] });

    const result = await promise;
    expect(result.eligible).toBe(true);
    await promise;
  });
});
