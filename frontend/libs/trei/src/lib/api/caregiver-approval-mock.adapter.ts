import { Injectable } from '@angular/core';
import { Observable, of, timer } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { CaregiverApprovalPort } from './caregiver-approval.port';
import { ApprovalRequest, CaregiverApprovalResponse } from '../trei.types';

// =============================================================================
// CaregiverApprovalMockAdapter — dev/test (default adapter)
// Simulates POST /api/v1/trei/nos/{noId}/aprovacoes/cuidadora.
// Idempotent: repeated approval of same (noId, locale) is silently accepted.
// Delay 400ms (project standard).
// Patient side always false (INV-1 — patient approves in CONV, never here).
// =============================================================================

@Injectable({ providedIn: 'root' })
export class CaregiverApprovalMockAdapter implements CaregiverApprovalPort {
  private readonly approved = new Set<string>();  // key = `${noId}:${locale}`

  approve(noId: string, request: ApprovalRequest): Observable<CaregiverApprovalResponse> {
    return timer(400).pipe(
      switchMap(() => {
        const key = `${noId}:${request.locale}`;
        this.approved.add(key);

        const response: CaregiverApprovalResponse = {
          noId,
          locale: request.locale,
          confirmadoCuidadora: true,
          confirmadoPaciente: false,  // INV-1: patient approves in CONV, never in TREI-FE
          subEstado: 'aguardando_aprovacao',
          publicacaoDisparada: false,  // dual-key gate: needs patient confirmation too (ADR-003)
        };

        return of(response);
      }),
    );
  }
}
