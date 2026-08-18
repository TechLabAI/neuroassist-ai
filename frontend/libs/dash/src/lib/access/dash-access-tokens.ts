import { InjectionToken } from '@angular/core';

// =============================================================================
// DASH access tokens — Wave-0 bypass pattern (aligned with HIST / ACS guards).
// Provide real implementations in production to enforce RBAC (SPEC-01 §3).
// Guards return true when these tokens resolve to null (dev/test/e2e).
// =============================================================================

export interface DashAuthSession {
  readonly token: string | null;
  /** Scopes granted to the current user. */
  readonly scopes: ReadonlySet<string>;
}

export interface DashAccessDecision {
  /** Returns true if the current user holds the given permission string. */
  can(permission: string): boolean;
}

export const DASH_AUTH_SESSION = new InjectionToken<DashAuthSession | null>(
  'DashAuthSession',
  { providedIn: 'root', factory: () => null },
);

export const DASH_ACCESS_DECISION = new InjectionToken<DashAccessDecision | null>(
  'DashAccessDecision',
  { providedIn: 'root', factory: () => null },
);
