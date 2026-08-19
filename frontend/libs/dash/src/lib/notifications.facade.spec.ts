import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { of, throwError } from 'rxjs';
import { NotificationsFacade } from './notifications.facade';
import { DASH_NOTIFICATIONS_PORT } from './api/dash-notifications.port';
import { DashStreamClient } from './api/dash-stream-client';
import { NotificationEnvelope, UnacknowledgedResponse } from './dash.types';

// =============================================================================
// NotificationsFacade spec — P2 (DASH-FE-003)
// Tests facade signals + async methods with immediate Observable mocks.
// acknowledge() is non-optimistic: evicts only after port confirms.
// =============================================================================

// ---------------------------------------------------------------------------
// Mock data helpers
// ---------------------------------------------------------------------------

const MOCK_NOTIFICATION_1: NotificationEnvelope = {
  dispatchId: 'd-mock-001',
  tier: 'full_screen_alarm',
  patientId: 'pac-marcio',
  commandLabel: 'Ajuda',
  urgencia: 'emergencia',
  raisedAt: new Date('2026-08-19T10:00:00Z').toISOString(),
};

const MOCK_NOTIFICATION_2: NotificationEnvelope = {
  dispatchId: 'd-mock-002',
  tier: 'discreet',
  patientId: 'pac-marcio',
  commandLabel: 'Água',
  urgencia: 'rotina',
  raisedAt: new Date('2026-08-19T09:30:00Z').toISOString(),
};

const TWO_NOTIFICATIONS: UnacknowledgedResponse = {
  notifications: [MOCK_NOTIFICATION_1, MOCK_NOTIFICATION_2],
};

const ONE_NOTIFICATION: UnacknowledgedResponse = {
  notifications: [MOCK_NOTIFICATION_1],
};

function createMockNotificationsPort() {
  return {
    listUnacknowledged: jest.fn(),
    acknowledge: jest.fn(),
    poll: jest.fn(),
  };
}

function createMockStreamClient() {
  return {
    events$: new Subject<NotificationEnvelope>(),
    open: jest.fn(),
    close: jest.fn(),
  };
}

// ---------------------------------------------------------------------------
// Suite
// ---------------------------------------------------------------------------

