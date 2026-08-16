import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { EVNT_AUTH_SESSION, EVNT_ACCESS_DECISION } from './evnt-access-tokens';
import { EVNT_E2E_BYPASS } from '../evnt.providers';

// =============================================================================
// evntGuard — SPEC-01 §1 (FE-EVNT-020, INT-FE-EVNT-004) — P6-E refactor
//
// Guards:
//   GUARD-01: session.token === null → redirect to /login (unauthenticated)
//   GUARD-02: access.can('evnt:eventos:read') === false → redirect to / (no permission)
//
// Mock/e2e bypass: EVNT_E2E_BYPASS token (InjectionToken<boolean>, default false).
//   Set to true in provideEvnt({ adapter: 'mock' }) — see evnt.providers.ts.
//   Replaces the previous class-instance check on EvntClientMockAdapter (P6-E).
//
// Wave-0 behaviour: EVNT_AUTH_SESSION and EVNT_ACCESS_DECISION tokens default to
// null (factory: () => null). Until the auth layer is wired in app.config.ts,
// the guard grants access only via the EVNT_E2E_BYPASS above.
//
// Permissions enforced here (INT-FE-EVNT-004):
//   evnt:eventos:read  — required to view /eventos and /eventos/:eventoId
// Component-level permissions (evnt:confirm, evnt:routine:view) are enforced
// by EventDetailPageComponent and EventFeedPageComponent respectively.
//
// GUARD-02 redirect: navigates to '/' (not '/login') when session is valid but
// permission is absent — FE-EVNT-020.
// =============================================================================

export const evntGuard: CanActivateFn = () => {
  // P6-E: e2e / mock-mode bypass via token — no more class-instance check
  const isBypass = inject(EVNT_E2E_BYPASS, { optional: true }) ?? false;
  if (isBypass) {
    return true;
  }

  const router = inject(Router);

  // GUARD-01: authentication check.
  // Wave-0: EVNT_AUTH_SESSION defaults to null (unauthenticated).
  const session = inject(EVNT_AUTH_SESSION, { optional: true });
  const isAuthenticated = session !== null && session.token !== null;
  if (!isAuthenticated) {
    void router.navigate(['/login']);
    return false;
  }

  // GUARD-02: permission check (FE-EVNT-020).
  // Wave-0: EVNT_ACCESS_DECISION defaults to null (no decision service = no permission).
  // On permission failure: redirect to '/' (not '/login') — session is valid.
  const access = inject(EVNT_ACCESS_DECISION, { optional: true });
  const hasPermission = access !== null && access.can('evnt:eventos:read');
  if (!hasPermission) {
    void router.navigate(['/']);
    return false;
  }

  return true;
};
