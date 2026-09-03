import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ProposalTurnPort } from './proposal-turn.port';
import { ProposeRequest, ProposalTurnResponse } from '../trei.types';

// =============================================================================
// ProposalTurnHttpAdapter — production adapter
// Active when provideTrei({ adapter: 'http' }) — requires trei-service + GWY.
// Base: /api/v1/trei/sessoes/{sessaoId}/propostas (api-contract §2.2)
// X-Empresa-Id and X-Correlation-Id are injected by accessHttpInterceptor
// (@neuroassist/shared/access-http-base) — no manual header setup needed here.
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
