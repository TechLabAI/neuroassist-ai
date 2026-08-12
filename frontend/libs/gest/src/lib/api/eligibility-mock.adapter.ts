import { Injectable } from '@angular/core';
import { EligibilityResult, Urgencia } from '@neuroassist/shared/contracts';
import { EligibilityPort } from './eligibility.port';

// Dev/test default: always eligible, mirrors gest-service's own guard —
// "emergencia" is the only urgency level the backend actually checks.
@Injectable()
export class EligibilityMockAdapter implements EligibilityPort {
  async check(urgencia: Urgencia): Promise<EligibilityResult> {
    if (urgencia !== 'emergencia') {
      return { eligible: true, reason: null, unsupportedPrimitiveIds: [] };
    }
    return { eligible: true, reason: null, unsupportedPrimitiveIds: [] };
  }
}
