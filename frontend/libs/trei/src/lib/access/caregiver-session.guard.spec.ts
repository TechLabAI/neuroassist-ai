// =============================================================================
// caregiver-session.guard.spec.ts — TREI-FE-022 DoD
//
// caregiverSessionGuard:
//   1. Token present (session.token !== null)  → returns true (access granted)
//   2. Token null (no session)                 → returns UrlTree to /login
//      with ?returnUrl preserving the attempted URL
//   3. Fail-closed default: when TREI_AUTH_SESSION is not overridden (factory
//      default { token: null }), guard denies access — no Wave-0 bypass.
// =============================================================================

import { TestBed } from '@angular/core/testing';
import {
  provideRouter,
  Router,
  UrlTree,
  ActivatedRouteSnapshot,
  RouterStateSnapshot,
} from '@angular/router';
import { caregiverSessionGuard } from './caregiver-session.guard';
import { TREI_AUTH_SESSION } from './trei-access-tokens';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

const MOCK_ROUTE = {} as ActivatedRouteSnapshot;

function mockState(url = '/treinamento/sessao'): RouterStateSnapshot {
  return { url } as RouterStateSnapshot;
}

function runGuard(url = '/treinamento/sessao'): boolean | UrlTree {
  return TestBed.runInInjectionContext(() =>
    caregiverSessionGuard(MOCK_ROUTE, mockState(url)),
  ) as boolean | UrlTree;
}

// ---------------------------------------------------------------------------
// caregiverSessionGuard — session presence check (TREI-FE-022)
// ---------------------------------------------------------------------------

describe('caregiverSessionGuard — TREI-FE-022', () => {
  describe('when caregiver has an active session (token present)', () => {
    beforeEach(() => {
      TestBed.configureTestingModule({
        providers: [
          provideRouter([]),
          { provide: TREI_AUTH_SESSION, useValue: { token: 'valid-jwt-token' } },
        ],
      });
    });

    it('returns true — access granted to /treinamento routes', () => {
      expect(runGuard()).toBe(true);
    });
  });

  describe('when caregiver has NO session (token: null)', () => {
    beforeEach(() => {
      TestBed.configureTestingModule({
        providers: [
          provideRouter([]),
          { provide: TREI_AUTH_SESSION, useValue: { token: null } },
        ],
      });
    });

    it('returns a UrlTree (not true/false)', () => {
      const result = runGuard();
      expect(result).toBeInstanceOf(UrlTree);
    });

    it('UrlTree points to /login', () => {
      const result = runGuard();
      const router = TestBed.inject(Router);
      const serialized = router.serializeUrl(result as UrlTree);
      expect(serialized).toContain('/login');
    });

    it('UrlTree carries returnUrl query param with the attempted URL', () => {
      const attemptedUrl = '/treinamento/sessao';
      const result = runGuard(attemptedUrl);
      const router = TestBed.inject(Router);
      const serialized = router.serializeUrl(result as UrlTree);
      expect(serialized).toContain('returnUrl=');
      expect(serialized).toContain(encodeURIComponent(attemptedUrl));
    });

    it('never redirects to /dashboard or /treinamento/sessao — always /login', () => {
      const result = runGuard();
      const router = TestBed.inject(Router);
      const serialized = router.serializeUrl(result as UrlTree);
      expect(serialized).not.toContain('/dashboard');
      expect(serialized).not.toContain('/treinamento');
    });
  });

  describe('fail-closed default — factory default { token: null }', () => {
    beforeEach(() => {
      // No override: TREI_AUTH_SESSION uses its factory default { token: null }
      TestBed.configureTestingModule({
        providers: [provideRouter([])],
      });
    });

    it('redirects to /login when token default is null — no Wave-0 bypass', () => {
      const result = runGuard();
      const router = TestBed.inject(Router);
      const serialized = router.serializeUrl(result as UrlTree);
      expect(serialized).toContain('/login');
    });
  });
});
