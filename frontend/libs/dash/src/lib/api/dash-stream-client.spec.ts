import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { DashStreamClient } from './dash-stream-client';
import { DASH_NOTIFICATIONS_PORT } from './dash-notifications.port';
import { NotificationEnvelope } from '../dash.types';

// =============================================================================
// DashStreamClient spec — P3 (DASH-FE-002)
// Tests SSE lifecycle, exponential backoff reconnect, long-poll fallback,
// and close() cleanup. EventSource is mocked globally (JSDOM has no native SSE).
// Uses fake timers to control reconnect delays deterministically.
// =============================================================================

// ---------------------------------------------------------------------------
// Mock EventSource setup
// ---------------------------------------------------------------------------

type MockEventSourceInstance = {
  onopen: (() => void) | null;
  onerror: ((e: Event) => void) | null;
  onmessage: ((e: MessageEvent) => void) | null;
  addEventListener: jest.Mock;
  close: jest.Mock;
};

let mockEventSourceInstances: MockEventSourceInstance[];
let MockEventSource: jest.Mock;

const MOCK_ENVELOPE: NotificationEnvelope = {
  dispatchId: 'd-sse-001',
  tier: 'full_screen_alarm',
  patientId: 'pac-marcio',
  commandLabel: 'Ajuda',
  urgencia: 'emergencia',
  raisedAt: new Date('2026-08-19T10:00:00Z').toISOString(),
};

function createMockNotificationsPort() {
  return {
    listUnacknowledged: jest.fn(),
    acknowledge: jest.fn(),
    poll: jest.fn(),
  };
}

/** Returns the most recently created mock EventSource instance. */
function getLastInstance(): MockEventSourceInstance {
  return mockEventSourceInstances[mockEventSourceInstances.length - 1];
}

// ---------------------------------------------------------------------------
// Trigger helpers — avoid non-null assertions (eslint no-non-null-assertion)
// ---------------------------------------------------------------------------

function triggerOpen(): void {
  const h = getLastInstance().onopen;
  if (h == null) throw new Error('onopen handler was not set on MockEventSource');
  h();
}

function triggerError(): void {
  const h = getLastInstance().onerror;
  if (h == null) throw new Error('onerror handler was not set on MockEventSource');
  h({} as Event);
}

function triggerMessage(data: string): void {
  const h = getLastInstance().onmessage;
  if (h == null) throw new Error('onmessage handler was not set on MockEventSource');
  h({ data } as MessageEvent);
}

// ---------------------------------------------------------------------------
// Suite
// ---------------------------------------------------------------------------

