// =============================================================================
// edge-conductor.guard.spec.ts — FE-EDGE-015 DoD
//
// Covers:
//   1. Wave-0 bypass: EDGE_ACCESS_DECISION is null → guard returns true
//      (mock/dev environment; auth enforcement deferred)
//   2. User LACKS edge.device.manage → returns UrlTree to /edge/device
//      (redirects to read-only detail; never produces blank page — SPEC-01 §3)
//   3. User HAS edge.device.manage → returns true (access granted)
// =============================================================================

import { TestBed } from '@angular/core/testing';
import {
  provideRouter,
  Router,
  UrlTree,
  ActivatedRouteSnapshot,
  RouterStateSnapshot,
} from '@angular/router';
import { edgeConductorGuard } from './edge-conductor.guard';
import { EDGE_ACCESS_DECISION } from './edge-access-tokens';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

const MOCK_ROUTE = {} as ActivatedRouteSnapshot;
const MOCK_STATE = { url: '/edge/pairing' } as RouterStateSnapshot;

function runGuard(): boolean | UrlTree {
  return TestBed.runInInjectionContext(() =>
    edgeConductorGuard(MOCK_ROUTE, MOCK_STATE),
  ) as boolean | UrlTree;
}

// ---------------------------------------------------------------------------
// Specs
// ---------------------------------------------------------------------------

describe('edgeConductorGuard — FE-EDGE-015', () => {
  describe('Wave-0 bypass: EDGE_ACCESS_DECISION is null', () => {
    beforeEach(() => {
      TestBed.configureTestingModule({
        providers: [
          provideRouter([]),
          { provide: EDGE_ACCESS_DECISION, useValue: null },
        ],
      });
    });

    it('returns true — mock/dev bypass active; no RBAC enforcement until auth layer wired', () => {
      expect(runGuard()).toBe(true);
    });
  });

  describe('with AccessDecision service provided', () => {
    describe('when user LACKS edge.device.manage permission', () => {
      beforeEach(() => {
        TestBed.configureTestingModule({
          providers: [
            provideRouter([]),
            {
              provide: EDGE_ACCESS_DECISION,
              useValue: { can: () => false },
            },
          ],
        });
      });

      it('returns a UrlTree (not true/false)', () => {
        const result = runGuard();
        expect(result).toBeInstanceOf(UrlTree);
      });

      it('UrlTree serializes to /edge/device (read-only detail redirect)', () => {
        const result = runGuard();
        const router = TestBed.inject(Router);
        expect(router.serializeUrl(result as UrlTree)).toBe('/edge/device');
      });
    });

    describe('when user HAS edge.device.manage permission', () => {
      beforeEach(() => {
        TestBed.configureTestingModule({
          providers: [
            provideRouter([]),
            {
              provide: EDGE_ACCESS_DECISION,
              useValue: { can: (perm: string) => perm === 'edge.device.manage' },
            },
          ],
        });
      });

      it('returns true — conductor access granted', () => {
        expect(runGuard()).toBe(true);
      });
    });

    describe('permission specificity: only edge.device.manage grants access', () => {
      it('returns UrlTree when a different permission is held (not edge.device.manage)', () => {
        TestBed.configureTestingModule({
          providers: [
            provideRouter([]),
            {
              provide: EDGE_ACCESS_DECISION,
              useValue: { can: (perm: string) => perm === 'edge.device.read' },
            },
          ],
        });

        const result = runGuard();
        expect(result).toBeInstanceOf(UrlTree);
      });
    });
  });
});
