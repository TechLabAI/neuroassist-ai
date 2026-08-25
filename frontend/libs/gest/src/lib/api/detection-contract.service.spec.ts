import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  provideHttpClientTesting,
  HttpTestingController,
} from '@angular/common/http/testing';
import { DetectionContractService } from './detection-contract.service';
import { GEST_SERVICE_BASE_URL } from './gest-service.config';

// ---------------------------------------------------------------------------
// DetectionContractService — unit tests (GEST-FE-009)
// Covers: successful negotiate, 4xx fallback, network-error fallback, caching.
// Contract shape mirrored from DetectionContractController + ContractDescriptorV1 (Java).
// ---------------------------------------------------------------------------

const BASE_URL = 'http://test-gest:8084';
const CONTRACT_ENDPOINT = `${BASE_URL}/gest-service/detection-contract`;

const REAL_CONTRACT = {
  version: 'v1.0.0',
  requiredFields: ['gestureId', 'confidence', 'windowMs'],
  forbiddenFields: ['video', 'image', 'landmark', 'base64', 'pixels', 'frame'],
};

describe('DetectionContractService', () => {
  let service: DetectionContractService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        DetectionContractService,
        { provide: GEST_SERVICE_BASE_URL, useValue: BASE_URL },
      ],
    });
    service = TestBed.inject(DetectionContractService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    TestBed.resetTestingModule();
  });

  describe('negotiate() — successful call', () => {
    it('should GET /gest-service/detection-contract', async () => {
      const promise = service.negotiate();
      const req = httpMock.expectOne(CONTRACT_ENDPOINT);
      expect(req.request.method).toBe('GET');
      req.flush(REAL_CONTRACT);
      await promise;
    });

    it('should return the descriptor from the backend', async () => {
      const promise = service.negotiate();
      const req = httpMock.expectOne(CONTRACT_ENDPOINT);
      req.flush(REAL_CONTRACT);

      const result = await promise;
      expect(result.version).toBe('v1.0.0');
      expect(result.requiredFields).toContain('gestureId');
      expect(result.requiredFields).toContain('confidence');
      expect(result.requiredFields).toContain('windowMs');
      expect(result.forbiddenFields).toContain('landmark');
      expect(result.forbiddenFields).toContain('frame');
    });
  });

  describe('negotiate() — error handling', () => {
    it('should return safe fallback when backend returns 4xx', async () => {
      const promise = service.negotiate();
      const req = httpMock.expectOne(CONTRACT_ENDPOINT);
      req.flush('Forbidden', { status: 403, statusText: 'Forbidden' });

      const result = await promise;
      expect(result.version).toBe('unknown');
      // Fallback must still include the known required fields so stream validation works
      expect(result.requiredFields).toContain('gestureId');
      expect(result.forbiddenFields).toContain('landmark');
    });

    it('should return safe fallback when network error occurs', async () => {
      const promise = service.negotiate();
      const req = httpMock.expectOne(CONTRACT_ENDPOINT);
      req.error(new ProgressEvent('NetworkError'));

      const result = await promise;
      expect(result.version).toBe('unknown');
      expect(result.requiredFields.length).toBeGreaterThan(0);
      expect(result.forbiddenFields.length).toBeGreaterThan(0);
    });

    it('should never throw even when backend is unreachable', async () => {
      const promise = service.negotiate();
      const req = httpMock.expectOne(CONTRACT_ENDPOINT);
      req.error(new ProgressEvent('NetworkError'));

      let threw = false;
      try {
        await promise;
      } catch {
        threw = true;
      }
      expect(threw).toBe(false);
    });
  });

  describe('negotiate() — caching', () => {
    it('should not make a second HTTP call when descriptor is already cached', async () => {
      // First call — triggers HTTP
      const first = service.negotiate();
      const req = httpMock.expectOne(CONTRACT_ENDPOINT);
      req.flush(REAL_CONTRACT);
      await first;

      // Second call — must NOT trigger HTTP (httpMock.verify() would catch an extra request)
      const second = await service.negotiate();
      expect(second.version).toBe('v1.0.0');
    });

    it('should return same object reference on repeated calls after cache', async () => {
      const first = service.negotiate();
      const req = httpMock.expectOne(CONTRACT_ENDPOINT);
      req.flush(REAL_CONTRACT);
      const result1 = await first;

      const result2 = await service.negotiate();
      expect(result2).toBe(result1);
    });
  });
});
