import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CaregiverApprovalPort } from './caregiver-approval.port';
import { ApprovalRequest, CaregiverApprovalResponse } from '../trei.types';

// =============================================================================
// CaregiverApprovalHttpAdapter — production adapter (stub)
// TODO[INT-FE-TREI-004]: activate when trei-service is deployed.
// api-contract: §2.4 POST /api/v1/trei/nos/{noId}/aprovacoes/cuidadora
// CAREGIVER SCOPE ONLY — patient scope handled by CONV, different token.
// =============================================================================

@Injectable({ providedIn: 'root' })
export class CaregiverApprovalHttpAdapter implements CaregiverApprovalPort {
  private readonly http = inject(HttpClient);

  approve(noId: string, request: ApprovalRequest): Observable<CaregiverApprovalResponse> {
    return this.http.post<CaregiverApprovalResponse>(
      `/api/v1/trei/nos/${noId}/aprovacoes/cuidadora`,
      request,
    );
  }
}
