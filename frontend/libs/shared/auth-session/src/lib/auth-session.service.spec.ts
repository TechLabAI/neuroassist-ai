// auth-session.service.spec.ts — FE-E2E-003 unit tests
//
// Covers: AUTH-FE-01, AUTH-FE-02, AUTH-FE-07
// + single-flight refresh (3 concurrent → exactly 1 HTTP request)
// + sessionStorage persistence (write path)
// + sessionStorage restoration (read path on service construction)

import { TestBed } from '@angular/core/testing';
import {
  provideHttpClient,
} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AuthSessionService } from './auth-session.service';
import { AuthClaims } from './auth-session';

/** Mirrors AutenticacaoResponse.java */
const TOKEN_RESPONSE = {
  accessToken: 'access-jwt-v1',
  refreshToken: 'refresh-jwt-v1',
  tokenType: 'Bearer',
  expiresIn: 900,
  tenant: 'tenant-uuid-001',
  sub: 'caregiver-uuid-001',
  papel: 'CUIDADORA',
  authorities: ['ROLE_CAREGIVER', 'pac.paciente.read', 'hist:read'],
};

const REFRESHED_RESPONSE = {
  ...TOKEN_RESPONSE,
  accessToken: 'access-jwt-v2',
  refreshToken: 'refresh-jwt-v2',
};

const STORAGE_KEY = 'na_auth_v1';

// ---------------------------------------------------------------------------
// Standard tests (service starts with empty sessionStorage)
// ---------------------------------------------------------------------------

describe('AuthSessionService', () => {
  let service: AuthSessionService;
  let controller: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });

    service = TestBed.inject(AuthSessionService);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    controller.verify();
    sessionStorage.clear();
  });

  // ── AUTH-FE-01: login ──────────────────────────────────────────────────────

  it('login() sends POST /oauth2/token with correct body (AUTH-FE-01)', async () => {
    const loginPromise = service.login('jamilly@test.com', 'senha123');

    const req = controller.expectOne('/oauth2/token');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      grantType: 'password',
      login: 'jamilly@test.com',
      senha: 'senha123',
    });
    req.flush(TOKEN_RESPONSE);

    await loginPromise;
  });

  it('login() populates accessToken and isAuthenticated signals on success', async () => {
    expect(service.accessToken()).toBeNull();
    expect(service.isAuthenticated()).toBe(false);

    const loginPromise = service.login('jamilly@test.com', 'senha123');
    controller.expectOne('/oauth2/token').flush(TOKEN_RESPONSE);
    await loginPromise;

    expect(service.accessToken()).toBe('access-jwt-v1');
    expect(service.isAuthenticated()).toBe(true);
  });

  it('login() populates claims — empresaId from "tenant", scopes from "authorities"', async () => {
    const loginPromise = service.login('jamilly@test.com', 'senha123');
    controller.expectOne('/oauth2/token').flush(TOKEN_RESPONSE);
    await loginPromise;

    const claims = service.claims() as AuthClaims;
    expect(claims.sub).toBe('caregiver-uuid-001');
    expect(claims.empresaId).toBe('tenant-uuid-001'); // from "tenant" field of response
    expect(claims.scopes).toEqual(['ROLE_CAREGIVER', 'pac.paciente.read', 'hist:read']);
  });

  // ── AUTH-FE-02: invalid credentials ───────────────────────────────────────

  it('login() with invalid credentials throws and does NOT store token (AUTH-FE-02)', async () => {
    const loginPromise = service.login('bad@test.com', 'wrong');

    controller.expectOne('/oauth2/token').flush(
      { type: '/errors/invalid-credentials', title: 'Unauthorized', status: 401 },
      { status: 401, statusText: 'Unauthorized' },
    );

    await expect(loginPromise).rejects.toBeTruthy();
    expect(service.accessToken()).toBeNull();
    expect(service.isAuthenticated()).toBe(false);
  });

  // ── refresh ────────────────────────────────────────────────────────────────

  it('refresh() sends POST /oauth2/token/refresh with refreshToken', async () => {
    // First login to get a refresh token
    const loginPromise = service.login('jamilly@test.com', 'senha123');
    controller.expectOne('/oauth2/token').flush(TOKEN_RESPONSE);
    await loginPromise;

    const refreshPromise = service.refresh();
    const req = controller.expectOne('/oauth2/token/refresh');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ refreshToken: 'refresh-jwt-v1' });
    req.flush(REFRESHED_RESPONSE);

    await refreshPromise;
    expect(service.accessToken()).toBe('access-jwt-v2');
  });

  // AUTH-FE-09: single-flight — 3 concurrent calls → exactly 1 HTTP request
  it('refresh() single-flight: 3 concurrent calls produce exactly 1 HTTP request (AUTH-FE-09)', async () => {
    // Login to get a refresh token
    const loginPromise = service.login('jamilly@test.com', 'senha123');
    controller.expectOne('/oauth2/token').flush(TOKEN_RESPONSE);
    await loginPromise;

    // Fire 3 concurrent refresh calls without awaiting
    const p1 = service.refresh();
    const p2 = service.refresh();
    const p3 = service.refresh();

    // controller.expectOne() asserts EXACTLY 1 pending request (throws if != 1)
    const req = controller.expectOne('/oauth2/token/refresh');
    req.flush(REFRESHED_RESPONSE);

    // All 3 callers get the same resolved promise
    await Promise.all([p1, p2, p3]);
    expect(service.accessToken()).toBe('access-jwt-v2');
  });

  // ── AUTH-FE-07: refresh failure → logout ──────────────────────────────────

  it('refresh() failure clears session and throws (AUTH-FE-07)', async () => {
    const loginPromise = service.login('jamilly@test.com', 'senha123');
    controller.expectOne('/oauth2/token').flush(TOKEN_RESPONSE);
    await loginPromise;

    expect(service.isAuthenticated()).toBe(true);

    const refreshPromise = service.refresh();
    controller.expectOne('/oauth2/token/refresh').flush(
      { title: 'Unauthorized', status: 401 },
      { status: 401, statusText: 'Unauthorized' },
    );

    await expect(refreshPromise).rejects.toBeTruthy();
    expect(service.accessToken()).toBeNull();
    expect(service.isAuthenticated()).toBe(false);
    expect(service.claims()).toBeNull();
  });

  // ── logout ─────────────────────────────────────────────────────────────────

  it('logout() clears all signals', async () => {
    const loginPromise = service.login('jamilly@test.com', 'senha123');
    controller.expectOne('/oauth2/token').flush(TOKEN_RESPONSE);
    await loginPromise;

    service.logout();

    expect(service.accessToken()).toBeNull();
    expect(service.claims()).toBeNull();
    expect(service.isAuthenticated()).toBe(false);
  });

  it('logout() removes session from sessionStorage', async () => {
    const loginPromise = service.login('jamilly@test.com', 'senha123');
    controller.expectOne('/oauth2/token').flush(TOKEN_RESPONSE);
    await loginPromise;

    expect(sessionStorage.getItem(STORAGE_KEY)).not.toBeNull();
    service.logout();
    expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  // ── sessionStorage write path ──────────────────────────────────────────────

  it('login() persists session to sessionStorage (write path)', async () => {
    const loginPromise = service.login('jamilly@test.com', 'senha123');
    controller.expectOne('/oauth2/token').flush(TOKEN_RESPONSE);
    await loginPromise;

    const raw = sessionStorage.getItem(STORAGE_KEY);
    expect(raw).not.toBeNull();
    if (!raw) return; // satisfies typescript, already asserted above
    const stored = JSON.parse(raw) as { accessToken: string; claims: AuthClaims; expiresAt: number };
    expect(stored.accessToken).toBe('access-jwt-v1');
    expect(stored.claims.empresaId).toBe('tenant-uuid-001');
    expect(stored.expiresAt).toBeGreaterThan(Date.now());
  });
});

