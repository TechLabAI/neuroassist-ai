import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { DASH_AUTH_SESSION, DASH_ACCESS_DECISION } from './dash-access-tokens';

// =============================================================================
// DASH Guards — SPEC-01 §3 (FE-DASH-002, FE-DASH-003)
//
// authGuard : verifies that a valid session token is present before
//             allowing access to / and /notificacoes. On failure: redirects to /entrada.
// dashGuard : verifies dash:view scope.
// roleGuard : hides Onboarding link for cuidadora role (nav-level only).
//
// Wave-0 bypass: tokens default to null (from factory). Guards pass through
// (return true) when tokens resolve to null — preserving dev/test/mock envs.
// To enforce in production, provide real implementations:
//   { provide: DASH_AUTH_SESSION, useExisting: RealAuthService }
//   { provide: DASH_ACCESS_DECISION, useExisting: RealAccessDecisionService }
// =============================================================================

/** authGuard — session presence check (FE-DASH-002). */
export const authGuard: CanActivateFn = () => {
  const session = inject(DASH_AUTH_SESSION, { optional: true });

  // Wave-0 bypass: not wired — allow through.
  if (session === null) {
    return true;
  }

  if (session.token === null) {
    return inject(Router).createUrlTree(['/entrada']);
  }

  return true;
};

/** dashGuard — dash:view scope check (FE-DASH-003). */
export const dashGuard: CanActivateFn = () => {
  const access = inject(DASH_ACCESS_DECISION, { optional: true });

  // Wave-0 bypass: not wired — allow through.
  if (access === null) {
    return true;
  }

  if (!access.can('dash:view')) {
    return inject(Router).createUrlTree(['/entrada']);
  }

  return true;
};

/** roleGuard — nav-level Onboarding link visibility (FE-DASH-010). */
export const roleGuard: CanActivateFn = () => {
  const access = inject(DASH_ACCESS_DECISION, { optional: true });

  // Wave-0 bypass: allow through.
  if (access === null) {
    return true;
  }

  // Only coordenador_clinica can access onboarding configuration (SPEC-01 §3).
  if (!access.can('dash:onboarding.entry')) {
    return inject(Router).createUrlTree(['/']);
  }

  return true;
};
