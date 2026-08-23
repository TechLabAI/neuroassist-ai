// access-http.interceptor.spec.ts — FE-E2E-002 unit tests
//
// Covers:
//   AUTH-FE-03  Authorization header present on module requests
//   AUTH-FE-04  X-Empresa-Id from EMPRESA_ID_TOKEN signal
//   AUTH-FE-05  X-Correlation-Id per request (UUID format, distinct between requests)
//   AUTH-FE-06  401 → exactly 1 refresh + 1 replay
//   AUTH-FE-08  403 → 0 refresh calls
//   AUTH-FE-09  concurrent 401s share the in-flight refresh (verified at service level)
//   DEG-08      ProblemDetail (RFC 7807) → AppError mapping
//
// Note: "exactly 1 HTTP request for refresh across N concurrent 401s" is tested
//   in auth-session.service.spec.ts (single-flight is a SERVICE invariant, not
//   an INTERCEPTOR invariant). Here we test that the interceptor correctly
//   delegates to auth.refresh() and replays the request.

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
import { AuthSessionService } from '@neuroassist/shared/auth-session';
import type { AppError } from '@neuroassist/shared/error-model';

// ---------------------------------------------------------------------------
// AuthSessionService mock factory
// ---------------------------------------------------------------------------

function createAuthMock(initialToken: string | null = 'bearer-test-token') {
  const _token = signal<string | null>(initialToken);
  return {
    accessToken: _token.asReadonly(),
    isAuthenticated: signal(initialToken !== null),
    claims: signal(null),
    refresh: jest.fn<() => Promise<void>>().mockResolvedValue(undefined),
    login: jest.fn<(u: string, p: string) => Promise<void>>().mockResolvedValue(undefined),
    logout: jest.fn<() => void>(),
    /** test helper — update token signal */
    _setToken: (t: string | null) => _token.set(t),
  };
}

