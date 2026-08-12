import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';

// =============================================================================
// AcsLinkPort — SPEC-04 §3 (data-access contract)
// Abstraction over the acs-service /caregivers/{id}/links sub-resource.
// Coverage invariant (ADR-005): BE enforces last-covering-caregiver block (409/422);
// FE derives per-patient coverage client-side from active links ⋈ active caregivers.
// =============================================================================

// ---------------------------------------------------------------------------
// Wire DTOs (mirror BE api-contract §5)
// ---------------------------------------------------------------------------

export interface CaregiverLinkDto {
  readonly id: string;          // linkId
  readonly cuidadorId: string;
  readonly pacienteId: string;
  readonly active: boolean;
  readonly createdAt: string;   // ISO-8601
}

export interface LinkCreateDto {
  readonly pacienteId: string;
}

// ---------------------------------------------------------------------------
// Port interface
// ---------------------------------------------------------------------------

export interface AcsLinkPort {
  /** GET /caregivers/{cid}/links — list all links for one caregiver. */
  listForCaregiver(cuidadorId: string): Observable<CaregiverLinkDto[]>;

  /** POST /caregivers/{cid}/links — bind caregiver to patient. */
  link(cuidadorId: string, pacienteId: string): Observable<CaregiverLinkDto>;

  /** DELETE /caregivers/{cid}/links/{linkId} — revoke link (soft).
   * 409 = last-covering-block (ADR-005 fail-closed). */
  revoke(cuidadorId: string, linkId: string): Observable<void>;
}

export const ACS_LINK_PORT = new InjectionToken<AcsLinkPort>('AcsLinkPort');
