import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { EligibilityPort, EligibilityCheckRequest } from './eligibility.port';
import { EligibilityResult } from '@neuroassist/shared/contracts';

// ---------------------------------------------------------------------------
// EligibilityHttpAdapter — stub compilable for Layer 1
// Endpoint: POST /api/v1/gest/eligibility:check
// TODO[GEST-FE-005]: implement real HTTP call once gest-service BE is running
// ---------------------------------------------------------------------------

@Injectable()
export class EligibilityHttpAdapter implements EligibilityPort {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/gest';

  check(req: EligibilityCheckRequest): Observable<EligibilityResult> {
    return this.http.post<EligibilityResult>(
      `${this.base}/eligibility:check`,
      req,
    );
  }
}
