import { Injectable, inject } from '@angular/core';
import { ObservabilityService } from '@neuroassist/shared/observability';
import { ComandoUrgencia } from '../api/comando.port';

// ---------------------------------------------------------------------------
// PAC-FE-022 — PacTelemetryService
// Emits FE telemetry events for all relevant PAC module actions.
//
// Zero PII: only opaque IDs and action types are emitted — never patient names,
// command labels, or any user-identifiable content.
//
// Events emitted:
//   pac.catalog.loaded            — command catalog fetched (count)
//   pac.comando.saved             — draft created or updated (urgencia type)
//   pac.comando.<action>          — lifecycle transition (validated / published /
//                                   recalibrated / retired) with opaque comandoId
//   pac.error.emergency_gate_blocked   — 422 emergency-not-detectable gate fired
//   pac.error.conflict_precondition    — 409 conflict-precondition on save/lifecycle
// ---------------------------------------------------------------------------

/** Lifecycle actions tracked by PacTelemetryService. */
export type PacLifecycleAction = 'saved' | 'validated' | 'published' | 'recalibrated' | 'retired';

@Injectable({ providedIn: 'root' })
export class PacTelemetryService {
  private readonly obs = inject(ObservabilityService);

  /** Fired after the command catalog is successfully loaded from the backend. */
  trackCatalogLoaded(count: number): void {
    this.obs.emit({ event: 'pac.catalog.loaded', count });
  }

  /**
   * Fired after a draft command is created or identity-edited (save operation).
   * Uses urgencia type (not name) — non-PII enum value.
   */
  trackComandoSaved(urgencia: ComandoUrgencia): void {
    this.obs.emit({ event: 'pac.comando.saved', urgencia });
  }

  /**
   * Fired after each lifecycle transition completes.
   * @param action  — one of: 'validated' | 'published' | 'recalibrated' | 'retired' | 'saved'
   * @param comandoId — opaque backend ID (UUID), not PII.
   */
  trackLifecycleTransition(action: PacLifecycleAction, comandoId: string): void {
    this.obs.emit({ event: `pac.comando.${action}`, comandoId });
  }

  /**
   * Fired when the emergency gate blocks publication (BE returns 422 with
   * type=emergency-not-detectable). Safety-critical: used to track fail-closed
   * events for audit/review.
   */
  trackEmergencyGateBlocked(comandoId: string): void {
    this.obs.emit({ event: 'pac.error.emergency_gate_blocked', comandoId });
  }

  /**
   * Fired when a conflict-precondition error (409) is detected during
   * save or lifecycle transition — indicates ETag mismatch / concurrent edit.
   */
  trackConflictPrecondition(comandoId: string): void {
    this.obs.emit({ event: 'pac.error.conflict_precondition', comandoId });
  }
}