const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('accessHttpInterceptor', () => {
  let http: HttpClient;
  let controller: HttpTestingController;
  let authMock: ReturnType<typeof createAuthMock>;
  let empresaIdSignal: ReturnType<typeof signal<string | null>>;

  beforeEach(() => {
    authMock = createAuthMock('valid-access-token');
    empresaIdSignal = signal<string | null>('empresa-uuid-001');

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([accessHttpInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthSessionService, useValue: authMock },
        { provide: EMPRESA_ID_TOKEN, useValue: empresaIdSignal },
      ],
    });

    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    controller.verify();
    jest.clearAllMocks();
  });

  // ── AUTH-FE-03: Authorization header ─────────────────────────────────────

  it('adds Authorization: Bearer <token> to authenticated requests (AUTH-FE-03)', () => {
    http.get('/api/v1/pac/pacientes').subscribe();

    const req = controller.expectOne('/api/v1/pac/pacientes');
    expect(req.request.headers.get('Authorization')).toBe('Bearer valid-access-token');
    req.flush([]);
  });

  it('does NOT add Authorization to POST /oauth2/token (login exempt)', () => {
    http
      .post('/oauth2/token', { grantType: 'password', login: 'u', senha: 'p' })
      .subscribe();

    const req = controller.expectOne('/oauth2/token');
    expect(req.request.headers.has('Authorization')).toBe(false);
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

  it('does NOT add Authorization to POST /oauth2/token/refresh (refresh exempt)', () => {
    http.post('/oauth2/token/refresh', { refreshToken: 'rt' }).subscribe();

    const req = controller.expectOne('/oauth2/token/refresh');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({
      accessToken: 'x2',
      refreshToken: 'y2',
      tokenType: 'Bearer',
      expiresIn: 900,
      tenant: 'tid',
      sub: 'sid',
      papel: 'P',
      authorities: [],
    });
  });

  it('does NOT add Authorization to .../devices/enroll endpoint', () => {
    http.post('/api/v1/edge/devices/enroll', { enrollmentToken: 'tok' }).subscribe();

    const req = controller.expectOne('/api/v1/edge/devices/enroll');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({ deviceId: 'dev-001' });
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

  it('fails immediately with EMPRESA_REQUIRED error when empresa signal is null', fakeAsync(() => {
    empresaIdSignal.set(null); // No active empresa

    let caughtErr: AppError | undefined;

    http.get('/api/v1/pac/pacientes').subscribe({
      error: (err: AppError) => {
        caughtErr = err;
      },
    });

    tick(); // flush synchronous error path

    // No HTTP request should have been made — error before sending
    controller.expectNone('/api/v1/pac/pacientes');
    expect(caughtErr?.code).toBe('EMPRESA_REQUIRED');
    expect(caughtErr?.message).toContain('No active empresa');
  }));

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

  // ── AUTH-FE-06: 401 → refresh + replay ────────────────────────────────────

  it('401 on authenticated request triggers auth.refresh() + replay (AUTH-FE-06)', fakeAsync(() => {
    authMock.refresh.mockResolvedValue(undefined);

    let receivedData: unknown;

    http.get('/api/v1/pac/pacientes').subscribe({
      next: (d) => {
        receivedData = d;
      },
    });

    // First attempt returns 401
    controller.expectOne('/api/v1/pac/pacientes').flush(
      { type: '/errors/unauthorized', title: 'Unauthorized', status: 401 },
      { status: 401, statusText: 'Unauthorized' },
    );

    // Flush the refresh promise microtask
    tick(0);

    // Replay request should now be pending
    controller.expectOne('/api/v1/pac/pacientes').flush([{ id: 'p1' }]);

    tick(0);

    expect(authMock.refresh).toHaveBeenCalledTimes(1);
    expect(receivedData).toEqual([{ id: 'p1' }]);
  }));

  // ── AUTH-FE-08: 403 → no refresh ─────────────────────────────────────────

  it('403 does NOT trigger auth.refresh() (AUTH-FE-08)', fakeAsync(() => {
    let caughtErr: AppError | undefined;

    http.get('/api/v1/hist/history').subscribe({
      error: (err: AppError) => {
        caughtErr = err;
      },
    });

    controller.expectOne('/api/v1/hist/history').flush(
      { type: '/errors/forbidden', title: 'Forbidden', status: 403 },
      { status: 403, statusText: 'Forbidden' },
    );

    tick();

    expect(authMock.refresh).not.toHaveBeenCalled();
    expect(caughtErr?.code).toBe('403');
  }));

  // ── AUTH-FE-09: concurrent 401 handling ───────────────────────────────────
  // The single-flight guarantee (exactly 1 HTTP refresh request for N concurrent callers)
  // is tested in auth-session.service.spec.ts — that is a SERVICE invariant.
  // Here we test the INTERCEPTOR behavior: after a 401, it calls auth.refresh()
  // and replays the original request, regardless of other concurrent 401s.

  it('multiple concurrent 401 responses are each replayed after shared refresh (AUTH-FE-09)', fakeAsync(() => {
    // Single-flight simulation: all callers get the same promise
    let resolveRefresh!: () => void;
    let sharedRefreshPromise: Promise<void> | null = null;
    authMock.refresh.mockImplementation(() => {
      if (!sharedRefreshPromise) {
        sharedRefreshPromise = new Promise<void>((res) => {
          resolveRefresh = res;
        }).finally(() => {
          sharedRefreshPromise = null;
        });
      }
      return sharedRefreshPromise;
    });

    const results: unknown[] = [];
    const errs: unknown[] = [];

    http.get('/api/v1/pac/pacientes').subscribe({
      next: (d) => results.push(d),
      error: (e) => errs.push(e),
    });
    http.get('/api/v1/acs/caregivers').subscribe({
      next: (d) => results.push(d),
      error: (e) => errs.push(e),
    });
    http.get('/api/v1/hist/history').subscribe({
      next: (d) => results.push(d),
      error: (e) => errs.push(e),
    });

    // All 3 initial requests get 401
    ['pac/pacientes', 'acs/caregivers', 'hist/history'].forEach((path) => {
      controller.expectOne(`/api/v1/${path}`).flush(
        { status: 401 },
        { status: 401, statusText: 'Unauthorized' },
      );
    });

    tick(0);

    // Resolve the shared refresh
    resolveRefresh();
    tick(0);

    // Flush retry requests
    ['pac/pacientes', 'acs/caregivers', 'hist/history'].forEach((path) => {
      controller.expectOne(`/api/v1/${path}`).flush([]);
    });

    tick(0);

    expect(errs).toHaveLength(0);
    expect(results).toHaveLength(3);
    // refresh was called (1+ times, single-flight reduces to 1 HTTP request — see service spec)
    expect(authMock.refresh).toHaveBeenCalled();
  }));

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
