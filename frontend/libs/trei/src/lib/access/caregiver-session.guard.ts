import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { TREI_AUTH_SESSION } from './trei-access-tokens';

// =============================================================================
// caregiverSessionGuard — TREI-FE-022
//
// Verifies that an active, non-null caregiver JWT session is present before
// allowing access to any /treinamento route.
//
// On failure: redirects to /login with ?returnUrl so the caregiver lands back
// on the attempted route after authenticating. This guard runs first in the
// canActivate chain (parent route — SPEC-01 §3 table).
//
// Authorization design:
//   - Reads TREI_AUTH_SESSION.token (fail-closed default: null).
//   - No Wave-0 bypass — token: null → redirect to /login on every navigation
//     until a real session provider is wired.
//   - TREI implements no login surface of its own; the caregiver login is the
//     shared shell login at /login (shared ADR-006 / SPEC-01 §3).
//   - TREI_AUTH_SESSION factory default is { token: null } — secure by default.
//     To wire the real store: { provide: TREI_AUTH_SESSION, useFactory: (s:
//     AuthSessionStore) => ({ token: s.token() }), deps: [AuthSessionStore] }
// =============================================================================

/**
 * Protects all /treinamento routes — verifies active caregiver session.
 * Redirects to /login (with returnUrl) when no valid session is present.
 */
export const caregiverSessionGuard: CanActivateFn = (_route, state) => {
  const session = inject(TREI_AUTH_SESSION);
  const router = inject(Router);

  // No Wave-0 bypass: token: null means no session → fail-closed redirect.
  return session.token !== null
    ? true
    : router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};
