import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ProposalTurnPort } from './proposal-turn.port';
import { ProposeRequest, ProposalTurnResponse } from '../trei.types';

// =============================================================================
// ProposalTurnHttpAdapter — production adapter (stub)
// TODO[INT-FE-TREI-002]: activate when trei-service is deployed.
// Base: /api/v1/trei/sessoes/{sessaoId}/propostas (api-contract §2.2)
// =============================================================================

@Injectable({ providedIn: 'root' })
export class ProposalTurnHttpAdapter implements ProposalTurnPort {
  private readonly http = inject(HttpClient);

  propose(sessaoId: string, request: ProposeRequest): Observable<ProposalTurnResponse> {
    return this.http.post<ProposalTurnResponse>(
      `/api/v1/trei/sessoes/${sessaoId}/propostas`,
      request,
    );
  }
}
