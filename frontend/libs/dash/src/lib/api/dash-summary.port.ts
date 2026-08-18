import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';
import { DashSummaryResponse } from '../dash.types';

// =============================================================================
// DashSummaryPort — BFF composition surface (api-contract.md §2.1)
// Abstraction over GET /panel/summary (dash-service, port 8090 dev).
// Components interact ONLY through DashHomeFacade; never with this port directly.
// =============================================================================

export interface DashSummaryPort {
  /**
   * GET /panel/summary
   * Cross-module composition: patient list + latest event/alarm state (EVNT) +
   * device liveness (EDGE) + unacknowledged notification count (EVNT).
   * Scope is resolved from the JWT — no query params needed.
   */
  getSummary(): Observable<DashSummaryResponse>;
}

export const DASH_SUMMARY_PORT = new InjectionToken<DashSummaryPort>('DashSummaryPort');
