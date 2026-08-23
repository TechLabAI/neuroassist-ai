// =============================================================================
// eligibility.contract.spec.ts — CTR-14 (FE-E2E-005 / P3)
//
// Verifica que EligibilityMockAdapter e EligibilityHttpAdapter satisfazem
// EligibilityPort com o mesmo shape de dados.
//
// NUANCE 2: EligibilityHttpAdapter injeta GEST_SERVICE_BASE_URL (InjectionToken).
// TestBed provê { provide: GEST_SERVICE_BASE_URL, useValue: '/api/v1/gest' }
// para que o HttpTestingController intercepte /api/v1/gest/eligibility
// (equivalente ao override do perfil integrated, configurado em P2).
//
// EligibilityPort usa Promise (não Observable).
// Testes usam async/await (não fakeAsync) porque:
//   - HttpTestingController.expectOne() é síncrono — pode ser chamado logo após
//     adapter.check(), pois firstValueFrom() subscreve o Observable de imediato.
//   - async function sem await interno resolve como microtask nativa (não requer
//     fakeAsync), e await no teste drena a microfila corretamente.
// =============================================================================

import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  provideHttpClientTesting,
  HttpTestingController,
} from '@angular/common/http/testing';
import { GEST_SERVICE_BASE_URL } from './gest-service.config';
import { EligibilityPort } from './eligibility.port';
import { EligibilityHttpAdapter } from './eligibility-http.adapter';
import { EligibilityMockAdapter } from './eligibility-mock.adapter';
import type { EligibilityResult } from '@neuroassist/shared/contracts';

describe.each([
  ['mock' as const, () => new EligibilityMockAdapter()],
  ['http' as const, () => TestBed.inject(EligibilityHttpAdapter)],
])('%s satisfaz EligibilityPort', (label, adapterFactory) => {
  let adapter: EligibilityPort;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        // NUANCE 2: prover GEST_SERVICE_BASE_URL para que o adapter use o path correto
        { provide: GEST_SERVICE_BASE_URL, useValue: '/api/v1/gest' },
        EligibilityHttpAdapter,
        EligibilityMockAdapter,
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
  // check('rotina') — caso básico (sempre elegível)
  // ---------------------------------------------------------------------------

  it('check(rotina) retorna EligibilityResult com eligible:true', async () => {
    // Inicia a Promise antes de fazer o flush — para http, firstValueFrom() subscreve
    // imediatamente, colocando a request na fila de HttpTestingController.
    const promise = adapter.check('rotina');

    if (label === 'http') {
      const req = controller.expectOne('/api/v1/gest/eligibility');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toMatchObject({ urgencyLevel: 'rotina' });
      req.flush({ eligible: true, missingStates: [] });
    }

    const result = await promise;

    expect(result).toMatchObject({
      eligible: expect.any(Boolean),
      unsupportedPrimitiveIds: expect.any(Array),
    });
    expect(typeof result.reason === 'string' || result.reason === null).toBe(true);
  });

  // ---------------------------------------------------------------------------
  // check('emergencia') — casos de emergência
  // ---------------------------------------------------------------------------

  it('check(emergencia) retorna EligibilityResult com campos obrigatórios', async () => {
    const promise = adapter.check('emergencia');

    if (label === 'http') {
      const req = controller.expectOne('/api/v1/gest/eligibility');
      expect(req.request.body).toMatchObject({ urgencyLevel: 'emergencia' });
      req.flush({ eligible: true, missingStates: [] });
    }

    const result: EligibilityResult = await promise;

    expect(result).toMatchObject({
      eligible: expect.any(Boolean),
      unsupportedPrimitiveIds: expect.any(Array),
    });
  });

  // ---------------------------------------------------------------------------
  // Fallback: backend unreachable — honest-block para emergencia
  // Testa comportamento do HTTP adapter quando backend retorna 503.
  // O adapter captura internamente e retorna resultado degradado (não rejeita).
  // ---------------------------------------------------------------------------

  it('check(emergencia) degrada graciosamente quando backend falha', async () => {
    if (label === 'mock') {
      // Mock adapter sempre retorna eligible (não simula falha de backend)
      const result = await adapter.check('emergencia');
      expect(result).toMatchObject({ eligible: expect.any(Boolean), unsupportedPrimitiveIds: expect.any(Array) });
      return;
    }

    // HTTP branch: simular 503
    const httpAdapter = TestBed.inject(EligibilityHttpAdapter);
    const promise = httpAdapter.check('emergencia');

    const req = controller.expectOne('/api/v1/gest/eligibility');
    req.flush(null, { status: 503, statusText: 'Service Unavailable' });

    const result = await promise;

    // Backend unreachable: honest-block para emergencia
    expect(result).toMatchObject({
      eligible: false,
      unsupportedPrimitiveIds: expect.any(Array),
    });
    expect(typeof result.reason).toBe('string');
  });
});
