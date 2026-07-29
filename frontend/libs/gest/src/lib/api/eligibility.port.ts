import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';
import { EligibilityResult, Urgencia } from '@neuroassist/shared/contracts';

// ---------------------------------------------------------------------------
// EligibilityPort — SPEC-04 §4
// Thin REST port for the eligibility:check endpoint.
// Consumed by host pages (PAC command editor) when saving an emergency command
// to confirm all primitives are 'supported' before persisting.
// GEST-FE itself does not call this during a live test — it is a pre-save guard.
// ---------------------------------------------------------------------------

export interface EligibilityCheckRequest {
  readonly urgencia: Urgencia;
  readonly primitiveIds: ReadonlyArray<string>;
}

export interface EligibilityPort {
  check(req: EligibilityCheckRequest): Observable<EligibilityResult>;
}

export const ELIGIBILITY_PORT = new InjectionToken<EligibilityPort>(
  'EligibilityPort',
);
