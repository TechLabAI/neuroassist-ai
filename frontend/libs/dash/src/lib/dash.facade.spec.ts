import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { DashHomeFacade } from './dash.facade';
import { DASH_SUMMARY_PORT } from './api/dash-summary.port';
import { DASH_ACTIVE_EVENT_TOKEN, ActiveEventVm } from './access/dash-access-tokens';
import { DashSummaryResponse } from './dash.types';

// =============================================================================
// DashHomeFacade spec — P2 (DASH-FE-003)
// Tests facade signals + async methods with immediate Observable mocks.
// No timer delay — port mocks use of() directly for fast unit tests.
// =============================================================================

// ---------------------------------------------------------------------------
// Mock data helpers
// ---------------------------------------------------------------------------

const MOCK_PATIENT = {
  patientId: 'pac-marcio',
  name: 'Márcio Amaral',
  device: { status: 'online' as const, lastSeenAt: null, degraded: false },
  latestEvent: null,
  unacknowledgedCount: 0,
};

const MOCK_SUMMARY: DashSummaryResponse = {
  tenantId: 'tenant-familia-amaral',
  caregiver: { id: 'cuid-jamilly', name: 'Jamilly Amaral', role: 'cuidadora' },
  patients: [MOCK_PATIENT],
  degradedSections: [],
};

function createMockSummaryPort() {
  return { getSummary: jest.fn() };
}

// ---------------------------------------------------------------------------
// Suite
// ---------------------------------------------------------------------------

describe('DashHomeFacade', () => {
  let facade: DashHomeFacade;
  let mockSummaryPort: ReturnType<typeof createMockSummaryPort>;

  beforeEach(() => {
    mockSummaryPort = createMockSummaryPort();

    TestBed.configureTestingModule({
      providers: [
        DashHomeFacade,
        { provide: DASH_SUMMARY_PORT, useValue: mockSummaryPort },
      ],
    });
    facade = TestBed.inject(DashHomeFacade);
  });

  afterEach(() => TestBed.resetTestingModule());

  // -------------------------------------------------------------------------
  // load() — success path
  // -------------------------------------------------------------------------

  it('load() sets status to loaded and populates patient and caregiverName signals', async () => {
    mockSummaryPort.getSummary.mockReturnValue(of(MOCK_SUMMARY));

    await facade.load();

    expect(facade.status()).toBe('loaded');
    expect(facade.patient()).not.toBeNull();
    expect(facade.patient()?.patientId).toBe('pac-marcio');
    expect(facade.caregiverName()).toBe('Jamilly Amaral');
    expect(facade.error()).toBeNull();
  });

  // -------------------------------------------------------------------------
  // load() — empty patients → 'empty' status
  // -------------------------------------------------------------------------

  it('load() with empty patients list sets status to empty', async () => {
    mockSummaryPort.getSummary.mockReturnValue(
      of({ ...MOCK_SUMMARY, patients: [] }),
    );

    await facade.load();

    expect(facade.status()).toBe('empty');
    expect(facade.patient()).toBeNull();
  });

  // -------------------------------------------------------------------------
  // load() — error path
  // -------------------------------------------------------------------------

  it('load() with port error sets status to error and populates error signal', async () => {
    mockSummaryPort.getSummary.mockReturnValue(
      throwError(() => new Error('Network failure')),
    );

    await facade.load();

    expect(facade.status()).toBe('error');
    expect(facade.error()).not.toBeNull();
    expect(facade.error()?.code).toBe('UNKNOWN');
    expect(facade.error()?.message).toBe('Network failure');
  });

  // -------------------------------------------------------------------------
  // loading computed — true while executing, false afterwards
  // -------------------------------------------------------------------------

  it('loading computed is true while load() is executing, false afterwards', async () => {
    let capturedLoading = false;
    mockSummaryPort.getSummary.mockImplementation(() => {
      // At the moment getSummary() is called, #status is already 'loading'
      capturedLoading = facade.loading();
      return of(MOCK_SUMMARY);
    });

    await facade.load();

    expect(capturedLoading).toBe(true);
    expect(facade.loading()).toBe(false);
  });

  // -------------------------------------------------------------------------
  // retry() — re-runs load()
  // -------------------------------------------------------------------------

  it('retry() re-calls load() — summaryPort invoked twice', async () => {
    mockSummaryPort.getSummary.mockReturnValue(of(MOCK_SUMMARY));

    await facade.load();
    await facade.retry();

    expect(mockSummaryPort.getSummary).toHaveBeenCalledTimes(2);
    expect(facade.status()).toBe('loaded');
  });

  // -------------------------------------------------------------------------
  // hasDegradedSections computed
  // -------------------------------------------------------------------------

  it('hasDegradedSections is true when degradedSections.length > 0', async () => {
    mockSummaryPort.getSummary.mockReturnValue(
      of({ ...MOCK_SUMMARY, degradedSections: ['evnt', 'edge'] }),
    );

    await facade.load();

    expect(facade.hasDegradedSections()).toBe(true);
  });

  it('hasDegradedSections is false when degradedSections is empty', async () => {
    mockSummaryPort.getSummary.mockReturnValue(of(MOCK_SUMMARY));

    await facade.load();

    expect(facade.hasDegradedSections()).toBe(false);
  });

  // -------------------------------------------------------------------------
  // screenVm computed — aggregates all signals
  // -------------------------------------------------------------------------

  it('screenVm computed reflects status, summary, and error after load()', async () => {
    mockSummaryPort.getSummary.mockReturnValue(of(MOCK_SUMMARY));

    await facade.load();

    const vm = facade.screenVm();
    expect(vm.status).toBe('loaded');
    expect(vm.summary?.tenantId).toBe('tenant-familia-amaral');
    expect(vm.error).toBeNull();
  });
});

