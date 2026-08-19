import { DashSummaryMockAdapter } from './dash-summary-mock.adapter';
import { DashSummaryResponse } from '../dash.types';

// =============================================================================
// DashSummaryMockAdapter spec — P2 (DASH-FE-003)
// Tests the dev/test mock adapter contract without a real backend.
// Uses fake timers to advance past the 400ms Observable delay.
// =============================================================================

describe('DashSummaryMockAdapter', () => {
  let adapter: DashSummaryMockAdapter;

  beforeEach(() => {
    adapter = new DashSummaryMockAdapter();
    jest.useFakeTimers();
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  // -------------------------------------------------------------------------
  // getSummary() — contract assertions
  // -------------------------------------------------------------------------

  it('getSummary() returns an Observable that emits a DashSummaryResponse with tenantId', () => {
    let result: DashSummaryResponse | null = null;
    adapter.getSummary().subscribe((r) => {
      result = r;
    });

    jest.advanceTimersByTime(500);

    expect(result).not.toBeNull();
    expect((result as DashSummaryResponse).tenantId).toBe('tenant-familia-amaral');
  });

  it('getSummary() response contains a caregiver with non-empty name and id', () => {
    let result: DashSummaryResponse | null = null;
    adapter.getSummary().subscribe((r) => {
      result = r;
    });

    jest.advanceTimersByTime(500);

    const caregiver = (result as DashSummaryResponse).caregiver;
    expect(caregiver.id).toBeTruthy();
    expect(caregiver.name).toBeTruthy();
    expect(caregiver.role).toBeTruthy();
  });

  it('getSummary() response has at least one patient with required fields', () => {
    let result: DashSummaryResponse | null = null;
    adapter.getSummary().subscribe((r) => {
      result = r;
    });

    jest.advanceTimersByTime(500);

    const patients = (result as DashSummaryResponse).patients;
    expect(patients.length).toBeGreaterThan(0);
    expect(patients[0].patientId).toBeTruthy();
    expect(patients[0].name).toBeTruthy();
    expect(patients[0].device).toBeTruthy();
  });

  it('getSummary() response contains a degradedSections array', () => {
    let result: DashSummaryResponse | null = null;
    adapter.getSummary().subscribe((r) => {
      result = r;
    });

    jest.advanceTimersByTime(500);

    expect(Array.isArray((result as DashSummaryResponse).degradedSections)).toBe(true);
  });
});
