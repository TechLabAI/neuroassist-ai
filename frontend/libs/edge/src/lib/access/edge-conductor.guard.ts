import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { EDGE_ACCESS_DECISION } from './edge-access-tokens';

// ---------------------------------------------------------------------------
// edgeConductorGuard — SPEC-01 §3 / SPEC-05 §3 (FE-EDGE-015)
//
// Requires the conductor entitlement (edge.device.manage) for
// register/pair/unpair/retire operations.
// On failure: redirects to /edge/device (read-only detail).
// Never produces a blank page (SPEC-01 §3).
//
// Wave-0 bypass: EDGE_ACCESS_DECISION defaults to null when auth layer is not
// yet wired (factory: () => null). Guard passes through in dev and test
// environments until the access-decision service is promoted.
//
// To enforce RBAC in production, provide the real service:
//   { provide: EDGE_ACCESS_DECISION, useExisting: RealAccessDecisionService }
// ---------------------------------------------------------------------------

export const edgeConductorGuard: CanActivateFn = () => {
  const access = inject(EDGE_ACCESS_DECISION, { optional: true });

  // Wave-0 bypass: access decision service not wired — allow through.
  if (access === null) {
    return true;
  }

  // RBAC check: conductor permission required for manage operations.
  if (!access.can('edge.device.manage')) {
    return inject(Router).createUrlTree(['/edge', 'device']);
  }

  return true;
};
