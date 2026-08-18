import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';
import { NotificationEnvelope, UnacknowledgedResponse } from '../dash.types';

// =============================================================================
// DashNotificationsPort — caregiver notification actions (api-contract.md §4)
// Abstraction over:
//   GET  /panel/notifications/unacknowledged  (§4.1 on-connect catch-up)
//   POST /panel/notifications/{dispatchId}/acknowledge  (§4.2 ack write-through to EVNT)
//   GET  /panel/notifications/poll  (§3.2 long-poll fallback, used by DashStreamClient)
// SSE /panel/stream is handled separately by the DashStreamClient (scope:web layer).
// =============================================================================

export interface DashNotificationsPort {
  /**
   * GET /panel/notifications/unacknowledged
   * On-connect catch-up — pulls caregiver's pending notifications from EVNT.
   * DASH does NOT persist notifications locally (write-through only, ADR-004/006).
   */
  listUnacknowledged(): Observable<UnacknowledgedResponse>;

  /**
   * POST /panel/notifications/{dispatchId}/acknowledge
   * Write-through to EVNT. Idempotent (repeated ack → 204).
   */
  acknowledge(dispatchId: string): Observable<void>;

  /**
   * GET /panel/notifications/poll?waitSeconds=<n>&cursor=<opaque>
   * Long-poll fallback when SSE is unavailable (api-contract.md §3.2).
   */
  poll(waitSeconds: number, cursor?: string): Observable<{ notifications: NotificationEnvelope[]; cursor: string }>;
}

export const DASH_NOTIFICATIONS_PORT = new InjectionToken<DashNotificationsPort>('DashNotificationsPort');