describe('DashStreamClient', () => {
  let client: DashStreamClient;
  let mockNotificationsPort: ReturnType<typeof createMockNotificationsPort>;

  beforeEach(() => {
    jest.useFakeTimers();
    mockEventSourceInstances = [];

    MockEventSource = jest.fn().mockImplementation(() => {
      const instance: MockEventSourceInstance = {
        onopen: null,
        onerror: null,
        onmessage: null,
        addEventListener: jest.fn(),
        close: jest.fn(),
      };
      mockEventSourceInstances.push(instance);
      return instance;
    });

    (global as { EventSource?: unknown }).EventSource = MockEventSource;

    mockNotificationsPort = createMockNotificationsPort();

    TestBed.configureTestingModule({
      providers: [
        DashStreamClient,
        { provide: DASH_NOTIFICATIONS_PORT, useValue: mockNotificationsPort },
      ],
    });

    client = TestBed.inject(DashStreamClient);
  });

  afterEach(() => {
    client.close();
    TestBed.resetTestingModule();
    jest.useRealTimers();
    jest.clearAllTimers();
    delete (global as { EventSource?: unknown }).EventSource;
  });

  // ---------------------------------------------------------------------------
  // Initial state
  // ---------------------------------------------------------------------------

  describe('initial state', () => {
    it('status is disconnected', () => {
      expect(client.status()).toBe('disconnected');
    });

    it('isConnected is false', () => {
      expect(client.isConnected()).toBe(false);
    });

    it('fallbackActive is false', () => {
      expect(client.fallbackActive()).toBe(false);
    });

    it('error is null', () => {
      expect(client.error()).toBeNull();
    });
  });

  // ---------------------------------------------------------------------------
  // open() — connection lifecycle
  // ---------------------------------------------------------------------------

  describe('open()', () => {
    it('sets status to connecting immediately and creates EventSource with the given URL', () => {
      client.open('/panel/stream');

      expect(client.status()).toBe('connecting');
      expect(MockEventSource).toHaveBeenCalledWith('/panel/stream');
    });

    it('sets status to connected when EventSource.onopen fires', () => {
      client.open('/panel/stream');
      triggerOpen();

      expect(client.status()).toBe('connected');
      expect(client.isConnected()).toBe(true);
      expect(client.error()).toBeNull();
    });

    it('resets error signal to null when EventSource.onopen fires after a previous error', () => {
      client.open('/panel/stream');
      triggerError(); // error 1 → reconnect scheduled

      jest.advanceTimersByTime(2000);
      triggerOpen(); // reconnect succeeds

      expect(client.error()).toBeNull();
      expect(client.status()).toBe('connected');
    });

    it('is idempotent — no-op when status is not disconnected', () => {
      client.open('/panel/stream');
      triggerOpen();
      const callsBefore = MockEventSource.mock.calls.length;

      client.open('/panel/stream'); // must be no-op while connected

      expect(MockEventSource.mock.calls.length).toBe(callsBefore);
    });
  });

  // ---------------------------------------------------------------------------
  // onmessage — event parsing
  // ---------------------------------------------------------------------------

  describe('onmessage', () => {
    it('parses valid JSON and emits NotificationEnvelope to events$', () => {
      const received: NotificationEnvelope[] = [];
      client.events$.subscribe((e) => received.push(e));

      client.open('/panel/stream');
      triggerOpen();
      triggerMessage(JSON.stringify(MOCK_ENVELOPE));

      expect(received.length).toBe(1);
      expect(received[0].dispatchId).toBe(MOCK_ENVELOPE.dispatchId);
      expect(received[0].urgencia).toBe('emergencia');
    });

    it('ignores empty data (SSE :heartbeat comment lines produce empty data)', () => {
      const received: NotificationEnvelope[] = [];
      client.events$.subscribe((e) => received.push(e));

      client.open('/panel/stream');
      triggerOpen();
      triggerMessage('');

      expect(received.length).toBe(0);
    });

    it('ignores malformed JSON silently without throwing', () => {
      const received: NotificationEnvelope[] = [];
      client.events$.subscribe((e) => received.push(e));

      client.open('/panel/stream');
      triggerOpen();

      expect(() => triggerMessage('not-valid-json')).not.toThrow();
      expect(received.length).toBe(0);
    });
  });

  // ---------------------------------------------------------------------------
  // Reconnect with exponential backoff
  // ---------------------------------------------------------------------------

  describe('reconnect / exponential backoff', () => {
    it('first onerror sets status to disconnected and schedules reconnect after 2000ms', () => {
      client.open('/panel/stream');

      triggerError();

      expect(client.status()).toBe('disconnected');
      expect(client.fallbackActive()).toBe(false);
      expect(MockEventSource.mock.calls.length).toBe(1); // no reconnect yet

      jest.advanceTimersByTime(2000);

      expect(MockEventSource.mock.calls.length).toBe(2); // reconnected
    });

    it('second onerror schedules reconnect after 4000ms (exponential: 2^1 * 2000)', () => {
      client.open('/panel/stream');

      // Error 1 → wait 2s → reconnect
      triggerError();
      jest.advanceTimersByTime(2000);
      const callsAfterFirstReconnect = MockEventSource.mock.calls.length;

      // Error 2 → wait 4s
      triggerError();

      // Not yet reconnected at 3999ms
      jest.advanceTimersByTime(3999);
      expect(MockEventSource.mock.calls.length).toBe(callsAfterFirstReconnect);

      // Reconnects exactly at 4000ms mark
      jest.advanceTimersByTime(1);
      expect(MockEventSource.mock.calls.length).toBe(callsAfterFirstReconnect + 1);
    });

    it('third onerror (MAX_RECONNECT_ATTEMPTS) activates long-poll fallback', () => {
      mockNotificationsPort.poll.mockReturnValue(of({ notifications: [], cursor: 'c1' }));

      client.open('/panel/stream');

      // Error 1 → 2s → reconnect
      triggerError();
      jest.advanceTimersByTime(2000);

      // Error 2 → 4s → reconnect
      triggerError();
      jest.advanceTimersByTime(4000);

      // Error 3 → fallback activated (no more reconnect timer)
      triggerError();

      expect(client.status()).toBe('fallback');
      expect(client.fallbackActive()).toBe(true);
      expect(mockNotificationsPort.poll).toHaveBeenCalled();
      expect(client.error()?.code).toBe('SSE_MAX_RETRIES');
    });
  });

  // ---------------------------------------------------------------------------
  // close()
  // ---------------------------------------------------------------------------

  describe('close()', () => {
    it('closes EventSource and resets status to disconnected', () => {
      client.open('/panel/stream');
      triggerOpen();
      expect(client.status()).toBe('connected');

      client.close();

      expect(client.status()).toBe('disconnected');
      expect(client.isConnected()).toBe(false);
      expect(getLastInstance().close).toHaveBeenCalled();
    });

    it('is idempotent — safe to call when already disconnected', () => {
      expect(() => client.close()).not.toThrow();
      expect(client.status()).toBe('disconnected');
    });

    it('cancels pending reconnect timer — no reconnect attempt fires after close', () => {
      client.open('/panel/stream');
      triggerError(); // schedules 2s reconnect

      client.close();

      const callsBefore = MockEventSource.mock.calls.length;
      jest.advanceTimersByTime(5000); // advance past reconnect window

      expect(MockEventSource.mock.calls.length).toBe(callsBefore);
    });

    it('stops fallback polling and resets fallbackActive to false', () => {
      mockNotificationsPort.poll.mockReturnValue(of({ notifications: [], cursor: 'c' }));

      client.open('/panel/stream');
      // Trigger 3 errors to reach fallback
      triggerError();
      jest.advanceTimersByTime(2000);
      triggerError();
      jest.advanceTimersByTime(4000);
      triggerError();

      expect(client.fallbackActive()).toBe(true);

      client.close();

      expect(client.fallbackActive()).toBe(false);
      expect(client.status()).toBe('disconnected');
    });

    it('clears error signal after close()', () => {
      mockNotificationsPort.poll.mockReturnValue(of({ notifications: [], cursor: 'c' }));

      client.open('/panel/stream');
      // Trigger SSE_MAX_RETRIES error to populate the error signal
      triggerError();
      jest.advanceTimersByTime(2000);
      triggerError();
      jest.advanceTimersByTime(4000);
      triggerError();

      expect(client.error()).not.toBeNull();

      client.close();

      expect(client.error()).toBeNull();
    });
  });

  // ---------------------------------------------------------------------------
  // SSE unsupported (SSR / old browsers)
  // ---------------------------------------------------------------------------

  describe('SSE unsupported environment', () => {
    it('immediately activates fallback when EventSource is undefined', () => {
      delete (global as { EventSource?: unknown }).EventSource;
      mockNotificationsPort.poll.mockReturnValue(of({ notifications: [], cursor: 'c' }));

      client.open('/panel/stream');

      expect(client.status()).toBe('fallback');
      expect(client.fallbackActive()).toBe(true);
      expect(client.error()?.code).toBe('SSE_UNSUPPORTED');
    });
  });

  // ---------------------------------------------------------------------------
  // Long-poll fallback — event emission
  // ---------------------------------------------------------------------------

  describe('long-poll fallback', () => {
    it('emits notifications received via poll to events$', () => {
      const received: NotificationEnvelope[] = [];
      client.events$.subscribe((e) => received.push(e));

      delete (global as { EventSource?: unknown }).EventSource;
      mockNotificationsPort.poll.mockReturnValue(
        of({ notifications: [MOCK_ENVELOPE], cursor: 'c' }),
      );

      client.open('/panel/stream');

      expect(received.length).toBe(1);
      expect(received[0].dispatchId).toBe(MOCK_ENVELOPE.dispatchId);
    });

    it('calls poll() with waitSeconds=30 and initial cursor=undefined', () => {
      delete (global as { EventSource?: unknown }).EventSource;
      mockNotificationsPort.poll.mockReturnValue(
        of({ notifications: [], cursor: 'first-cursor' }),
      );

      client.open('/panel/stream');

      expect(mockNotificationsPort.poll).toHaveBeenCalledWith(30, undefined);
    });
  });

  // ---------------------------------------------------------------------------
  // Server-initiated close event
  // ---------------------------------------------------------------------------

  describe('server-initiated close event', () => {
    it('transitions to disconnected when server sends event: close', () => {
      client.open('/panel/stream');
      triggerOpen();
      expect(client.status()).toBe('connected');

      // Retrieve the 'close' event listener registered via addEventListener
      const addEventListenerCalls =
        getLastInstance().addEventListener.mock.calls as Array<[string, () => void]>;
      const closeCall = addEventListenerCalls.find((call) => call[0] === 'close');
      expect(closeCall).toBeDefined();

      if (closeCall) {
        const closeListener = closeCall[1];
        closeListener();
      }

      expect(client.status()).toBe('disconnected');
    });
  });
});
