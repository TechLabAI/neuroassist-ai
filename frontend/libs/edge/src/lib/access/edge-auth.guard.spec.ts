// =============================================================================
// edge-auth.guard.spec.ts — FE-EDGE-015 DoD
//
// Covers:
//   1. Wave-0 bypass: EDGE_AUTH_SESSION is null → guard returns true
//      (mock/dev environment; auth enforcement deferred)
//   2. Session token is null (unauthenticated) → redirects to / (shell root)
//   3. Session token is present (authenticated) → returns true
// =============================================================================

import { TestBed } from '@angular/core/testing';
import {
  provideRouter,
  Router,
  UrlTree,
  ActivatedRouteSnapshot,
  RouterStateSnapshot,
} from '@angular/router';
import { edgeAuthGuard } from './edge-auth.guard';
import { EDGE_AUTH_SESSION } from './edge-access-tokens';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

const MOCK_ROUTE = {} as ActivatedRouteSnapshot;
const MOCK_STATE = { url: '/edge/device' } as RouterStateSnapshot;

function runGuard(): boolean | UrlTree {
  return TestBed.runInInjectionContext(() =>
    edgeAuthGuard(MOCK_ROUTE, MOCK_STATE),
  ) as boolean | UrlTree;
}

// ---------------------------------------------------------------------------
// Specs
// ---------------------------------------------------------------------------

describe('edgeAuthGuard — FE-EDGE-015', () => {
  describe('Wave-0 bypass: EDGE_AUTH_SESSION is null', () => {
    beforeEach(() => {
      TestBed.configureTestingModule({
        providers: [
          provideRouter([]),
          { provide: EDGE_AUTH_SESSION, useValue: null },
        ],
      });
    });

    it('returns true — mock/dev bypass active; no auth enforcement until session layer wired', () => {
      expect(runGuard()).toBe(true);
    });
  });

  describe('with AuthSession service provided', () => {
    describe('when session token is null (unauthenticated)', () => {
      beforeEach(() => {
        TestBed.configureTestingModule({
          providers: [
            provideRouter([]),
            { provide: EDGE_AUTH_SESSION, useValue: { token: null } },
          ],
        });
      });

      it('returns a UrlTree (not true/false)', () => {
        const result = runGuard();
        expect(result).toBeInstanceOf(UrlTree);
      });

      it('UrlTree serializes to / (shell root redirect — login or home)', () => {
        const result = runGuard();
        const router = TestBed.inject(Router);
        expect(router.serializeUrl(result as UrlTree)).toBe('/');
      });
    });

    describe('when session token is present (authenticated)', () => {
      beforeEach(() => {
        TestBed.configureTestingModule({
          providers: [
            provideRouter([]),
            { provide: EDGE_AUTH_SESSION, useValue: { token: 'valid-jwt-token' } },
          ],
        });
      });

      it('returns true — authenticated access granted', () => {
        expect(runGuard()).toBe(true);
      });
    });
  });
});
