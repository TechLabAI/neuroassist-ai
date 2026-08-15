import { InjectionToken } from '@angular/core';

// =============================================================================
// ACS access injection tokens (Wave-0 — FE-ACS-004 / FE-ACS-005 / FE-ACS-007 / FE-ACS-009)
//
// Angular requires a class or InjectionToken to inject; TypeScript interfaces
// cannot be injected directly. These tokens provide DI-compatible handles that
// default to null until the shared auth/access infrastructure is promoted from
// Wave-0 stubs to real implementations.
//
// The interfaces below mirror the contracts of the Wave-0 shared stubs:
//   @neuroassist/shared/auth-session    → AuthSession
//   @neuroassist/shared/access-decision → AccessDecision
//
// Note: shared libs are non-buildable stubs; importing them from a buildable
// lib (acs) triggers Angular ngtypecheck outside rootDir. Interfaces are
// re-declared locally (structurally equivalent) to keep the build green.
// Replace local declarations with real imports when shared libs are promoted
// to buildable Angular libraries.
//
// Replace the factory defaults in app.config.ts providers to enable real enforcement:
//   { provide: ACS_AUTH_SESSION,    useExisting: RealAuthService           }
//   { provide: ACS_ACCESS_DECISION, useExisting: RealAccessDecisionService }
// =============================================================================

/** Local alias — structurally equivalent to @neuroassist/shared/auth-session AuthSession. */
export interface AcsAuthSession { readonly token: string | null; }

/** Local alias — structurally equivalent to @neuroassist/shared/access-decision AccessDecision. */
export interface AcsAccessDecision { can(permission: string): boolean; }

/** DI token for AuthSession — used by acsCoordinatorGuard to verify session validity.
 * Wave-0: factory returns null (mock bypass active; auth enforcement deferred). */
export const ACS_AUTH_SESSION = new InjectionToken<AcsAuthSession | null>(
  'ACS_AUTH_SESSION',
  { providedIn: 'root', factory: () => null },
);

/** DI token for AccessDecision — used by acsCaregiverWriteGuard and acsLinkWriteGuard
 * to check RBAC permissions on write routes.
 * Wave-0: factory returns null (permissions not enforced; mock bypass active). */
export const ACS_ACCESS_DECISION = new InjectionToken<AcsAccessDecision | null>(
  'ACS_ACCESS_DECISION',
  { providedIn: 'root', factory: () => null },
);
