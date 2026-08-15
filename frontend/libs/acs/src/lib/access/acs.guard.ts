import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { ACS_AUTH_SESSION, ACS_ACCESS_DECISION } from './acs-access-tokens';

// =============================================================================
// ACS Guards — SPEC-01 §3 (FE-ACS-004, FE-ACS-005, FE-ACS-007, FE-ACS-009)
//
// acsCoordinatorGuard   : verifies that a valid session token is present before
//                         allowing access to any /acs route. On failure: redirects to /.
// acsCaregiverWriteGuard: requires the acs.cuidador.write permission for
//                         caregiver create/update/deactivate operations.
// acsLinkWriteGuard     : requires the acs.vinculo.write permission for
//                         patient-caregiver link operations.
//
// Wave-0 bypass: tokens default to null until auth/access layer is promoted from
// Wave-0 stubs to real implementations. Guards pass through (return true) when
// the respective token resolves to null — preserving dev/test/mock environments.
//
// To enforce real security in production, provide the real services:
//   { provide: ACS_AUTH_SESSION,    useExisting: RealAuthService           }
//   { provide: ACS_ACCESS_DECISION, useExisting: RealAccessDecisionService }
// =============================================================================

// ---------------------------------------------------------------------------
// acsCoordinatorGuard — session presence check (FE-ACS-005 / FE-ACS-007)
//
// Protects all /acs routes. Verifies that the user has an active session
// (token !== null). Redirects to / (shell root / login) if unauthenticated.
// Wave-0 bypass: ACS_AUTH_SESSION defaults to null → guard passes through.
// ---------------------------------------------------------------------------
export const acsCoordinatorGuard: CanActivateFn = () => {
  const session = inject(ACS_AUTH_SESSION, { optional: true });

  // Wave-0 bypass: auth session service not wired — allow through.
  if (session === null) {
    return true;
  }

  // Authentication check: token must be present and non-null.
  if (session.token === null) {
    return inject(Router).createUrlTree(['/']);
  }

  return true;
};

// ---------------------------------------------------------------------------
// acsCaregiverWriteGuard — acs.cuidador.write permission check (FE-ACS-004)
//
// Protects caregiver create/update/deactivate routes.
// Redirects to / if the user lacks the permission.
// Wave-0 bypass: ACS_ACCESS_DECISION defaults to null → guard passes through.
// ---------------------------------------------------------------------------
export const acsCaregiverWriteGuard: CanActivateFn = () => {
  const access = inject(ACS_ACCESS_DECISION, { optional: true });

  // Wave-0 bypass: access decision service not wired — allow through.
  if (access === null) {
    return true;
  }

  // RBAC check: acs.cuidador.write permission required for caregiver write ops.
  if (!access.can('acs.cuidador.write')) {
    return inject(Router).createUrlTree(['/']);
  }

  return true;
};

// ---------------------------------------------------------------------------
// acsLinkWriteGuard — acs.vinculo.write permission check (FE-ACS-009)
//
// Protects patient-caregiver link create/revoke routes.
// Redirects to / if the user lacks the permission.
// Wave-0 bypass: ACS_ACCESS_DECISION defaults to null → guard passes through.
// ---------------------------------------------------------------------------
export const acsLinkWriteGuard: CanActivateFn = () => {
  const access = inject(ACS_ACCESS_DECISION, { optional: true });

  // Wave-0 bypass: access decision service not wired — allow through.
  if (access === null) {
    return true;
  }

  // RBAC check: acs.vinculo.write permission required for link write ops.
  if (!access.can('acs.vinculo.write')) {
    return inject(Router).createUrlTree(['/']);
  }

  return true;
};
