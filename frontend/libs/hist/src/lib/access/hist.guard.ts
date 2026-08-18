import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { HIST_AUTH_SESSION, HIST_ACCESS_DECISION } from './hist-access-tokens';

// =============================================================================
// HIST Guards — SPEC-01 §3 (FE-HIST-002, FE-HIST-003)
//
// authGuard   : verifies that a valid session token is present before
//               allowing access to /historico. On failure: redirects to /.
// histGuard   : verifies hist:read scope; coordinators may also hold hist:read:all
//               for the chain-verification badge (SPEC-04 §2.3).
//
// Wave-0 bypass: tokens default to null (from factory). Guards pass through
// (return true) when tokens resolve to null — preserving dev/test/mock envs.
// To enforce in production, provide real implementations:
//   { provide: HIST_AUTH_SESSION, useExisting: RealAuthService }
//   { provide: HIST_ACCESS_DECISION, useExisting: RealAccessDecisionService }
// =============================================================================

/** authGuard — session presence check (FE-HIST-002). */
export const authGuard: CanActivateFn = () => {
  const session = inject(HIST_AUTH_SESSION, { optional: true });

  // Wave-0 bypass: not wired — allow through.
  if (session === null) {
    return true;
  }

  if (session.token === null) {
    return inject(Router).createUrlTree(['/']);
  }

  return true;
};

/** histGuard — hist:read scope check (FE-HIST-003). */
export const histGuard: CanActivateFn = () => {
  const access = inject(HIST_ACCESS_DECISION, { optional: true });

  // Wave-0 bypass: not wired — allow through.
  if (access === null) {
    return true;
  }

  if (!access.can('hist:read')) {
    return inject(Router).createUrlTree(['/']);
  }

  return true;
};
