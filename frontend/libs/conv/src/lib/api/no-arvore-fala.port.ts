import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';
import { NoArvoreFala, Locale } from '../conv.types';

// =============================================================================
// NoArvoreFalaPort — primary port for the CONV speech-tree resource.
// API contract: conv-service GET /api/v1/conv/tree
//               POST /api/v1/conv/select
//               POST /api/v1/conv/search
// Security model: patient-with-session JWT (ADR-012); no caregiver authority.
// Retrieval-only at runtime — CONV-FE never writes tree content (ADR-015).
// =============================================================================

export interface NoArvoreFalaPort {
  /** Fetch the navigable speech tree for the authenticated patient.
   *  Returns only nodes with Dual-Authorization complete in the given locale
   *  (retrieval-only, ADR-015). locale defaults to the JWT session locale. */
  list(locale?: Locale): Observable<NoArvoreFala[]>;

  /** Emit a node-selection audit event (non-mutating on CONV state — safe to
   *  retry, see api-contract.md §1 SPEC-05). Does not change tree content.
   *  Ignored by MockAdapter (audit is a backend concern). */
  selectNode(nodeId: string, locale: Locale): Observable<void>;

  /** Semantic-search assist: returns nearest phrases for the given query text,
   *  ranked by ES kNN over the active-locale content (ADR-015, TECH-007).
   *  FE NEVER queries Elasticsearch directly — result comes from conv-service. */
  search(query: string, locale: Locale): Observable<NoArvoreFala[]>;
}

export const NO_ARVORE_FALA_PORT = new InjectionToken<NoArvoreFalaPort>('NoArvoreFalaPort');
