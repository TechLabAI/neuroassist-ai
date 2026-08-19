import { InjectionToken, Signal, signal } from '@angular/core';

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

// =============================================================================
// Cross-module injection tokens — INT-FE-DASH-001 / INT-FE-DASH-002
//
// DASH does NOT import scope:evnt or scope:edge directly (nx-boundaries).
// These view-model types are DASH-owned projections of the external signals.
// The real signals are provided at scope:web (app.config.ts) when the source
// modules are scaffolded. Until then, safe defaults (signal(null)) apply.
// =============================================================================

/**
 * DASH-owned projection of EVNT activeEvent.
 * Provided by scope:web when EVNT FE exports its activeEvent signal.
 */
export interface ActiveEventVm {
  readonly eventId: string;
  readonly urgencia: 'emergencia' | 'rotina';
  readonly patientId: string;
}

/**
 * DASH-owned projection of EDGE deviceBadge.
 * Provided by scope:web when EDGE FE exports its deviceBadge signal.
 */
export interface DeviceBadgeVm {
  readonly status: 'online' | 'offline' | 'unknown';
  readonly lastSeenAt: string | null;
  readonly degraded: boolean;
}

/**
 * Token for the EVNT activeEvent signal.
 * DashHomeFacade uses this to compute `calm` — the security invariant:
 * calm === true ↔ no active alarm from EVNT (normal care).
 * calm === false ↔ an alarm is active in EVNT (action required).
 *
 * Safe default: signal(null) — calm until EVNT provides a real signal.
 * TODO[INT-FE-DASH-001]: override in app.config.ts when EVNT FE is scaffolded.
 */
export const DASH_ACTIVE_EVENT_TOKEN = new InjectionToken<Signal<ActiveEventVm | null>>(
  'DashActiveEventToken',
  { providedIn: 'root', factory: () => signal(null) },
);

/**
 * Token for the EDGE deviceBadge signal.
 * PatientHeaderComponent uses this to render the live device status badge.
 *
 * Safe default: signal(null) — unknown status until EDGE provides a real signal.
 * TODO[INT-FE-DASH-002]: override in app.config.ts when EDGE FE is scaffolded.
 */
export const DASH_DEVICE_BADGE_TOKEN = new InjectionToken<Signal<DeviceBadgeVm | null>>(
  'DashDeviceBadgeToken',
  { providedIn: 'root', factory: () => signal(null) },
);
