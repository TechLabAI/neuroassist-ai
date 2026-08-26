// =============================================================================
// dash-notifications.contract.spec.ts — CTR-12 (FE-E2E-005 / P3)
//
// Verifica que DashNotificationsMockAdapter e DashNotificationsHttpAdapter
// satisfazem DashNotificationsPort com o mesmo shape de dados.
//
// Base path (normalizado em P2 / ADR-008): /api/v1/dash/notifications
// Previously: /panel/notifications (direct service path, pre-ADR-008)
// =============================================================================

import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  provideHttpClientTesting,
  HttpTestingController,
} from '@angular/common/http/testing';
import { DashNotificationsPort } from './dash-notifications.port';
import { DashNotificationsHttpAdapter } from './dash-notifications-http.adapter';
import { DashNotificationsMockAdapter } from './dash-notifications-mock.adapter';
import type { UnacknowledgedResponse, NotificationEnvelope } from '../dash.types';

const STUB_NOTIFICATION: NotificationEnvelope = {
  dispatchId: 'd-test-001',
  tier: 'full_screen_alarm',
  patientId: 'pac-marcio',
  commandLabel: 'Ajuda',
  urgencia: 'emergencia',
  raisedAt: '2026-08-22T09:40:00Z',
};

const STUB_UNACKNOWLEDGED: UnacknowledgedResponse = {
  notifications: [STUB_NOTIFICATION],
};

describe.each([
  ['mock' as const, () => new DashNotificationsMockAdapter()],
  ['http' as const, () => TestBed.inject(DashNotificationsHttpAdapter)],
])('%s satisfaz DashNotificationsPort', (label, adapterFactory) => {
  let adapter: DashNotificationsPort;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        DashNotificationsHttpAdapter,
        DashNotificationsMockAdapter,
      ],
    });
    controller = TestBed.inject(HttpTestingController);
    adapter = adapterFactory();
  });

  afterEach(() => {
    controller.verify();
    TestBed.resetTestingModule();
  });

  // ---------------------------------------------------------------------------
  // listUnacknowledged()
  // ---------------------------------------------------------------------------

  it('listUnacknowledged() retorna UnacknowledgedResponse com shape correto', fakeAsync(() => {
    let result: UnacknowledgedResponse | undefined;
    let error: unknown;

    adapter.listUnacknowledged().subscribe({
      next: r => { result = r; },
      error: e => { error = e; },
    });

    if (label === 'http') {
      const req = controller.expectOne('/api/v1/dash/notifications/unacknowledged');
      expect(req.request.method).toBe('GET');
      req.flush(STUB_UNACKNOWLEDGED);
    } else {
      tick(1000);
    }

    expect(error).toBeUndefined();
    expect(result).toMatchObject({
      notifications: expect.any(Array),
    });

    if (result!.notifications.length > 0) {
      expect(result!.notifications[0]).toMatchObject({
        dispatchId: expect.any(String),
        tier: expect.stringMatching(/^(full_screen_alarm|discreet)$/),
        patientId: expect.any(String),
        commandLabel: expect.any(String),
        urgencia: expect.stringMatching(/^(emergencia|rotina)$/),
        raisedAt: expect.any(String),
      });
    }
  }));

  // ---------------------------------------------------------------------------
  // acknowledge()
  // ---------------------------------------------------------------------------

  it('acknowledge() retorna void (Observable<void>)', fakeAsync(() => {
    let completed = false;
    let error: unknown;

    adapter.acknowledge('d-mock-001').subscribe({
      next: () => { completed = true; },
      complete: () => { completed = true; },
      error: e => { error = e; },
    });

    if (label === 'http') {
      const req = controller.expectOne('/api/v1/dash/notifications/d-mock-001/acknowledge');
      expect(req.request.method).toBe('POST');
      req.flush(null, { status: 204, statusText: 'No Content' });
    } else {
      tick(1000);
    }

    expect(error).toBeUndefined();
    expect(completed).toBe(true);
  }));

  // ---------------------------------------------------------------------------
  // poll()
  // ---------------------------------------------------------------------------

  it('poll() retorna { notifications, cursor } com shape correto', fakeAsync(() => {
    let result: { notifications: NotificationEnvelope[]; cursor: string } | undefined;
    let error: unknown;

    adapter.poll(30).subscribe({
      next: r => { result = r; },
      error: e => { error = e; },
    });

    if (label === 'http') {
      const req = controller.expectOne(r =>
        r.url.startsWith('/api/v1/dash/notifications/poll') && r.method === 'GET',
      );
      req.flush({ notifications: [STUB_NOTIFICATION], cursor: 'cursor-001' });
    } else {
      tick(1000);
    }

    expect(error).toBeUndefined();
    expect(result).toMatchObject({
      notifications: expect.any(Array),
      cursor: expect.any(String),
    });
  }));
});
