import { InjectionToken } from '@angular/core';

// =============================================================================
// TREI access tokens — SPEC-01 §3 (guards)
//
// Angular requires a class or InjectionToken to inject; TypeScript interfaces
// cannot be injected directly. These tokens provide DI-compatible handles that
// can be overridden in production (via app.config.ts) with real auth/access
// implementations.
//
// The interfaces below mirror the contracts of the shared stubs:
//   @neuroassist/shared/auth-session    → AuthSessionStore (.token signal, .autenticado)
//   @neuroassist/shared/access-decision → AccessDecision (.can)
//
// Note: shared libs source files are outside rootDir when building a packagr
// lib. Interfaces are re-declared locally (structurally equivalent) to keep
// the build green — same pattern as ACS, DASH, HIST, PAC, GEST.
//
// P3 — no Wave-0 bypass:
//   The token factories no longer return null. They return fail-closed
//   defaults: { token: null } (no session) and { can: () => false } (no
//   permissions). This means guards deny access by default until a real
//   implementation is provided — which is the correct secure-by-default
//   behavior.
//
// To wire real enforcement in app.config.ts:
//   { provide: TREI_AUTH_SESSION, useFactory: (s: AuthSessionStore) =>
//       ({ token: s.token() }), deps: [AuthSessionStore] }
//   { provide: TREI_ACCESS_DECISION, useFactory: (s: AuthSessionStore) =>
//       ({ can: (p: string) => s.pode(p) }), deps: [AuthSessionStore] }
// =============================================================================

/** Minimal session read-only view the guards need. */
export interface TreiAuthSession {
  /** The current JWT token, or null if not authenticated. */
  readonly token: string | null;
}

/** Minimal access-decision API (mirrors shared/access-decision pattern). */
export interface TreiAccessDecision {
  /** Returns true if the current user holds the given authority. */
  can(authority: string): boolean;
}

// ---------------------------------------------------------------------------
// TREI_AUTH_SESSION — caregiverSessionGuard depends on this token.
//
// Default factory: { token: null } — no session; guard redirects to /login.
// Override in app.config.ts with a factory wrapping AuthSessionStore.
// ---------------------------------------------------------------------------
export const TREI_AUTH_SESSION = new InjectionToken<TreiAuthSession>(
  'TREI_AUTH_SESSION',
  {
    providedIn: 'root',
    // Fail-closed: no token → guard redirects to /login. No Wave-0 bypass.
    factory: () => ({ token: null }),
  },
);

// ---------------------------------------------------------------------------
// TREI_ACCESS_DECISION — treiGuard and treiProporGuard depend on this token.
//
// Default factory: { can: () => false } — no permissions; guards redirect.
// Override in app.config.ts with a factory wrapping AuthSessionStore.pode().
// ---------------------------------------------------------------------------
export const TREI_ACCESS_DECISION = new InjectionToken<TreiAccessDecision>(
  'TREI_ACCESS_DECISION',
  {
    providedIn: 'root',
    // Fail-closed: no permissions → guards redirect. No Wave-0 bypass.
    factory: () => ({ can: () => false }),
  },
);