describe('NotificationsFacade', () => {
  let facade: NotificationsFacade;
  let mockNotificationsPort: ReturnType<typeof createMockNotificationsPort>;
  let mockStreamClient: ReturnType<typeof createMockStreamClient>;

  beforeEach(() => {
    mockNotificationsPort = createMockNotificationsPort();
    mockStreamClient = createMockStreamClient();

    TestBed.configureTestingModule({
      providers: [
        NotificationsFacade,
        { provide: DASH_NOTIFICATIONS_PORT, useValue: mockNotificationsPort },
        { provide: DashStreamClient, useValue: mockStreamClient },
      ],
    });
    facade = TestBed.inject(NotificationsFacade);
  });

  afterEach(() => TestBed.resetTestingModule());

  // -------------------------------------------------------------------------
  // load() — success path
  // -------------------------------------------------------------------------

  it('load() sets status to loaded and populates notifications signal', async () => {
    mockNotificationsPort.listUnacknowledged.mockReturnValue(of(TWO_NOTIFICATIONS));

    await facade.load();

    expect(facade.status()).toBe('loaded');
    expect(facade.notifications().length).toBe(2);
    expect(facade.error()).toBeNull();
  });

  // -------------------------------------------------------------------------
  // load() — empty list
  // -------------------------------------------------------------------------

  it('load() with empty list sets status to empty', async () => {
    mockNotificationsPort.listUnacknowledged.mockReturnValue(
      of({ notifications: [] }),
    );

    await facade.load();

    expect(facade.status()).toBe('empty');
    expect(facade.notifications().length).toBe(0);
  });

  // -------------------------------------------------------------------------
  // load() — error path
  // -------------------------------------------------------------------------

  it('load() with port error sets status to error and populates error signal', async () => {
    mockNotificationsPort.listUnacknowledged.mockReturnValue(
      throwError(() => new Error('Load failed')),
    );

    await facade.load();

    expect(facade.status()).toBe('error');
    expect(facade.error()).not.toBeNull();
    expect(facade.error()?.code).toBe('UNKNOWN');
  });

  // -------------------------------------------------------------------------
  // acknowledge() — non-optimistic remove
  // -------------------------------------------------------------------------

  it('acknowledge(id) removes notification from list after port confirms (non-optimistic)', async () => {
    mockNotificationsPort.listUnacknowledged.mockReturnValue(of(TWO_NOTIFICATIONS));
    mockNotificationsPort.acknowledge.mockReturnValue(of(void 0));

    await facade.load();
    expect(facade.notifications().length).toBe(2);

    await facade.acknowledge('d-mock-001');

    expect(facade.notifications().length).toBe(1);
    expect(facade.notifications()[0].dispatchId).toBe('d-mock-002');
  });

  // -------------------------------------------------------------------------
  // acknowledge() — list becomes empty → status = 'empty'
  // -------------------------------------------------------------------------

  it('acknowledge(id) when list becomes empty sets status to empty', async () => {
    mockNotificationsPort.listUnacknowledged.mockReturnValue(of(ONE_NOTIFICATION));
    mockNotificationsPort.acknowledge.mockReturnValue(of(void 0));

    await facade.load();
    await facade.acknowledge('d-mock-001');

    expect(facade.status()).toBe('empty');
    expect(facade.notifications().length).toBe(0);
  });

  // -------------------------------------------------------------------------
  // acknowledge() — error is non-fatal
  // -------------------------------------------------------------------------

  it('acknowledge(id) with port error is non-fatal — notification stays in list', async () => {
    jest.spyOn(console, 'error').mockImplementation(jest.fn());
    mockNotificationsPort.listUnacknowledged.mockReturnValue(of(TWO_NOTIFICATIONS));
    mockNotificationsPort.acknowledge.mockReturnValue(
      throwError(() => new Error('Ack failed')),
    );

    await facade.load();
    await facade.acknowledge('d-mock-001');

    expect(facade.notifications().length).toBe(2);
    expect(facade.status()).toBe('loaded');
    jest.restoreAllMocks();
  });

  // -------------------------------------------------------------------------
  // retry() — re-runs load()
  // -------------------------------------------------------------------------

  it('retry() re-calls load() — port.listUnacknowledged invoked twice', async () => {
    mockNotificationsPort.listUnacknowledged.mockReturnValue(of(TWO_NOTIFICATIONS));

    await facade.load();
    await facade.retry();

    expect(mockNotificationsPort.listUnacknowledged).toHaveBeenCalledTimes(2);
    expect(facade.status()).toBe('loaded');
  });

  // -------------------------------------------------------------------------
  // count computed
  // -------------------------------------------------------------------------

  it('count computed equals notifications().length', async () => {
    mockNotificationsPort.listUnacknowledged.mockReturnValue(of(TWO_NOTIFICATIONS));

    await facade.load();

    expect(facade.count()).toBe(2);
    expect(facade.count()).toBe(facade.notifications().length);
  });

  // -------------------------------------------------------------------------
  // screenVm computed
  // -------------------------------------------------------------------------

  it('screenVm computed reflects current status, notifications, and error', async () => {
    mockNotificationsPort.listUnacknowledged.mockReturnValue(of(TWO_NOTIFICATIONS));

    await facade.load();

    const vm = facade.screenVm();
    expect(vm.status).toBe('loaded');
    expect(vm.notifications.length).toBe(2);
    expect(vm.error).toBeNull();
  });

  // -------------------------------------------------------------------------
  // DashStreamClient integration (DASH-FE-002)
  // -------------------------------------------------------------------------

  it('load() calls streamClient.open() with /panel/stream after successful HTTP catch-up', async () => {
    mockNotificationsPort.listUnacknowledged.mockReturnValue(of(TWO_NOTIFICATIONS));

    await facade.load();

    expect(mockStreamClient.open).toHaveBeenCalledWith('/panel/stream');
    expect(mockStreamClient.open).toHaveBeenCalledTimes(1);
  });

  it('stream events from DashStreamClient.events$ are accumulated into notifications signal', async () => {
    mockNotificationsPort.listUnacknowledged.mockReturnValue(
      of({ notifications: [] }),
    );

    await facade.load();
    expect(facade.notifications().length).toBe(0);
    expect(facade.status()).toBe('empty');

    const streamEnvelope: NotificationEnvelope = {
      dispatchId: 'd-stream-001',
      tier: 'full_screen_alarm',
      patientId: 'pac-marcio',
      commandLabel: 'Emergência',
      urgencia: 'emergencia',
      raisedAt: new Date('2026-08-19T11:00:00Z').toISOString(),
    };

    // Emit via SSE/long-poll fallback
    mockStreamClient.events$.next(streamEnvelope);

    expect(facade.notifications().length).toBe(1);
    expect(facade.notifications()[0].dispatchId).toBe('d-stream-001');
    expect(facade.status()).toBe('loaded');
  });

  it('duplicate stream events (same dispatchId) are ignored (deduplication)', async () => {
    mockNotificationsPort.listUnacknowledged.mockReturnValue(
      of({ notifications: [] }),
    );

    await facade.load();

    const streamEnvelope: NotificationEnvelope = {
      dispatchId: 'd-dedup-001',
      tier: 'discreet',
      patientId: 'pac-marcio',
      commandLabel: 'Água',
      urgencia: 'rotina',
      raisedAt: new Date('2026-08-19T11:00:00Z').toISOString(),
    };

    mockStreamClient.events$.next(streamEnvelope);
    mockStreamClient.events$.next(streamEnvelope); // duplicate

    expect(facade.notifications().length).toBe(1);
  });
});
