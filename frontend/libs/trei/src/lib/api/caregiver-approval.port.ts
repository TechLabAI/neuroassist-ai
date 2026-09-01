import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';
import { ApprovalRequest, CaregiverApprovalResponse } from '../trei.types';

// =============================================================================
// CaregiverApprovalPort — §2.4 POST /api/v1/trei/nos/{noId}/aprovacoes/cuidadora
// CAREGIVER SIDE ONLY (INV-1, ADR-007 FE, ADR-003 BE).
// No method for patient approval — patient confirms in CONV (Tela 09), different module,
// different actor, different scope (treinamento.no.aprovar.paciente ≠ cuidadora scope).
// Idempotent: re-approving an already-approved (noId, locale) is a no-op (SPEC-05).
// Caregiver scope: treinamento.no.aprovar.cuidadora
// =============================================================================

export interface CaregiverApprovalPort {
  /**
   * POST /api/v1/trei/nos/{noId}/aprovacoes/cuidadora — approve caregiver side.
   * 200 → CaregiverApprovalResponse (publicacaoDisparada:false while patient pending).
   * Idempotent — same approval twice: no 409, treated as no-op (SPEC-05).
   */
  approve(noId: string, request: ApprovalRequest): Observable<CaregiverApprovalResponse>;
}

export const CAREGIVER_APPROVAL_PORT = new InjectionToken<CaregiverApprovalPort>(
  'CaregiverApprovalPort',
);
