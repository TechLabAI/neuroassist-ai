// =============================================================================
// acs.guard.spec.ts — FE-ACS-004 / FE-ACS-005 / FE-ACS-007 / FE-ACS-009 DoD
//
// Covers all 3 ACS guards using the Wave-0 bypass pattern:
//
// acsCoordinatorGuard:
//   1. Wave-0 bypass: ACS_AUTH_SESSION is null → returns true (dev/mock bypass)
//   2. Session token is null (unauthenticated) → redirects to / (UrlTree)
//   3. Session token is present → returns true (access granted)
//
// acsCaregiverWriteGuard:
//   1. Wave-0 bypass: ACS_ACCESS_DECISION is null → returns true
//   2. User lacks acs.cuidador.write → redirects to / (UrlTree)
//   3. User has acs.cuidador.write → returns true
//
// acsLinkWriteGuard:
//   1. Wave-0 bypass: ACS_ACCESS_DECISION is null → returns true
//   2. User lacks acs.vinculo.write → redirects to / (UrlTree)
//   3. User has acs.vinculo.write → returns true
// =============================================================================

import { TestBed } from '@angular/core/testing';
import {
  provideRouter,
  Router,
  UrlTree,
  ActivatedRouteSnapshot,
  RouterStateSnapshot,
} from '@angular/router';
import {
  acsCoordinatorGuard,
  acsCaregiverWriteGuard,
  acsLinkWriteGuard,
} from './acs.guard';
import { ACS_AUTH_SESSION, ACS_ACCESS_DECISION } from './acs-access-tokens';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

const MOCK_ROUTE = {} as ActivatedRouteSnapshot;
const MOCK_STATE = { url: '/acs' } as RouterStateSnapshot;

function runCoordinatorGuard(): boolean | UrlTree {
  return TestBed.runInInjectionContext(() =>
    acsCoordinatorGuard(MOCK_ROUTE, MOCK_STATE),
  ) as boolean | UrlTree;
}

function runCaregiverWriteGuard(): boolean | UrlTree {
  return TestBed.runInInjectionContext(() =>
    acsCaregiverWriteGuard(MOCK_ROUTE, MOCK_STATE),
  ) as boolean | UrlTree;
}

function runLinkWriteGuard(): boolean | UrlTree {
  return TestBed.runInInjectionContext(() =>
    acsLinkWriteGuard(MOCK_ROUTE, MOCK_STATE),
  ) as boolean | UrlTree;
}

// ---------------------------------------------------------------------------
// acsCoordinatorGuard — session presence check
// ---------------------------------------------------------------------------

describe('acsCoordinatorGuard — FE-ACS-005 / FE-ACS-007', () => {
  describe('Wave-0 bypass: ACS_AUTH_SESSION is null', () => {
    beforeEach(() => {
      TestBed.configureTestingModule({
        providers: [
          provideRouter([]),
          { provide: ACS_AUTH_SESSION, useValue: null },
        ],
      });
    });

    it('returns true — mock/dev bypass active; no auth enforcement until session layer wired', () => {
      expect(runCoordinatorGuard()).toBe(true);
    });
  });

  describe('with AuthSession service provided', () => {
    describe('when session token is null (unauthenticated)', () => {
      beforeEach(() => {
        TestBed.configureTestingModule({
          providers: [
            provideRouter([]),
            { provide: ACS_AUTH_SESSION, useValue: { token: null } },
          ],
        });
      });

      it('returns a UrlTree (not true/false)', () => {
        const result = runCoordinatorGuard();
        expect(result).toBeInstanceOf(UrlTree);
      });

      it('UrlTree serializes to / (shell root redirect — login or home)', () => {
        const result = runCoordinatorGuard();
        const router = TestBed.inject(Router);
        expect(router.serializeUrl(result as UrlTree)).toBe('/');
      });
    });

    describe('when session token is present (authenticated)', () => {
      beforeEach(() => {
        TestBed.configureTestingModule({
          providers: [
            provideRouter([]),
            { provide: ACS_AUTH_SESSION, useValue: { token: 'valid-jwt-token' } },
          ],
        });
      });

      it('returns true — authenticated access granted', () => {
        expect(runCoordinatorGuard()).toBe(true);
      });
    });
  });
});

// ---------------------------------------------------------------------------
// acsCaregiverWriteGuard — acs.cuidador.write permission check
// ---------------------------------------------------------------------------

