import { InjectionToken } from '@angular/core';

// =============================================================================
// EDGE access injection tokens (Wave-0 — FE-EDGE-015)
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
// lib (edge) triggers Angular ngtypecheck outside rootDir. Interfaces are
// re-declared locally (structurally equivalent) to keep the build green.
// Replace local declarations with real imports when shared libs are promoted
// to buildable Angular libraries.
//
// Replace the factory defaults in app.config.ts providers to enable real enforcement:
//   { provide: EDGE_AUTH_SESSION,    useExisting: RealAuthService           }
//   { provide: EDGE_ACCESS_DECISION, useExisting: RealAccessDecisionService }
// =============================================================================

/** Local alias — structurally equivalent to @neuroassist/shared/auth-session AuthSession. */
export interface EdgeAuthSession { readonly token: string | null; }

/** Local alias — structurally equivalent to @neuroassist/shared/access-decision AccessDecision. */
export interface EdgeAccessDecision { can(permission: string): boolean; }

/** DI token for AuthSession — used by edgeAuthGuard to verify session validity.
 * Wave-0: factory returns null (mock bypass active; auth enforcement deferred). */
export const EDGE_AUTH_SESSION = new InjectionToken<EdgeAuthSession | null>(
  'EDGE_AUTH_SESSION',
  { providedIn: 'root', factory: () => null },
);

/** DI token for AccessDecision — used by edgeConductorGuard to check edge.device.manage.
 * Wave-0: factory returns null (permissions not enforced; mock bypass active). */
export const EDGE_ACCESS_DECISION = new InjectionToken<EdgeAccessDecision | null>(
  'EDGE_ACCESS_DECISION',
  { providedIn: 'root', factory: () => null },
);
