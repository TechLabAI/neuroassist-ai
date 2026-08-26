// =============================================================================
// acs-link.contract.spec.ts — CTR-01c (FE-E2E-005 / P3)
//
// Verifica que AcsLinkMockAdapter e AcsLinkHttpAdapter satisfazem
// AcsLinkPort com o mesmo shape de dados.
//
// NUANCE 1: ACCESS_HTTP_BASE é InjectionToken — provido com useValue: ''
// Base resultante: '/v1/acs/caregivers'
// Endpoints: GET/POST /v1/acs/caregivers/{cid}/links,
//            DELETE /v1/acs/caregivers/{cid}/links/{linkId}
// =============================================================================

import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  provideHttpClientTesting,
  HttpTestingController,
} from '@angular/common/http/testing';
import { ACCESS_HTTP_BASE } from '@neuroassist/shared/access-http-base';
import { AcsLinkPort, CaregiverLinkDto } from './acs-link.port';
import { AcsLinkHttpAdapter } from './acs-link-http.adapter';
import { AcsLinkMockAdapter } from './acs-link-mock.adapter';

const STUB_LINK: CaregiverLinkDto = {
  id: 'link-test-001',
  cuidadorId: 'cuid-test',
  pacienteId: 'pac-test',
  active: true,
  createdAt: '2026-08-22T09:00:00Z',
};

describe.each([
  ['mock' as const, () => new AcsLinkMockAdapter()],
  ['http' as const, () => TestBed.inject(AcsLinkHttpAdapter)],
])('%s satisfaz AcsLinkPort', (label, adapterFactory) => {
  let adapter: AcsLinkPort;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ACCESS_HTTP_BASE, useValue: '' },
        AcsLinkHttpAdapter,
        AcsLinkMockAdapter,
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
  // listForCaregiver()
  // ---------------------------------------------------------------------------

  it('listForCaregiver() retorna CaregiverLinkDto[] com shape correto', fakeAsync(() => {
    let result: CaregiverLinkDto[] | undefined;
    let error: unknown;

    adapter.listForCaregiver('cuid-jamilly').subscribe({
      next: r => { result = r; },
      error: e => { error = e; },
    });

    if (label === 'http') {
      const req = controller.expectOne('/v1/acs/caregivers/cuid-jamilly/links');
      expect(req.request.method).toBe('GET');
      req.flush([STUB_LINK]);
    } else {
      tick(1000);
    }

    expect(error).toBeUndefined();
    expect(Array.isArray(result)).toBe(true);
    if (result!.length > 0) {
      expect(result![0]).toMatchObject({
        id: expect.any(String),
        cuidadorId: expect.any(String),
        pacienteId: expect.any(String),
        active: expect.any(Boolean),
        createdAt: expect.any(String),
      });
    }
  }));

  // ---------------------------------------------------------------------------
  // link()
  // ---------------------------------------------------------------------------

  it('link() retorna CaregiverLinkDto com campos obrigatórios', fakeAsync(() => {
    let result: CaregiverLinkDto | undefined;
    let error: unknown;

    // mock: usar pacienteId conhecido no seed para não disparar 404
    const pid = label === 'mock' ? 'pac-marcio' : 'pac-test';

    adapter.link('cuid-jamilly', pid).subscribe({
      next: r => { result = r; },
      error: e => { error = e; },
    });

    if (label === 'http') {
      const req = controller.expectOne('/v1/acs/caregivers/cuid-jamilly/links');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toMatchObject({ pacienteId: pid });
      req.flush(STUB_LINK);
    } else {
      tick(1000);
    }

    expect(error).toBeUndefined();
    expect(result).toMatchObject({
      id: expect.any(String),
      cuidadorId: expect.any(String),
      pacienteId: expect.any(String),
      active: expect.any(Boolean),
      createdAt: expect.any(String),
    });
  }));

  // ---------------------------------------------------------------------------
  // revoke()
  // ---------------------------------------------------------------------------

  it('revoke() retorna void (Observable<void>)', fakeAsync(() => {
    let completed = false;
    let error: unknown;

    // mock: pac-marcio tem link-001 (jamilly) e link-002 (maria) — não é last-covering
    // http: qualquer linkId funciona com HttpTestingController
    adapter.revoke('cuid-jamilly', 'link-001').subscribe({
      next: () => { completed = true; },
      complete: () => { completed = true; },
      error: e => { error = e; },
    });

    if (label === 'http') {
      const req = controller.expectOne('/v1/acs/caregivers/cuid-jamilly/links/link-001');
      expect(req.request.method).toBe('DELETE');
      req.flush(null, { status: 204, statusText: 'No Content' });
    } else {
      tick(1000);
    }

    expect(error).toBeUndefined();
    expect(completed).toBe(true);
  }));
});
