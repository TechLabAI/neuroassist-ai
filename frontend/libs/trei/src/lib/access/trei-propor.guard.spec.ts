// =============================================================================
// trei-propor.guard.spec.ts — TREI-FE-023 / G1 DoD (treiProporGuard)
//
// treiProporGuard (G1 invariant):
//   1. User holds `treinamento.no.propor` → returns true (proposal route open)
//   2. User lacks `treinamento.no.propor` → returns UrlTree to /treinamento/sessao
//      (NOT /dashboard, NOT false — G1: session route stays reachable)
//   3. Fail-closed default: TREI_ACCESS_DECISION factory { can: () => false }
//      → redirects to /treinamento/sessao without Wave-0 bypass.
//   4. Redirect target invariant: ALWAYS /treinamento/sessao on failure.
//
// G1 contract (SPEC-01 §3 / SPEC-01 §5):
//   "IF the treiProporGuard finds the JWT lacks scope treinamento.no.propor,
//    THEN THE SYSTEM SHALL keep the review surface reachable and SHALL NOT
//    open the proposal materialize/edit route."
//
// ADR-007: treinamento.no.aprovar.paciente is NEVER a guard here — patient
// approval is CONV's domain (INV-1).
// =============================================================================

import { TestBed } from '@angular/core/testing';
import {
  provideRouter,
  Router,
  UrlTree,
  ActivatedRouteSnapshot,
  RouterStateSnapshot,
} from '@angular/router';
import { treiProporGuard } from './trei-propor.guard';
import { TREI_ACCESS_DECISION } from './trei-access-tokens';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

const MOCK_ROUTE = {} as ActivatedRouteSnapshot;
const MOCK_STATE = { url: '/treinamento/sessao/propostas' } as RouterStateSnapshot;

function runGuard(authorities: string[]): boolean | UrlTree {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      {
        provide: TREI_ACCESS_DECISION,
        useValue: { can: (authority: string) => authorities.includes(authority) },
      },
    ],
  });
  return TestBed.runInInjectionContext(() =>
    treiProporGuard(MOCK_ROUTE, MOCK_STATE),
  ) as boolean | UrlTree;
}

// ---------------------------------------------------------------------------
// treiProporGuard — G1 invariant: redirect to /treinamento/sessao, never 403
// ---------------------------------------------------------------------------

describe('treiProporGuard — TREI-FE-023 / G1 invariant', () => {
  describe('when user holds treinamento.no.propor', () => {
    it('returns true — proposal route accessible (can create/edit proposals)', () => {
      const result = runGuard(['treinamento.sessao.conduzir', 'treinamento.no.propor']);
      expect(result).toBe(true);
    });
  });

  describe('when user LACKS treinamento.no.propor (G1 critical)', () => {
    it('returns a UrlTree (not true, not false)', () => {
      const result = runGuard(['treinamento.sessao.conduzir']);
      expect(result).toBeInstanceOf(UrlTree);
    });

    it('G1: UrlTree points to /treinamento/sessao — session remains reachable', () => {
      const result = runGuard(['treinamento.sessao.conduzir']);
      const router = TestBed.inject(Router);
      const serialized = router.serializeUrl(result as UrlTree);
      expect(serialized).toBe('/treinamento/sessao');
    });

    it('G1 invariant: NEVER redirects to /dashboard', () => {
      const result = runGuard(['treinamento.sessao.conduzir']);
      const router = TestBed.inject(Router);
      const serialized = router.serializeUrl(result as UrlTree);
      expect(serialized).not.toBe('/dashboard');
      expect(serialized).not.toContain('dashboard');
    });

    it('G1 invariant: NEVER returns false (which would result in a 403)', () => {
      const result = runGuard(['treinamento.sessao.conduzir']);
      expect(result).not.toBe(false);
    });

    it('G1 invariant: NEVER redirects to /login', () => {
      const result = runGuard(['treinamento.sessao.conduzir']);
      const router = TestBed.inject(Router);
      const serialized = router.serializeUrl(result as UrlTree);
      expect(serialized).not.toContain('login');
    });
  });

  describe('fail-closed default — factory default { can: () => false }', () => {
    beforeEach(() => {
      TestBed.configureTestingModule({
        providers: [provideRouter([])],
      });
    });

    it('redirects to /treinamento/sessao by default — no Wave-0 bypass', () => {
      const result = TestBed.runInInjectionContext(() =>
        treiProporGuard(MOCK_ROUTE, MOCK_STATE),
      ) as boolean | UrlTree;
      const router = TestBed.inject(Router);
      const serialized = router.serializeUrl(result as UrlTree);
      expect(serialized).toBe('/treinamento/sessao');
    });
  });

  describe('when user has no authorities at all', () => {
    it('returns UrlTree to /treinamento/sessao — same G1 behavior', () => {
      const result = runGuard([]);
      const router = TestBed.inject(Router);
      const serialized = router.serializeUrl(result as UrlTree);
      expect(serialized).toBe('/treinamento/sessao');
    });
  });

  describe('INV-1 guard (ADR-007): patient authority does not bypass guard', () => {
    it('treinamento.no.propor alone is sufficient — no patient authority required', () => {
      // ADR-007: patient-side approval is CONV's domain, never TREI's.
      const result = runGuard(['treinamento.sessao.conduzir', 'treinamento.no.propor']);
      expect(result).toBe(true);
    });

    it('holding patient authority alone does NOT grant access to the proposal route', () => {
      // INV-1: treinamento.no.aprovar.paciente is a CONV authority, never a TREI guard.
      const result = runGuard([
        'treinamento.sessao.conduzir',
        'treinamento.no.aprovar.paciente', // patient authority — must NOT grant access
      ]);
      const router = TestBed.inject(Router);
      const serialized = router.serializeUrl(result as UrlTree);
      expect(serialized).toBe('/treinamento/sessao');
    });
  });
});
