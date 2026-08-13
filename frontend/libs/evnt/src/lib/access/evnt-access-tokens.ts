import { InjectionToken } from '@angular/core';

// =============================================================================
// EVNT access injection tokens (Wave-0 — INT-FE-EVNT-004, INT-FE-EVNT-006)
//
// Angular requires a class or InjectionToken to inject; TypeScript interfaces
// cannot be injected directly. These tokens provide DI-compatible handles that
// default to null until the shared auth/access/empresa infrastructure is promoted
// from Wave-0 stubs to real implementations.
//
// The interfaces below mirror the contracts of the Wave-0 shared stubs:
//   @neuroassist/shared/empresa-context → EmpresaContext
//   @neuroassist/shared/auth-session    → AuthSession
//   @neuroassist/shared/access-decision → AccessDecision
//
// Note: shared libs are non-buildable stubs; importing them from a buildable
// lib (evnt) triggers Angular ngtypecheck outside rootDir. Interfaces are
// re-declared locally (structurally equivalent) to keep the build green.
// Replace local declarations with real imports when shared libs are promoted
// to buildable Angular libraries.
//
// Replace the factory defaults in app.config.ts providers to enable real enforcement:
//   { provide: EVNT_EMPRESA_CONTEXT, useExisting: RealEmpresaContextService }
//   { provide: EVNT_AUTH_SESSION,    useExisting: RealAuthService           }
//   { provide: EVNT_ACCESS_DECISION, useExisting: RealAccessDecisionService }
// =============================================================================

// Local type aliases — structurally equivalent to @neuroassist/shared/* interfaces.
// TODO[INT-FE-EVNT-004]: replace with real imports when shared libs are promoted.
export interface EvntEmpresaContext { readonly id: string | null; }
export interface EvntAuthSession { readonly token: string | null; }
export interface EvntAccessDecision { can(permission: string): boolean; }

/** DI token for EmpresaContext — used by EvntClientHttpAdapter to add X-Empresa-Id header.
 * Wave-0: factory returns null (header omitted; wiring deferred to shared lib promotion). */
export const EVNT_EMPRESA_CONTEXT = new InjectionToken<EvntEmpresaContext | null>(
  'EVNT_EMPRESA_CONTEXT',
  { providedIn: 'root', factory: () => null },
);

/** DI token for AuthSession — used by evntGuard to verify session validity.
 * Wave-0: factory returns null (mock bypass active; see evnt.guard.ts). */
export const EVNT_AUTH_SESSION = new InjectionToken<EvntAuthSession | null>(
  'EVNT_AUTH_SESSION',
  { providedIn: 'root', factory: () => null },
);

/** DI token for AccessDecision — used by evntGuard to check evnt:eventos:read permission.
 * Wave-0: factory returns null (permissions not enforced; mock bypass active). */
export const EVNT_ACCESS_DECISION = new InjectionToken<EvntAccessDecision | null>(
  'EVNT_ACCESS_DECISION',
  { providedIn: 'root', factory: () => null },
);
