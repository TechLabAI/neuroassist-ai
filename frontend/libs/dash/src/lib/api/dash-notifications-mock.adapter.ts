import { Injectable } from '@angular/core';
import { Observable, of, timer } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { DashNotificationsPort } from './dash-notifications.port';
import { NotificationEnvelope, UnacknowledgedResponse } from '../dash.types';

// =============================================================================
// DashNotificationsMockAdapter — dev/test (default adapter)
// Seed: one pending emergency notification for Márcio Amaral.
// Delay 400ms (project standard).
// acknowledge() is a no-op in mock (client-side eviction only).
// TODO[INT-FE-DASH-001]: wire to real EVNT activeEvent signal (bloqueio externo).
// =============================================================================

const MOCK_PENDING: NotificationEnvelope[] = [
  {
    dispatchId: 'd-mock-001',
    tier: 'full_screen_alarm',
    patientId: 'pac-marcio',
    commandLabel: 'Ajuda',
    urgencia: 'emergencia',
    raisedAt: new Date(Date.now() - 20 * 60 * 1000).toISOString(),
  },
];

@Injectable({ providedIn: 'root' })
export class DashNotificationsMockAdapter implements DashNotificationsPort {
  private pending: NotificationEnvelope[] = [...MOCK_PENDING];

  listUnacknowledged(): Observable<UnacknowledgedResponse> {
    return timer(400).pipe(
      switchMap(() => of({ notifications: [...this.pending] })),
    );
  }

  acknowledge(dispatchId: string): Observable<void> {
    // Client-side eviction — mock simulates 204 idempotent ack.
    this.pending = this.pending.filter((n) => n.dispatchId !== dispatchId);
    return timer(300).pipe(switchMap(() => of(void 0)));
  }

  poll(
    _waitSeconds: number,
    _cursor?: string,
  ): Observable<{ notifications: NotificationEnvelope[]; cursor: string }> {
    // Long-poll fallback: mock returns empty after 400ms (simulates timeout).
    return timer(400).pipe(switchMap(() => of({ notifications: [], cursor: 'mock-cursor' })));
  }
}
