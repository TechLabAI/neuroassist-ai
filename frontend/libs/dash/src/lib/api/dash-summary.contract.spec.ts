// =============================================================================
// dash-summary.contract.spec.ts — CTR-11 (FE-E2E-005 / P3)
//
// Verifica que DashSummaryMockAdapter e DashSummaryHttpAdapter satisfazem
// DashSummaryPort com o mesmo shape de dados.
//
// Base path (normalizado em P2 / ADR-008): /api/v1/dash
// Previously: /panel (direct service path, pre-ADR-008)
// =============================================================================

import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  provideHttpClientTesting,
  HttpTestingController,
} from '@angular/common/http/testing';
import { DashSummaryPort } from './dash-summary.port';
import { DashSummaryHttpAdapter } from './dash-summary-http.adapter';
import { DashSummaryMockAdapter } from './dash-summary-mock.adapter';
import type { DashSummaryResponse } from '../dash.types';

const STUB_SUMMARY: DashSummaryResponse = {
  tenantId: 'tenant-familia-amaral',
  caregiver: {
    id: 'cuid-test',
    name: 'Tester',
    role: 'cuidadora',
  },
  patients: [
    {
      patientId: 'pac-marcio',
      name: 'Márcio Amaral',
      device: {
        status: 'online',
        lastSeenAt: '2026-08-22T10:00:00Z',
        degraded: false,
      },
      latestEvent: {
        eventId: 'evt-001',
        commandLabel: 'Ajuda',
        urgencia: 'emergencia',
        feedback: 'nao_avaliado',
        at: '2026-08-22T09:40:00Z',
      },
      unacknowledgedCount: 1,
    },
  ],
  degradedSections: [],
};

describe.each([
  ['mock' as const, () => new DashSummaryMockAdapter()],
  ['http' as const, () => TestBed.inject(DashSummaryHttpAdapter)],
])('%s satisfaz DashSummaryPort', (label, adapterFactory) => {
  let adapter: DashSummaryPort;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        DashSummaryHttpAdapter,
        DashSummaryMockAdapter,
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
  // getSummary()
  // ---------------------------------------------------------------------------

  it('getSummary() retorna DashSummaryResponse com campos obrigatórios', fakeAsync(() => {
    let result: DashSummaryResponse | undefined;
    let error: unknown;

    adapter.getSummary().subscribe({
      next: r => { result = r; },
      error: e => { error = e; },
    });

    if (label === 'http') {
      const req = controller.expectOne('/api/v1/dash/summary');
      expect(req.request.method).toBe('GET');
      req.flush(STUB_SUMMARY);
    } else {
      tick(1000);
    }

    expect(error).toBeUndefined();
    expect(result).toMatchObject({
      tenantId: expect.any(String),
      caregiver: expect.objectContaining({
        id: expect.any(String),
        name: expect.any(String),
        role: expect.any(String),
      }),
      patients: expect.any(Array),
      degradedSections: expect.any(Array),
    });
  }));

  it('getSummary() patients têm shape correto', fakeAsync(() => {
    let result: DashSummaryResponse | undefined;

    adapter.getSummary().subscribe(r => { result = r; });

    if (label === 'http') {
      const req = controller.expectOne('/api/v1/dash/summary');
      req.flush(STUB_SUMMARY);
    } else {
      tick(1000);
    }

    if (result!.patients.length > 0) {
      const patient = result!.patients[0];
      expect(patient).toMatchObject({
        patientId: expect.any(String),
        name: expect.any(String),
        device: expect.objectContaining({
          status: expect.stringMatching(/^(online|offline|unknown)$/),
          degraded: expect.any(Boolean),
        }),
        unacknowledgedCount: expect.any(Number),
      });
    }
  }));
});
