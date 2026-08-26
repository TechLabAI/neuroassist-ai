import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { DashSummaryPort } from './dash-summary.port';
import { DashSummaryResponse } from '../dash.types';

// =============================================================================
// DashSummaryHttpAdapter — production adapter (stub, compilable)
// Base: /api/v1/dash (GWY-normalized — ADR-008 / E2E-014).
// Previously: /panel (direct service path, pre-ADR-008).
// Switch via provideDash({ adapter: 'http' }) in app.config.ts.
// TODO[INT-FE-DASH-004]: activate once dash-service + GWY route is deployed.
// Auth: Bearer JWT from ACS; X-Correlation-Id propagated by shared interceptor.
// =============================================================================

@Injectable({ providedIn: 'root' })
export class DashSummaryHttpAdapter implements DashSummaryPort {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/dash';

  getSummary(): Observable<DashSummaryResponse> {
    return this.http.get<DashSummaryResponse>(`${this.base}/summary`);
  }
}
