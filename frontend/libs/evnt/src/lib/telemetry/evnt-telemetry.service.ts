import { Injectable, inject } from '@angular/core';
import { ObservabilityService } from '@neuroassist/shared/observability';

// ---------------------------------------------------------------------------
// FE-EVNT-025 — EvntTelemetryService
// Emits FE telemetry events for all critical EVNT module operations.
//
// Zero PII: only urgency type, opaque IDs (UUIDs), and technical metadata
// (degradation reason) are emitted — never patient names, caregiver names,
// clinical data, or any LGPD-sensitive content.
//
// Events emitted:
//   evnt.alarm.received                    — alarm hydrated or received (urgency only)
//   evnt.alarm.confirm.submitted           — confirmation request initiated (opaque eventoId)
//   evnt.alarm.confirm.success             — confirmation 200 OK (opaque eventoId)
//   evnt.alarm.confirm.conflict            — confirmation 409 feedback-terminal (opaque eventoId)
//   evnt.alarm.confirm.error               — confirmation failed (non-409 error)
//   evnt.delivery.degraded.activated       — SSE failed, polling fallback activated (reason)
//   evnt.stream.sse.error                  — SSE stream emitted an error event
// ---------------------------------------------------------------------------

@Injectable({ providedIn: 'root' })
export class EvntTelemetryService {
  private readonly obs = inject(ObservabilityService);

  /**
   * Fired when an active alarm is detected (hydration or SSE evento.created).
   * @param urgencia — event urgency type; NOT a patient name or clinical field.
   */
  trackAlarmReceived(urgencia: 'emergencia' | 'rotina'): void {
    this.obs.emit({ event: 'evnt.alarm.received', urgencia });
  }

  /**
   * Fired when the cuidador initiates a confirmation request.
   * @param eventoId — opaque backend event ID (UUID), not PII.
   */
  trackConfirmStart(eventoId: string): void {
    this.obs.emit({ event: 'evnt.alarm.confirm.submitted', eventoId });
  }

  /**
   * Fired after confirmation returns 200 OK.
   * @param eventoId — opaque backend event ID, not PII.
   */
  trackConfirmSuccess(eventoId: string): void {
    this.obs.emit({ event: 'evnt.alarm.confirm.success', eventoId });
  }

  /**
   * Fired when the backend returns 409 feedback-terminal (already confirmed by another cuidador).
   * Safety-critical: used to audit concurrent confirmation attempts.
   * @param eventoId — opaque backend event ID, not PII.
   */
  trackConfirmConflict(eventoId: string): void {
    this.obs.emit({ event: 'evnt.alarm.confirm.conflict', eventoId });
  }

  /** Fired when a confirmation request fails with a non-409 error. */
  trackConfirmError(): void {
    this.obs.emit({ event: 'evnt.alarm.confirm.error' });
  }

  /**
   * Fired when the delivery channel degrades from SSE to polling.
   * @param reason — 'sse_error': stream emitted an error; 'sse_timeout': SSE disabled by flag.
   */
  trackDegradedActivated(reason: 'sse_error' | 'sse_timeout'): void {
    this.obs.emit({ event: 'evnt.delivery.degraded.activated', reason });
  }

  /** Fired when the SSE stream emits an error event (before polling fallback activates). */
  trackSseError(): void {
    this.obs.emit({ event: 'evnt.stream.sse.error' });
  }
}
