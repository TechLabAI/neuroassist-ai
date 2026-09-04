import { Injectable, inject } from '@angular/core';
import { ObservabilityService } from '@neuroassist/shared/observability';

// ---------------------------------------------------------------------------
// TREI-FE-026 / TREI-INT-006 — TreiTelemetryService
// Emits FE telemetry events for all critical TREI module operations.
//
// Zero PII (SEC-002): only opaque IDs (sessionId, noId — UUIDs from backend)
// are emitted — NEVER patientId in plaintext, caregiver names, clinical text,
// or any LGPD-sensitive content.
//
// Events emitted:
//   trei.session.started       — training session successfully started (opaque sessaoId)
//   trei.turn.submitted        — caregiver turn sent and LLM proposals received (opaque sessaoId)
//   trei.node.approved         — draft node caregiver-side approval confirmed (opaque noId)
//   trei.session.error         — session start failed (no sensitive data)
//   trei.turn.error            — turn submission failed (no sensitive data)
//   trei.node.approval.error   — node approval failed (no sensitive data)
// ---------------------------------------------------------------------------

@Injectable({ providedIn: 'root' })
export class TreiTelemetryService {
  private readonly obs = inject(ObservabilityService);

  /**
   * Fired when a training session is successfully started.
   * @param sessaoId — opaque backend session ID (UUID), not PII.
   */
  trackSessionStarted(sessaoId: string): void {
    this.obs.emit({ event: 'trei.session.started', sessaoId });
  }

  /**
   * Fired when a caregiver turn is submitted and LLM proposals are received.
   * @param sessaoId — opaque backend session ID (UUID), not PII.
   */
  trackTurnSubmitted(sessaoId: string): void {
    this.obs.emit({ event: 'trei.turn.submitted', sessaoId });
  }

  /**
   * Fired after a draft node receives caregiver-side approval (INV-1: never patient-side).
   * @param noId — opaque draft-node ID (UUID), not PII.
   */
  trackNodeApproved(noId: string): void {
    this.obs.emit({ event: 'trei.node.approved', noId });
  }

  /**
   * Fired when a session start fails (e.g. HTTP error, patient-not-found 404).
   * No sensitive data emitted — error type only.
   * @param errorCode — HTTP status code or 'UNKNOWN'; not derived from patient data.
   */
  trackSessionError(errorCode: string): void {
    this.obs.emit({ event: 'trei.session.error', errorCode });
  }

  /**
   * Fired when a turn submission fails (HTTP or network error).
   * @param sessaoId — opaque session ID; no patient data.
   */
  trackTurnError(sessaoId: string): void {
    this.obs.emit({ event: 'trei.turn.error', sessaoId });
  }

  /**
   * Fired when a node approval request fails.
   * @param noId — opaque node ID; no patient data.
   */
  trackNodeApprovalError(noId: string): void {
    this.obs.emit({ event: 'trei.node.approval.error', noId });
  }
}
