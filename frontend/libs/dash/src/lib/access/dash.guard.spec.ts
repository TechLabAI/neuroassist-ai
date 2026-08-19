import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot } from '@angular/router';
import { authGuard, dashGuard, roleGuard } from './dash.guard';
import { DASH_AUTH_SESSION, DASH_ACCESS_DECISION } from './dash-access-tokens';

// =============================================================================
// dash.guard spec — P2 (DASH-FE-003)
// Wave-0 bypass: all guards return true when tokens resolve to null.
// Guards use inject(TOKEN, { optional: true }); factory provides null by default.
// =============================================================================

const mockRoute = {} as ActivatedRouteSnapshot;
const mockState = {} as RouterStateSnapshot;

// ---------------------------------------------------------------------------
// authGuard
// ---------------------------------------------------------------------------

describe('authGuard', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('returns true when DASH_AUTH_SESSION is null (Wave-0 bypass active)', () => {
    TestBed.configureTestingModule({
      providers: [{ provide: DASH_AUTH_SESSION, useValue: null }],
    });

    const result = TestBed.runInInjectionContext(() =>
      authGuard(mockRoute, mockState),
    );

    expect(result).toBe(true);
  });

  it('returns true with default factory (no explicit provider — Wave-0 bypass)', () => {
    // DASH_AUTH_SESSION has { providedIn: 'root', factory: () => null }
    // So inject resolves to null → guard bypasses.
    TestBed.configureTestingModule({});

    const result = TestBed.runInInjectionContext(() =>
      authGuard(mockRoute, mockState),
    );

    expect(result).toBe(true);
  });

  it('returns UrlTree (redirect to /entrada) when session.token is null', () => {
    const mockSession = { token: null, scopes: new Set<string>() };
    const mockRouter = {
      createUrlTree: jest.fn().mockReturnValue({ redirectTo: '/entrada' }),
    };

    TestBed.configureTestingModule({
      providers: [
        { provide: DASH_AUTH_SESSION, useValue: mockSession },
        { provide: Router, useValue: mockRouter },
      ],
    });

    const result = TestBed.runInInjectionContext(() =>
      authGuard(mockRoute, mockState),
    );

    expect(typeof result).not.toBe('boolean');
    expect(mockRouter.createUrlTree).toHaveBeenCalledWith(['/entrada']);
  });

  it('returns true when session has a valid (non-null) token', () => {
    const mockSession = { token: 'valid-jwt-token', scopes: new Set(['dash:view']) };

    TestBed.configureTestingModule({
      providers: [{ provide: DASH_AUTH_SESSION, useValue: mockSession }],
    });

    const result = TestBed.runInInjectionContext(() =>
      authGuard(mockRoute, mockState),
    );

    expect(result).toBe(true);
  });
});

// ---------------------------------------------------------------------------
// dashGuard
// ---------------------------------------------------------------------------

describe('dashGuard', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('returns true when DASH_ACCESS_DECISION is null (Wave-0 bypass active)', () => {
    TestBed.configureTestingModule({
      providers: [{ provide: DASH_ACCESS_DECISION, useValue: null }],
    });

    const result = TestBed.runInInjectionContext(() =>
      dashGuard(mockRoute, mockState),
    );

    expect(result).toBe(true);
  });

  it('returns UrlTree (redirect to /entrada) when access.can("dash:view") is false', () => {
    const mockAccess = { can: jest.fn().mockReturnValue(false) };
    const mockRouter = {
      createUrlTree: jest.fn().mockReturnValue({ redirectTo: '/entrada' }),
    };

    TestBed.configureTestingModule({
      providers: [
        { provide: DASH_ACCESS_DECISION, useValue: mockAccess },
        { provide: Router, useValue: mockRouter },
      ],
    });

    const result = TestBed.runInInjectionContext(() =>
      dashGuard(mockRoute, mockState),
    );

    expect(typeof result).not.toBe('boolean');
    expect(mockAccess.can).toHaveBeenCalledWith('dash:view');
    expect(mockRouter.createUrlTree).toHaveBeenCalledWith(['/entrada']);
  });

  it('returns true when access.can("dash:view") is true', () => {
    const mockAccess = { can: jest.fn().mockReturnValue(true) };

    TestBed.configureTestingModule({
      providers: [{ provide: DASH_ACCESS_DECISION, useValue: mockAccess }],
    });

    const result = TestBed.runInInjectionContext(() =>
      dashGuard(mockRoute, mockState),
    );

    expect(result).toBe(true);
  });
});

// ---------------------------------------------------------------------------
// roleGuard
// ---------------------------------------------------------------------------

describe('roleGuard', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('returns true when DASH_ACCESS_DECISION is null (Wave-0 bypass active)', () => {
    TestBed.configureTestingModule({
      providers: [{ provide: DASH_ACCESS_DECISION, useValue: null }],
    });

    const result = TestBed.runInInjectionContext(() =>
      roleGuard(mockRoute, mockState),
    );

    expect(result).toBe(true);
  });

  it('returns UrlTree (redirect to /) when access.can("dash:onboarding.entry") is false', () => {
    const mockAccess = { can: jest.fn().mockReturnValue(false) };
    const mockRouter = {
      createUrlTree: jest.fn().mockReturnValue({ redirectTo: '/' }),
    };

    TestBed.configureTestingModule({
      providers: [
        { provide: DASH_ACCESS_DECISION, useValue: mockAccess },
        { provide: Router, useValue: mockRouter },
      ],
    });

    const result = TestBed.runInInjectionContext(() =>
      roleGuard(mockRoute, mockState),
    );

    expect(typeof result).not.toBe('boolean');
    expect(mockAccess.can).toHaveBeenCalledWith('dash:onboarding.entry');
    expect(mockRouter.createUrlTree).toHaveBeenCalledWith(['/']);
  });

  it('returns true when access.can("dash:onboarding.entry") is true', () => {
    const mockAccess = { can: jest.fn().mockReturnValue(true) };

    TestBed.configureTestingModule({
      providers: [{ provide: DASH_ACCESS_DECISION, useValue: mockAccess }],
    });

    const result = TestBed.runInInjectionContext(() =>
      roleGuard(mockRoute, mockState),
    );

    expect(result).toBe(true);
  });
});
