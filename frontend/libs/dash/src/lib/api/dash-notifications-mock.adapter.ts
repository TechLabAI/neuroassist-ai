import { Injectable } from '@angular/core';
import { Observable, of, throwError, timer } from 'rxjs';
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

// =============================================================================
// E2E flag helpers (localStorage — browser-only, best-effort)
// Flags set via page.addInitScript(() => localStorage.setItem('__DASH_E2E_*__', 'true'))
// before Angular bootstraps.
//
//  __DASH_E2E_NOTIFY_FAIL__  : listUnacknowledged() throws → facade shows error state (G5b)
//  __DASH_E2E_NOTIFY_EMPTY__ : listUnacknowledged() returns empty list → "Tudo revisado" (G5a)
// =============================================================================

function e2eFlag(name: string): boolean {
  try {
    return typeof localStorage !== 'undefined' && localStorage.getItem(name) === 'true';
  } catch {
    return false;
  }
}

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
      switchMap(() => {
        // E2E: force error to test error state inline (FE-DASH-026, G5b)
        if (e2eFlag('__DASH_E2E_NOTIFY_FAIL__')) {
          return throwError(() =>
            Object.assign(new Error('Erro ao carregar notificações.'), { status: 503 }),
          );
        }
        // E2E: force empty list to test "Tudo revisado" state (FE-DASH-026, G5a)
        if (e2eFlag('__DASH_E2E_NOTIFY_EMPTY__')) {
          return of({ notifications: [] as NotificationEnvelope[] });
        }
        return of({ notifications: [...this.pending] });
      }),
    );
  }

  acknowledge(dispatchId: string): Observable<void> {
    // Client-side eviction — mock simulates 204 idempotent ack.
    this.pending = this.pending.filter((n) => n.dispatchId !== dispatchId);
    return timer(300).pipe(switchMap(() => of(void 0)));
  }

  poll(
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    _waitSeconds: number,
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    _cursor?: string,
  ): Observable<{ notifications: NotificationEnvelope[]; cursor: string }> {
    // Long-poll fallback: mock returns empty after 400ms (simulates timeout).
    return timer(400).pipe(switchMap(() => of({ notifications: [], cursor: 'mock-cursor' })));
  }
}
