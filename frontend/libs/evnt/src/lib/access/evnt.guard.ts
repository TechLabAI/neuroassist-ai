import { CanActivateFn } from '@angular/router';

// =============================================================================
// evntGuard — SPEC-01 (route guard for EVNT feature)
// Guards:
//   GUARD-01: authGuard fails → redirect to login (FE-EVNT-021)
//   GUARD-02: evntGuard fails → redirect to /eventos blocked (FE-EVNT-022)
//   GUARD-03: /eventos/:eventoId not in tenant → redirect without revealing (FE-EVNT-023)
//   GUARD-04: 403/tenant-mismatch → redirect (FE-EVNT-024)
//
// TODO[FE-EVNT-021..024]: implement real auth checks using
//   @neuroassist/shared/auth-session (session validity)
//   @neuroassist/shared/access-decision (permission: evnt:eventos:read)
// =============================================================================

/** Route guard for all EVNT routes: /eventos, /eventos/:eventoId.
 * Stub implementation — always grants access in dev.
 * Implement FE-EVNT-021..024 for the production guard. */
export const evntGuard: CanActivateFn = () => {
  // TODO[FE-EVNT-021]: inject AuthSession, return false + redirect to /login if not authenticated
  // TODO[FE-EVNT-022]: inject AccessDecision, check 'evnt:eventos:read' permission
  return true;
};
