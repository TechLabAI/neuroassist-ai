import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { delay } from 'rxjs/operators';
import { EligibilityPort, EligibilityCheckRequest } from './eligibility.port';
import { EligibilityResult } from '@neuroassist/shared/contracts';

// ---------------------------------------------------------------------------
// EligibilityMockAdapter — dev/test fallback (always returns eligible)
// ---------------------------------------------------------------------------

@Injectable()
export class EligibilityMockAdapter implements EligibilityPort {
  check(req: EligibilityCheckRequest): Observable<EligibilityResult> {
    return of({
      eligible: true,
      reason: null,
      unsupportedPrimitiveIds: [],
    } satisfies EligibilityResult).pipe(delay(200));
  }
}