describe('acsCaregiverWriteGuard — FE-ACS-004', () => {
  describe('Wave-0 bypass: ACS_ACCESS_DECISION is null', () => {
    beforeEach(() => {
      TestBed.configureTestingModule({
        providers: [
          provideRouter([]),
          { provide: ACS_ACCESS_DECISION, useValue: null },
        ],
      });
    });

    it('returns true — mock/dev bypass active; no RBAC enforcement until auth layer wired', () => {
      expect(runCaregiverWriteGuard()).toBe(true);
    });
  });

  describe('with AccessDecision service provided', () => {
    describe('when user LACKS acs.cuidador.write permission', () => {
      beforeEach(() => {
        TestBed.configureTestingModule({
          providers: [
            provideRouter([]),
            { provide: ACS_ACCESS_DECISION, useValue: { can: () => false } },
          ],
        });
      });

      it('returns a UrlTree (not true/false)', () => {
        const result = runCaregiverWriteGuard();
        expect(result).toBeInstanceOf(UrlTree);
      });

      it('UrlTree serializes to / (shell root redirect)', () => {
        const result = runCaregiverWriteGuard();
        const router = TestBed.inject(Router);
        expect(router.serializeUrl(result as UrlTree)).toBe('/');
      });
    });

    describe('when user HAS acs.cuidador.write permission', () => {
      beforeEach(() => {
        TestBed.configureTestingModule({
          providers: [
            provideRouter([]),
            {
              provide: ACS_ACCESS_DECISION,
              useValue: { can: (perm: string) => perm === 'acs.cuidador.write' },
            },
          ],
        });
      });

      it('returns true — caregiver write access granted', () => {
        expect(runCaregiverWriteGuard()).toBe(true);
      });
    });

    describe('permission specificity: only acs.cuidador.write grants access', () => {
      it('returns UrlTree when a different permission is held (not acs.cuidador.write)', () => {
        TestBed.configureTestingModule({
          providers: [
            provideRouter([]),
            {
              provide: ACS_ACCESS_DECISION,
              useValue: { can: (perm: string) => perm === 'acs.cuidador.read' },
            },
          ],
        });

        const result = runCaregiverWriteGuard();
        expect(result).toBeInstanceOf(UrlTree);
      });
    });
  });
});

// ---------------------------------------------------------------------------
// acsLinkWriteGuard — acs.vinculo.write permission check
// ---------------------------------------------------------------------------

describe('acsLinkWriteGuard — FE-ACS-009', () => {
  describe('Wave-0 bypass: ACS_ACCESS_DECISION is null', () => {
    beforeEach(() => {
      TestBed.configureTestingModule({
        providers: [
          provideRouter([]),
          { provide: ACS_ACCESS_DECISION, useValue: null },
        ],
      });
    });

    it('returns true — mock/dev bypass active; no RBAC enforcement until auth layer wired', () => {
      expect(runLinkWriteGuard()).toBe(true);
    });
  });

  describe('with AccessDecision service provided', () => {
    describe('when user LACKS acs.vinculo.write permission', () => {
      beforeEach(() => {
        TestBed.configureTestingModule({
          providers: [
            provideRouter([]),
            { provide: ACS_ACCESS_DECISION, useValue: { can: () => false } },
          ],
        });
      });

      it('returns a UrlTree (not true/false)', () => {
        const result = runLinkWriteGuard();
        expect(result).toBeInstanceOf(UrlTree);
      });

      it('UrlTree serializes to / (shell root redirect)', () => {
        const result = runLinkWriteGuard();
        const router = TestBed.inject(Router);
        expect(router.serializeUrl(result as UrlTree)).toBe('/');
      });
    });

    describe('when user HAS acs.vinculo.write permission', () => {
      beforeEach(() => {
        TestBed.configureTestingModule({
          providers: [
            provideRouter([]),
            {
              provide: ACS_ACCESS_DECISION,
              useValue: { can: (perm: string) => perm === 'acs.vinculo.write' },
            },
          ],
        });
      });

      it('returns true — link write access granted', () => {
        expect(runLinkWriteGuard()).toBe(true);
      });
    });

    describe('permission specificity: only acs.vinculo.write grants access', () => {
      it('returns UrlTree when a different permission is held (not acs.vinculo.write)', () => {
        TestBed.configureTestingModule({
          providers: [
            provideRouter([]),
            {
              provide: ACS_ACCESS_DECISION,
              useValue: { can: (perm: string) => perm === 'acs.vinculo.read' },
            },
          ],
        });

        const result = runLinkWriteGuard();
        expect(result).toBeInstanceOf(UrlTree);
      });
    });
  });
});
