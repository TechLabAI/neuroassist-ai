import { InjectionToken } from '@angular/core';
import { EligibilityResult, Urgencia } from '@neuroassist/shared/contracts';

// ---------------------------------------------------------------------------
// EligibilityPort — SPEC-04 §4
// Wraps POST /gest-service/eligibility (emergency-command guard). GestEligibilityService
// (GEST-FE-009) is the only consumer; components never call this port directly.
// ---------------------------------------------------------------------------

export interface EligibilityPort {
  check(urgencia: Urgencia): Promise<EligibilityResult>;
}

export const ELIGIBILITY_PORT = new InjectionToken<EligibilityPort>('ELIGIBILITY_PORT');
