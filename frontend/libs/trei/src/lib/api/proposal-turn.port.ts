import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';
import { ProposeRequest, ProposalTurnResponse } from '../trei.types';

// =============================================================================
// ProposalTurnPort — §2.2 POST /api/v1/trei/sessoes/{sessaoId}/propostas
// Drives one LLM proposal turn: caregiver instruction → proposed nodes.
// Caregiver scope: treinamento.no.propor (SPEC-01).
// The FE never calls the Anthropic API directly — all generation is server-mediated
// (ADR-005 local, TECH-007). This port represents the TREI-FE→trei-service call.
// =============================================================================

export interface ProposalTurnPort {
  /**
   * POST /api/v1/trei/sessoes/{sessaoId}/propostas — request LLM proposals.
   * 200 → ProposalTurnResponse; 503 → llm-unavailable.
   */
  propose(sessaoId: string, request: ProposeRequest): Observable<ProposalTurnResponse>;
}

export const PROPOSAL_TURN_PORT = new InjectionToken<ProposalTurnPort>(
  'ProposalTurnPort',
);
