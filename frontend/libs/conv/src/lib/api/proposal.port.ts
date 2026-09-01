import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';
import { NoArvoreFala, Locale } from '../conv.types';

// =============================================================================
// ProposalPort — port for TREI-sourced proposals awaiting patient confirmation.
// IMPORTANT: the real endpoint belongs to TREI (future module).
//   G-2 / CONV-INT-006: HttpAdapter DEFERRED — ProposalMockAdapter is the only
//   implementation in scope for this promote. Do NOT create HttpAdapter here.
// API contract (future): TREI-service GET /api/v1/trei/proposals?pendente=paciente
//                        POST /api/v1/trei/proposals/{id}/confirmar
//                        POST /api/v1/trei/proposals/{id}/recusar
// =============================================================================

export interface ProposalPort {
  /** List nodes caregiver-approved but still awaiting patient confirmation in
   *  the given locale (Dual-Authorization, ADR-016/ADR-018). */
  listPendingPatient(locale: Locale): Observable<NoArvoreFala[]>;

  /** Patient confirms a pending proposal in the given locale.
   *  On Dual-Authorization complete, backend triggers ES indexing + ElevenLabs
   *  synthesis (ADR-015/ADR-017) — CONV-FE only reflects the resulting state. */
  confirmarPaciente(id: string, locale: Locale): Observable<void>;

  /** Patient rejects a proposal for the given locale.
   *  Node remains in draft for other locales. */
  recusar(id: string, locale: Locale): Observable<void>;
}

export const PROPOSAL_PORT = new InjectionToken<ProposalPort>('ProposalPort');
