// access-http.interceptor.spec.ts — FE-E2E-002 unit tests
//
// Covers:
//   AUTH-FE-04  X-Empresa-Id from EMPRESA_ID_TOKEN signal, traceability-only (never blocks)
//   AUTH-FE-05  X-Correlation-Id per request (UUID format, distinct between requests)
//   DEG-08      ProblemDetail (RFC 7807) → AppError mapping
//
// Authorization and 401-refresh are NOT this interceptor's responsibility — see
// access-http.interceptor.ts header comment. Those are covered by
// auth.interceptor.spec.ts (@neuroassist/shared/auth-login).

import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import {
  HttpClient,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { accessHttpInterceptor } from './access-http.interceptor';
import { EMPRESA_ID_TOKEN } from './access-http-base';
import type { AppError } from '@neuroassist/shared/error-model';

const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

describe('accessHttpInterceptor', () => {
  let http: HttpClient;
  let controller: HttpTestingController;
  let empresaIdSignal: ReturnType<typeof signal<string | null>>;

  beforeEach(() => {
    empresaIdSignal = signal<string | null>('empresa-uuid-001');

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([accessHttpInterceptor])),
        provideHttpClientTesting(),
        { provide: EMPRESA_ID_TOKEN, useValue: empresaIdSignal },
      ],
    });

    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    controller.verify();
  });

  // ── AUTH-FE-04: X-Empresa-Id ──────────────────────────────────────────────

  it('adds X-Empresa-Id from EMPRESA_ID_TOKEN signal (AUTH-FE-04)', () => {
    http.get('/api/v1/hist/history').subscribe();

    const req = controller.expectOne('/api/v1/hist/history');
    expect(req.request.headers.get('X-Empresa-Id')).toBe('empresa-uuid-001');
    req.flush([]);
  });

  it('does NOT add X-Empresa-Id to /oauth2/* endpoints', () => {
    http
      .post('/oauth2/token', { grantType: 'password', login: 'u', senha: 'p' })
      .subscribe();

    const req = controller.expectOne('/oauth2/token');
    expect(req.request.headers.has('X-Empresa-Id')).toBe(false);
    req.flush({
      accessToken: 'x',
      refreshToken: 'y',
      tokenType: 'Bearer',
      expiresIn: 900,
      tenant: 'tid',
      sub: 'sid',
      papel: 'P',
      authorities: [],
    });
  });

  it('omits X-Empresa-Id (does not block the request) when empresa signal is null', () => {
    empresaIdSignal.set(null); // No active empresa yet — traceability header only, not a gate.

    http.get('/api/v1/pac/pacientes').subscribe();

    const req = controller.expectOne('/api/v1/pac/pacientes');
    expect(req.request.headers.has('X-Empresa-Id')).toBe(false);
    req.flush([]);
  });

  // ── AUTH-FE-05: X-Correlation-Id ─────────────────────────────────────────

  it('adds X-Correlation-Id as UUID to each request, unique between requests (AUTH-FE-05)', () => {
    http.get('/api/v1/acs/caregivers').subscribe();
    http.get('/api/v1/acs/caregivers').subscribe();

    const reqs = controller.match('/api/v1/acs/caregivers');
    expect(reqs).toHaveLength(2);

    const id1 = reqs[0].request.headers.get('X-Correlation-Id') ?? '';
    const id2 = reqs[1].request.headers.get('X-Correlation-Id') ?? '';

    expect(id1).toMatch(UUID_PATTERN);
    expect(id2).toMatch(UUID_PATTERN);
    expect(id1).not.toBe(id2); // distinct per request

    reqs.forEach((r) => r.flush([]));
  });

  // ── Idempotency-Key ────────────────────────────────────────────────────────

  it('adds Idempotency-Key to POST requests', () => {
    http.post('/api/v1/pac/pacientes', { nome: 'Test' }).subscribe();

    const req = controller.expectOne('/api/v1/pac/pacientes');
    const key = req.request.headers.get('Idempotency-Key');
    expect(key).toBeTruthy();
    expect(key).toMatch(UUID_PATTERN);
    req.flush({});
  });

  it('adds Idempotency-Key to PUT requests', () => {
    http.put('/api/v1/pac/pacientes/123', { nome: 'Test' }).subscribe();

    const req = controller.expectOne('/api/v1/pac/pacientes/123');
    expect(req.request.headers.get('Idempotency-Key')).toMatch(UUID_PATTERN);
    req.flush({});
  });

  it('does NOT add Idempotency-Key to GET requests', () => {
    http.get('/api/v1/pac/pacientes').subscribe();

    const req = controller.expectOne('/api/v1/pac/pacientes');
    expect(req.request.headers.has('Idempotency-Key')).toBe(false);
    req.flush([]);
  });

  // ── DEG-08: ProblemDetail → AppError ──────────────────────────────────────

  it('maps ProblemDetail body (RFC 7807) to AppError with type (DEG-08)', fakeAsync(() => {
    let caughtErr: AppError | undefined;

    http.get('/api/v1/pac/pacientes').subscribe({
      error: (err: AppError) => {
        caughtErr = err;
      },
    });

    controller.expectOne('/api/v1/pac/pacientes').flush(
      {
        type: '/errors/tenant-mismatch',
        title: 'Tenant Mismatch',
        detail: 'Empresa no header differs from token claim',
        status: 403,
      },
      { status: 403, statusText: 'Forbidden' },
    );

    tick();

    expect(caughtErr?.code).toBe('403');
    expect(caughtErr?.message).toBe('Empresa no header differs from token claim');
    expect(caughtErr?.type).toBe('/errors/tenant-mismatch');
  }));

  it('maps plain HTTP error (no ProblemDetail body) to AppError with status code', fakeAsync(() => {
    let caughtErr: AppError | undefined;

    http.get('/api/v1/edge/devices').subscribe({
      error: (err: AppError) => {
        caughtErr = err;
      },
    });

    controller.expectOne('/api/v1/edge/devices').flush(
      'Internal Server Error',
      { status: 500, statusText: 'Internal Server Error' },
    );

    tick();

    expect(caughtErr?.code).toBe('500');
    expect(caughtErr?.message).toBeTruthy();
  }));
});
