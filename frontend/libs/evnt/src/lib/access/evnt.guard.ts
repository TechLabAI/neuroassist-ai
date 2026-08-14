import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { EVNT_AUTH_SESSION, EVNT_ACCESS_DECISION } from './evnt-access-tokens';
import { EVNT_CLIENT_PORT } from '../api/evnt-client.port';
import { EvntClientMockAdapter } from '../api/evnt-client-mock.adapter';

// =============================================================================
// evntGuard — SPEC-01 §1 (FE-EVNT-020, INT-FE-EVNT-004)
//
// Guards:
//   GUARD-01: session.token === null → redirect to /login (unauthenticated)
//   GUARD-02: access.can('evnt:eventos:read') === false → redirect to / (no permission)
//
// Mock-mode bypass: when EVNT_CLIENT_PORT resolves to EvntClientMockAdapter
// (i.e. adapter='mock' in provideEvnt()), the guard returns true unconditionally.
// This allows dev servers and tests to function without auth infrastructure.
// Remove this bypass when auth stubs are promoted to real implementations.
//
// Wave-0 behaviour: EVNT_AUTH_SESSION and EVNT_ACCESS_DECISION tokens default to
// null (factory: () => null). Until the auth layer is wired in app.config.ts,
// the guard grants access only via the mock bypass above.
//
// Permissions enforced here (INT-FE-EVNT-004):
//   evnt:eventos:read  — required to view /eventos and /eventos/:eventoId
// Component-level permissions (evnt:confirm, evnt:routine:view) are enforced
// by EventDetailPageComponent and EventFeedPageComponent respectively.
// =============================================================================

export const evntGuard: CanActivateFn = () => {
  const port = inject(EVNT_CLIENT_PORT);

  // Dev bypass — mock adapter means no auth infrastructure is available.
  // This ensures /eventos is reachable during local development without backend.
  if (port instanceof EvntClientMockAdapter) {
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

  // GUARD-02: permission check.
  // Wave-0: EVNT_ACCESS_DECISION defaults to null (no decision service = no permission).
  const access = inject(EVNT_ACCESS_DECISION, { optional: true });
  const hasPermission = access !== null && access.can('evnt:eventos:read');
  if (!hasPermission) {
    void router.navigate(['/']);
    return false;
  }

  return true;
};
