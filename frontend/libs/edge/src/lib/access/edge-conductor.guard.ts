import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';

// ---------------------------------------------------------------------------
// edgeConductorGuard — SPEC-01 §3 / SPEC-05 §3
// Requires the conductor entitlement for register/pair/unpair/retire operations.
// On failure: redirects to /edge/device (read-only detail) with a notice.
// Never produces a blank page (SPEC-01 §3).
//
// TODO[FE-EDGE-003]: wire to AuthSessionService.hasPermission('edge.device.manage')
// until then: pass-through (Layer 1 scaffold).
// ---------------------------------------------------------------------------

export const edgeConductorGuard: CanActivateFn = (_route, _state) => {
  const router = inject(Router);

  // TODO[FE-EDGE-003]: replace with real RBAC check
  // const session = inject(AuthSessionService);
  // if (!session.hasPermission('edge.device.manage')) {
  //   return router.createUrlTree(['/edge', 'device']);
  // }
  // return true;

  // Layer 1 scaffold: always allow (conductor session assumed in dev)
  return true;
};
