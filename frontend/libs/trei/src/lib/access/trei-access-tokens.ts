import { InjectionToken } from '@angular/core';

// =============================================================================
// TREI access tokens — SPEC-01 §3 (guards)
// Mirrors the pattern established by ACS, HIST, and CONV modules.
// Wave-0 bypass: tokens default to null (factory: null) in all existing tests.
// To enforce in production:
//   { provide: TREI_AUTH_SESSION, useExisting: RealAuthSessionService }
//   { provide: TREI_ACCESS_DECISION, useExisting: RealAccessDecisionService }
// =============================================================================

/** Minimal session read-only view the guards need. */
export interface TreiAuthSession {
  readonly token: string | null;
}

/** Minimal access-decision API (mirrors shared/access-decision pattern). */
export interface TreiAccessDecision {
  /** Returns true if the current user holds the given authority. */
  can(authority: string): boolean;
}

export const TREI_AUTH_SESSION = new InjectionToken<TreiAuthSession | null>(
  'TreiAuthSession',
  { providedIn: 'root', factory: () => null },
);

export const TREI_ACCESS_DECISION = new InjectionToken<TreiAccessDecision | null>(
  'TreiAccessDecision',
  { providedIn: 'root', factory: () => null },
);
