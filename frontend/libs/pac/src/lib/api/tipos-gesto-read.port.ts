import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';

// ---------------------------------------------------------------------------
// TipoGesto — GEST-owned, PAC reads via GWY (api-contract note §4)
// PAC is NOT the authoring source for TipoGesto primitives (ADR-004).
// PAC reads the GEST catalog to populate gesture selectors; new-type requests
// are delegated to GEST via requestNew (naming ≠ detectability).
// ---------------------------------------------------------------------------

export interface TipoGesto {
  readonly id: string;
  readonly nome: string;
}

// ---------------------------------------------------------------------------
// TiposGestoReadPort — SPEC-04 §2 (cross-module read, GEST-owned)
// Base path via GWY: /api/gest/v1/tipos-gesto
// Degradation: if GEST is unavailable, PAC shows "carregando tipos..." and
// allows manual name entry (graceful degradation, SPEC-07).
// ---------------------------------------------------------------------------

export interface TiposGestoReadPort {
  /** GET /api/gest/v1/tipos-gesto */
  list(): Observable<TipoGesto[]>;

  /** GET /api/gest/v1/tipos-gesto/{id} */
  get(id: string): Observable<TipoGesto>;

  /** POST to GEST — delegate new-type request (naming only, not detectability) */
  requestNew(nome: string): Observable<TipoGesto>;
}

export const TIPOS_GESTO_READ_PORT = new InjectionToken<TiposGestoReadPort>(
  'TiposGestoReadPort',
);
