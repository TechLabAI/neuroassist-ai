import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { DashNotificationsPort } from './dash-notifications.port';
import { NotificationEnvelope, UnacknowledgedResponse } from '../dash.types';

// =============================================================================
// DashNotificationsHttpAdapter — production adapter (stub, compilable)
// Base: /panel — routed through GWY (api-contract.md §4; port 8090 dev).
// Switch via provideDash({ adapter: 'http' }) in app.config.ts.
// TODO[INT-FE-DASH-005]: activate once dash-service is deployed.
// SSE auth note: EventSource cannot send custom headers; coordinate with BE
// whether to use cookie-based or query-param JWT for /panel/stream (M5 warning).
// =============================================================================

@Injectable({ providedIn: 'root' })
export class DashNotificationsHttpAdapter implements DashNotificationsPort {
  private readonly http = inject(HttpClient);
  private readonly base = '/panel/notifications';

  listUnacknowledged(): Observable<UnacknowledgedResponse> {
    return this.http.get<UnacknowledgedResponse>(`${this.base}/unacknowledged`);
  }

  acknowledge(dispatchId: string): Observable<void> {
    return this.http.post<void>(`${this.base}/${dispatchId}/acknowledge`, null);
  }

  poll(
    waitSeconds: number,
    cursor?: string,
  ): Observable<{ notifications: NotificationEnvelope[]; cursor: string }> {
    let params = new HttpParams().set('waitSeconds', String(waitSeconds));
    if (cursor) {
      params = params.set('cursor', cursor);
    }
    return this.http.get<{ notifications: NotificationEnvelope[]; cursor: string }>(
      `${this.base}/poll`,
      { params },
    );
  }
}