// ---------------------------------------------------------------------------
// sessionStorage restoration tests — service created AFTER sessionStorage is set
// ---------------------------------------------------------------------------

describe('AuthSessionService — sessionStorage restoration', () => {
  let service: AuthSessionService;
  let controller: HttpTestingController;

  afterEach(() => {
    controller?.verify();
    sessionStorage.clear();
  });

  it('restores valid (non-expired) session on construction', () => {
    const futureExpiry = Date.now() + 900_000; // 15 min from now
    sessionStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({
        accessToken: 'restored-token',
        refreshToken: 'restored-refresh',
        expiresAt: futureExpiry,
        claims: { sub: 'cuid-001', empresaId: 'empresa-001', scopes: ['hist:read'] },
      }),
    );

    // Create fresh TestBed AFTER sessionStorage is set
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    service = TestBed.inject(AuthSessionService);
    controller = TestBed.inject(HttpTestingController);

    expect(service.accessToken()).toBe('restored-token');
    expect(service.isAuthenticated()).toBe(true);
    expect(service.claims()?.empresaId).toBe('empresa-001');
  });

  it('ignores expired session from sessionStorage and clears it', () => {
    const pastExpiry = Date.now() - 1000; // already expired
    sessionStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({
        accessToken: 'expired-token',
        refreshToken: 'expired-refresh',
        expiresAt: pastExpiry,
        claims: { sub: 'x', empresaId: 'y', scopes: [] },
      }),
    );

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    service = TestBed.inject(AuthSessionService);
    controller = TestBed.inject(HttpTestingController);

    expect(service.accessToken()).toBeNull();
    expect(service.isAuthenticated()).toBe(false);
    // Service should have cleared the expired session
    expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull();
  });
});
