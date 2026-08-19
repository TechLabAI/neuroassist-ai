import { DashNotificationsMockAdapter } from './dash-notifications-mock.adapter';
import { NotificationEnvelope, UnacknowledgedResponse } from '../dash.types';

// =============================================================================
// DashNotificationsMockAdapter spec — P2 (DASH-FE-003)
// Tests the dev/test mock adapter contract without a real backend.
// Uses fake timers to advance past the 400ms/300ms Observable delays.
// acknowledge() mutates internal state synchronously (before timer fires),
// so a subsequent listUnacknowledged() will not include the ack-ed item.
// =============================================================================

describe('DashNotificationsMockAdapter', () => {
  let adapter: DashNotificationsMockAdapter;

  beforeEach(() => {
    adapter = new DashNotificationsMockAdapter();
    jest.useFakeTimers();
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  // -------------------------------------------------------------------------
  // listUnacknowledged()
  // -------------------------------------------------------------------------

  describe('listUnacknowledged()', () => {
    it('returns an Observable that emits a non-empty notifications list', () => {
      let result: UnacknowledgedResponse | null = null;
      adapter.listUnacknowledged().subscribe((r) => {
        result = r;
      });

      jest.advanceTimersByTime(500);

      expect(result).not.toBeNull();
      expect((result as UnacknowledgedResponse).notifications.length).toBeGreaterThan(0);
    });

    it('each notification has required fields (dispatchId, tier, patientId, commandLabel, urgencia)', () => {
      let result: UnacknowledgedResponse | null = null;
      adapter.listUnacknowledged().subscribe((r) => {
        result = r;
      });

      jest.advanceTimersByTime(500);

      const notification = (result as UnacknowledgedResponse).notifications[0];
      expect(notification.dispatchId).toBeTruthy();
      expect(notification.tier).toBeTruthy();
      expect(notification.patientId).toBeTruthy();
      expect(notification.commandLabel).toBeTruthy();
      expect(notification.urgencia).toBeTruthy();
    });
  });

  // -------------------------------------------------------------------------
  // acknowledge()
  // -------------------------------------------------------------------------

  describe('acknowledge()', () => {
    it('acknowledge(id) returns an Observable<void> that completes successfully', () => {
      let completed = false;
      adapter.acknowledge('d-mock-001').subscribe({
        complete: () => {
          completed = true;
        },
      });

      jest.advanceTimersByTime(500);

      expect(completed).toBe(true);
    });

    it('acknowledge(id) removes the item — subsequent listUnacknowledged does not include it', () => {
      // Step 1: capture the initial list
      let firstResult: UnacknowledgedResponse | null = null;
      adapter.listUnacknowledged().subscribe((r) => {
        firstResult = r;
      });
      jest.advanceTimersByTime(500);

      const initialNotifications = (firstResult as UnacknowledgedResponse).notifications;
      expect(initialNotifications.length).toBeGreaterThan(0);
      const idToAck = initialNotifications[0].dispatchId;

      // Step 2: acknowledge the first item (mutates internal state synchronously)
      adapter.acknowledge(idToAck).subscribe();

      // Step 3: list again — the acknowledged item must not be present
      let secondResult: UnacknowledgedResponse | null = null;
      adapter.listUnacknowledged().subscribe((r) => {
        secondResult = r;
      });
      jest.advanceTimersByTime(500);

      const remaining = (secondResult as UnacknowledgedResponse).notifications;
      expect(
        remaining.every((n: NotificationEnvelope) => n.dispatchId !== idToAck),
      ).toBe(true);
    });
  });

  // -------------------------------------------------------------------------
  // poll()
  // -------------------------------------------------------------------------

  describe('poll()', () => {
    it('poll() returns an Observable with notifications array and cursor string', () => {
      let result: { notifications: NotificationEnvelope[]; cursor: string } | null = null;
      adapter.poll(30).subscribe((r) => {
        result = r;
      });

      jest.advanceTimersByTime(500);

      expect(result).not.toBeNull();
      expect(Array.isArray((result as { notifications: NotificationEnvelope[]; cursor: string }).notifications)).toBe(true);
      expect(typeof (result as { notifications: NotificationEnvelope[]; cursor: string }).cursor).toBe('string');
    });
  });
});