// =============================================================================
// calm security invariant — INT-FE-DASH-001
//
// calm must be derived EXCLUSIVELY from DASH_ACTIVE_EVENT_TOKEN (EVNT signal).
// It must NEVER be derived from unacknowledgedCount, latestEvent, or any
// aggregate in DashSummaryResponse. These tests enforce the invariant.
// =============================================================================

describe('DashHomeFacade — calm security invariant (INT-FE-DASH-001)', () => {
  let facade: DashHomeFacade;
  let mockSummaryPort: { getSummary: jest.Mock };

  // -------------------------------------------------------------------------
  // Helper: create the facade with a specific activeEvent signal value.
  // -------------------------------------------------------------------------

  function buildFacade(activeEvent: ActiveEventVm | null): DashHomeFacade {
    mockSummaryPort = { getSummary: jest.fn() };
    TestBed.configureTestingModule({
      providers: [
        DashHomeFacade,
        { provide: DASH_SUMMARY_PORT, useValue: mockSummaryPort },
        { provide: DASH_ACTIVE_EVENT_TOKEN, useValue: signal(activeEvent) },
      ],
    });
    return TestBed.inject(DashHomeFacade);
  }

  afterEach(() => TestBed.resetTestingModule());

  // -------------------------------------------------------------------------
  // calm = true when activeEvent is null (safe default — no alarm)
  // -------------------------------------------------------------------------

  it('calm() is true when DASH_ACTIVE_EVENT_TOKEN resolves to signal(null)', () => {
    facade = buildFacade(null);
    expect(facade.calm()).toBe(true);
  });

  // -------------------------------------------------------------------------
  // calm = false when activeEvent has an active emergency
  // -------------------------------------------------------------------------

  it('calm() is false when DASH_ACTIVE_EVENT_TOKEN resolves to a signal with an emergencia event', () => {
    const activeEvent: ActiveEventVm = {
      eventId: 'evt-001',
      urgencia: 'emergencia',
      patientId: 'pac-marcio',
    };
    facade = buildFacade(activeEvent);
    expect(facade.calm()).toBe(false);
  });

  // -------------------------------------------------------------------------
  // calm = false for rotina events too (any active event = not calm)
  // -------------------------------------------------------------------------

  it('calm() is false when DASH_ACTIVE_EVENT_TOKEN resolves to a signal with a rotina event', () => {
    const activeEvent: ActiveEventVm = {
      eventId: 'evt-002',
      urgencia: 'rotina',
      patientId: 'pac-marcio',
    };
    facade = buildFacade(activeEvent);
    expect(facade.calm()).toBe(false);
  });

  // -------------------------------------------------------------------------
  // calm is independent of summary state — the invariant must hold even if
  // the summary has no unacknowledged notifications (they are different signals).
  // -------------------------------------------------------------------------

  it('calm() is false even when unacknowledgedCount is 0 but activeEvent is set', async () => {
    const activeEvent: ActiveEventVm = {
      eventId: 'evt-003',
      urgencia: 'emergencia',
      patientId: 'pac-marcio',
    };
    facade = buildFacade(activeEvent);

    // Summary with 0 unacknowledged notifications — calm must still be false
    mockSummaryPort.getSummary.mockReturnValue(
      of({
        ...{
          tenantId: 'tenant-familia-amaral',
          caregiver: { id: 'cuid-jamilly', name: 'Jamilly Amaral', role: 'cuidadora' },
          patients: [
            {
              patientId: 'pac-marcio',
              name: 'Márcio Amaral',
              device: { status: 'online' as const, lastSeenAt: null, degraded: false },
              latestEvent: null,
              unacknowledgedCount: 0,
            },
          ],
          degradedSections: [],
        },
      }),
    );

    await facade.load();

    expect(facade.unacknowledgedCount()).toBe(0);
    // Safety invariant: calm must be false because EVNT has an active event,
    // regardless of the summary aggregate saying there are no notifications.
    expect(facade.calm()).toBe(false);
  });

  // -------------------------------------------------------------------------
  // calm is true even when summary has unacknowledged notifications but
  // EVNT has no active event — calm comes from EVNT, not from the aggregate.
  // -------------------------------------------------------------------------

  it('calm() is true when activeEvent is null even if summary has unacknowledgedCount > 0', async () => {
    facade = buildFacade(null);

    mockSummaryPort.getSummary.mockReturnValue(
      of({
        tenantId: 'tenant-familia-amaral',
        caregiver: { id: 'cuid-jamilly', name: 'Jamilly Amaral', role: 'cuidadora' },
        patients: [
          {
            patientId: 'pac-marcio',
            name: 'Márcio Amaral',
            device: { status: 'online' as const, lastSeenAt: null, degraded: false },
            latestEvent: null,
            unacknowledgedCount: 5,
          },
        ],
        degradedSections: [],
      }),
    );

    await facade.load();

    expect(facade.unacknowledgedCount()).toBe(5);
    // calm must still be true because EVNT reports no active event.
    expect(facade.calm()).toBe(true);
  });
});
