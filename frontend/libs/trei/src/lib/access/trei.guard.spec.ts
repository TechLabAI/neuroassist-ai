// =============================================================================
// trei.guard.spec.ts — TREI-FE-023 DoD (treiGuard)
//
// treiGuard:
//   1. User holds `treinamento.sessao.conduzir` → returns true (access granted)
//   2. User lacks `treinamento.sessao.conduzir` → returns UrlTree to /dashboard
//   3. Fail-closed default: TREI_ACCESS_DECISION factory { can: () => false }
//      → guard denies access without Wave-0 bypass.
//   4. Permission specificity: only the exact authority grants access.
// =============================================================================

import { TestBed } from '@angular/core/testing';
import {
  provideRouter,
  Router,
  UrlTree,
  ActivatedRouteSnapshot,
  RouterStateSnapshot,
} from '@angular/router';
import { treiGuard } from './trei.guard';
import { TREI_ACCESS_DECISION } from './trei-access-tokens';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

const MOCK_ROUTE = {} as ActivatedRouteSnapshot;
const MOCK_STATE = { url: '/treinamento' } as RouterStateSnapshot;

function runGuard(): boolean | UrlTree {
  return TestBed.runInInjectionContext(() =>
    treiGuard(MOCK_ROUTE, MOCK_STATE),
  ) as boolean | UrlTree;
}

// ---------------------------------------------------------------------------
// treiGuard — treinamento.sessao.conduzir authority check (TREI-FE-023)
// ---------------------------------------------------------------------------

describe('treiGuard — TREI-FE-023', () => {
  describe('when user holds treinamento.sessao.conduzir', () => {
    beforeEach(() => {
      TestBed.configureTestingModule({
        providers: [
          provideRouter([]),
          {
            provide: TREI_ACCESS_DECISION,
            useValue: { can: (authority: string) => authority === 'treinamento.sessao.conduzir' },
          },
        ],
      });
    });

    it('returns true — TREI module access granted', () => {
      expect(runGuard()).toBe(true);
    });
  });

  describe('when user LACKS treinamento.sessao.conduzir', () => {
    beforeEach(() => {
      TestBed.configureTestingModule({
        providers: [
          provideRouter([]),
          { provide: TREI_ACCESS_DECISION, useValue: { can: () => false } },
        ],
      });
    });

    it('returns a UrlTree (not true/false)', () => {
      const result = runGuard();
      expect(result).toBeInstanceOf(UrlTree);
    });

    it('UrlTree points to /dashboard', () => {
      const result = runGuard();
      const router = TestBed.inject(Router);
      const serialized = router.serializeUrl(result as UrlTree);
      expect(serialized).toBe('/dashboard');
    });

    it('never redirects to /login or /treinamento/sessao', () => {
      const result = runGuard();
      const router = TestBed.inject(Router);
      const serialized = router.serializeUrl(result as UrlTree);
      expect(serialized).not.toContain('/login');
      expect(serialized).not.toContain('/treinamento');
    });
  });

  describe('fail-closed default — factory default { can: () => false }', () => {
    beforeEach(() => {
      // No override: TREI_ACCESS_DECISION uses its factory default { can: () => false }
      TestBed.configureTestingModule({
        providers: [provideRouter([])],
      });
    });

    it('redirects to /dashboard when default is no-permissions — no Wave-0 bypass', () => {
      const result = runGuard();
      const router = TestBed.inject(Router);
      const serialized = router.serializeUrl(result as UrlTree);
      expect(serialized).toBe('/dashboard');
    });
  });

  describe('permission specificity', () => {
    it('only treinamento.sessao.conduzir grants access — different authority is denied', () => {
      TestBed.configureTestingModule({
        providers: [
          provideRouter([]),
          {
            provide: TREI_ACCESS_DECISION,
            // holds a different authority, not treinamento.sessao.conduzir
            useValue: { can: (authority: string) => authority === 'treinamento.sessao.read' },
          },
        ],
      });
      const result = runGuard();
      expect(result).toBeInstanceOf(UrlTree);
    });
  });
});
