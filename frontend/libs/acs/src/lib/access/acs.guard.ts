import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

// =============================================================================
// ACS Guards — SPEC-01 §3
// authGuard: session present (shared layer — referenced, not re-implemented)
// acsCoordinatorGuard: coordinator role only (cuidadora is redirected)
// acsCaregiverWriteGuard: acs.cuidador.write permission
// acsLinkWriteGuard: acs.vinculo.write permission
//
// TODO[FE-ACS-006]: wire to @neuroassist/shared/access-decision (auth session).
// Currently returns true (open gate for architecture stage).
// =============================================================================

export const acsCoordinatorGuard: CanActivateFn = () => {
  // TODO[FE-ACS-006]: check auth session role
  // const auth = inject(AuthSessionService);
  // if (!auth.hasRole('coordenador_clinica') && !auth.isBootstrapConductor()) {
  //   inject(Router).navigate(['/home']);
  //   return false;
  // }
  return true;
};

export const acsCaregiverWriteGuard: CanActivateFn = () => {
  // TODO[FE-ACS-007]: check acs.cuidador.write permission
  return true;
};

export const acsLinkWriteGuard: CanActivateFn = () => {
  // TODO[FE-ACS-009]: check acs.vinculo.write permission
  return true;
};
