import { InjectionToken } from '@angular/core';

// =============================================================================
// HIST access tokens — Wave-0 bypass pattern (aligned with ACS / EVNT guards).
// Provide real implementations in production to enforce RBAC (SPEC-01 §3).
// Guards return true when these tokens resolve to null (dev/test/e2e).
// =============================================================================

export interface HistAuthSession {
  readonly token: string | null;
  /** Scopes granted to the current user. */
  readonly scopes: ReadonlySet<string>;
}

export interface HistAccessDecision {
  /** Returns true if the current user holds the given permission string. */
  can(permission: string): boolean;
}

export const HIST_AUTH_SESSION = new InjectionToken<HistAuthSession | null>(
  'HistAuthSession',
  { providedIn: 'root', factory: () => null },
);

export const HIST_ACCESS_DECISION = new InjectionToken<HistAccessDecision | null>(
  'HistAccessDecision',
  { providedIn: 'root', factory: () => null },
);
