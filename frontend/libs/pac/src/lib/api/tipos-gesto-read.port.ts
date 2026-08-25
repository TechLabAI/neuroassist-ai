import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';
import type { DetectabilityState, EngineFeatureRef } from '@neuroassist/shared/contracts';

// ---------------------------------------------------------------------------
// TipoGesto — GEST-owned, PAC reads via GWY (api-contract note §4)
// PAC is NOT the authoring source for TipoGesto primitives (ADR-004).
// PAC reads the GEST catalog to populate gesture selectors; new-type requests
// are delegated to GEST via requestNew (naming ≠ detectability).
// ---------------------------------------------------------------------------

export interface TipoGesto {
  readonly id: string;
  readonly nome: string;

  // --- detectabilidade — espelha `tipo_gesto` no banco -----------------------
  //
  // Estes dois campos existiam no banco desde a V0400 e o frontend os ignorava.
  // A consequência estava na tela: o seletor oferecia "Leitura labial" e "Mover
  // o nariz" ao lado de "Piscar duas vezes", sem distinção nenhuma — nomes sem
  // motor atrás, apresentados como se funcionassem. A cuidadora só descobriria
  // ao testar, e o teste ficava girando sem veredito.
  //
  // A constraint `ck_tipo_gesto_supported_needs_ref` garante o par: `supported`
  // sempre traz `engineFeatureRef`; qualquer outro estado pode não trazer.
  readonly detectabilityState: DetectabilityState;
  readonly engineFeatureRef: EngineFeatureRef | null;
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
