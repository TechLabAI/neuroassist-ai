import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { DashNotificationsPort } from './dash-notifications.port';
import { NotificationEnvelope, UnacknowledgedResponse } from '../dash.types';

// =============================================================================
// DashNotificationsHttpAdapter — production adapter (stub, compilable)
// Base: /api/v1/dash/notifications (GWY-normalized — ADR-008 / E2E-014).
// Previously: /panel/notifications (direct service path, pre-ADR-008).
// Switch via provideDash({ adapter: 'http' }) in app.config.ts.
// TODO[INT-FE-DASH-005]: activate once dash-service is deployed.
// SSE auth note: DashStreamClient opens /panel/stream via EventSource — auth
// strategy is P6 scope (FE-E2E-011 / DASH-005-AUTH); not changed here.
// =============================================================================

@Injectable({ providedIn: 'root' })
export class DashNotificationsHttpAdapter implements DashNotificationsPort {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/dash/notifications';

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
