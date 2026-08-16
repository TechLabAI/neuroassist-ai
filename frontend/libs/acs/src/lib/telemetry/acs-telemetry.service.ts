import { Injectable, inject } from '@angular/core';
import { ObservabilityService } from '@neuroassist/shared/observability';

// ---------------------------------------------------------------------------
// INT-FE-ACS-004 — AcsTelemetryService
// Emits FE telemetry events for all critical ACS module operations.
//
// Zero PII: only opaque IDs and action types are emitted — never caregiver
// names, patient names, or any user-identifiable content.
//
// Events emitted:
//   acs.caregiver.invite.start           — invite flow initiated
//   acs.caregiver.invite.success         — cuidador created (opaque cuidadorId)
//   acs.caregiver.invite.error           — invite failed
//   acs.caregiver.deactivate.adr005_block — deactivation pre-flight blocked (ADR-005)
//   acs.caregiver.deactivate.success     — cuidador deactivated (opaque cuidadorId)
//   acs.caregiver.deactivate.error       — deactivation request failed
//   acs.caregiver.revoke_link.adr005_block_409 — BE 409 last-covering-block (ADR-005)
//   acs.caregiver.revoke_link.success    — link revoked (opaque linkId)
//   acs.caregiver.revoke_link.error      — revoke failed (non-409 error)
//   acs.caregiver.load_roster.error      — roster load failed (logged only on error)
// ---------------------------------------------------------------------------

@Injectable({ providedIn: 'root' })
export class AcsTelemetryService {
  private readonly obs = inject(ObservabilityService);

  /** Fired when the coordinator initiates an invite flow. */
  trackInviteStart(): void {
    this.obs.emit({ event: 'acs.caregiver.invite.start' });
  }

  /**
   * Fired after a cuidador is successfully created.
   * @param cuidadorId — opaque backend ID (UUID), not PII.
   */
  trackInviteSuccess(cuidadorId: string): void {
    this.obs.emit({ event: 'acs.caregiver.invite.success', cuidadorId });
  }

  /** Fired when the invite request fails (any error). */
  trackInviteError(): void {
    this.obs.emit({ event: 'acs.caregiver.invite.error' });
  }

  /**
   * Fired when the ADR-005 pre-flight check blocks a deactivation before the request.
   * Safety-critical: used to audit how often coordinators attempt last-covering deactivations.
   * @param pacienteRef — opaque patient reference (ID), not PII.
   */
  trackDeactivateBlock(pacienteRef: string): void {
    this.obs.emit({ event: 'acs.caregiver.deactivate.adr005_block', pacienteRef });
  }

  /**
   * Fired after a cuidador is successfully deactivated.
   * @param cuidadorId — opaque backend ID, not PII.
   */
  trackDeactivateSuccess(cuidadorId: string): void {
    this.obs.emit({ event: 'acs.caregiver.deactivate.success', cuidadorId });
  }

  /** Fired when a deactivation request fails (adapter error, non-ADR-005). */
  trackDeactivateError(): void {
    this.obs.emit({ event: 'acs.caregiver.deactivate.error' });
  }

  /**
   * Fired when the backend returns 409 on revokeLink (last-covering block — ADR-005).
   * Safety-critical: used to audit last-covering revoke attempts that bypass the UI pre-flight.
   * @param pacienteRef — opaque patient reference, not PII.
   */
  trackRevokeLinkBlock409(pacienteRef: string): void {
    this.obs.emit({ event: 'acs.caregiver.revoke_link.adr005_block_409', pacienteRef });
  }

  /**
   * Fired after a link is successfully revoked.
   * @param linkId — opaque link ID, not PII.
   */
  trackRevokeLinkSuccess(linkId: string): void {
    this.obs.emit({ event: 'acs.caregiver.revoke_link.success', linkId });
  }

  /** Fired when a revoke fails with a non-409 error. */
  trackRevokeLinkError(): void {
    this.obs.emit({ event: 'acs.caregiver.revoke_link.error' });
  }

  /** Fired when the roster fails to load. Suppressed on success (avoids high-frequency noise). */
  trackLoadRosterError(): void {
    this.obs.emit({ event: 'acs.caregiver.load_roster.error' });
  }
}
