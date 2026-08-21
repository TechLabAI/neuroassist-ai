import { HistClientMockAdapter } from './hist-client-mock.adapter';
import {
  AuditRecordResponse,
  ChainVerificationResult,
  HistoryQueryParams,
  PagedResponse,
} from '../hist.types';

// =============================================================================
// HistClientMockAdapter spec — P2 (FE-HIST-002)
// Tests the dev/test mock adapter contract without backend.
// Uses fake timers to advance past the 400ms/600ms Observable delays.
// =============================================================================

/** Wide window that captures all 4 MOCK_RECORDS (isoNDaysAgo relative to 2026-08-16). */
const WIDE_PARAMS: HistoryQueryParams = {
  from: new Date('2020-01-01').toISOString(),
  to: new Date('2030-01-01').toISOString(),
  order: 'desc',
  page: 0,
  size: 50,
};

/** Narrow window — past dates that exclude all mock records to test filter. */
const EMPTY_PARAMS: HistoryQueryParams = {
  from: new Date('2010-01-01').toISOString(),
  to: new Date('2010-01-02').toISOString(),
  order: 'desc',
  page: 0,
  size: 50,
};

const DEFAULT_PAGED: PagedResponse<AuditRecordResponse> = {
  items: [],
  page: 0,
  size: 50,
  total: 0,
};

describe('HistClientMockAdapter', () => {
  let adapter: HistClientMockAdapter;

  beforeEach(() => {
    adapter = new HistClientMockAdapter();
    jest.useFakeTimers();
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  // -------------------------------------------------------------------------
  // list()
  // -------------------------------------------------------------------------

  describe('list()', () => {
    it('returns an Observable with a paged response containing items and pagination', () => {
      let result: PagedResponse<AuditRecordResponse> = DEFAULT_PAGED;
      adapter.list(WIDE_PARAMS).subscribe((r) => {
        result = r;
      });

      jest.advanceTimersByTime(500);

      expect(result.items.length).toBeGreaterThan(0);
      expect(typeof result.page).toBe('number');
      expect(typeof result.size).toBe('number');
      expect(typeof result.total).toBe('number');
    });

    it('emits only records within the time window (filter behavior)', () => {
      let result: PagedResponse<AuditRecordResponse> = DEFAULT_PAGED;
      adapter.list(EMPTY_PARAMS).subscribe((r) => {
        result = r;
      });

      jest.advanceTimersByTime(500);

      expect(result.items).toHaveLength(0);
    });

    it('returns records sorted descending by recordedAt when order=desc', () => {
      let result: PagedResponse<AuditRecordResponse> = DEFAULT_PAGED;
      adapter.list({ ...WIDE_PARAMS, order: 'desc' }).subscribe((r) => {
        result = r;
      });

      jest.advanceTimersByTime(500);

      const items = result.items;
      expect(items.length).toBeGreaterThan(0);
      for (let i = 1; i < items.length; i++) {
        expect(items[i - 1].recordedAt >= items[i].recordedAt).toBe(true);
      }
    });
  });

  // -------------------------------------------------------------------------
  // get()
  // -------------------------------------------------------------------------

  describe('get()', () => {
    it('returns the matching AuditRecordResponse for a known id', () => {
      let result: AuditRecordResponse | null = null;
      adapter.get('rec-001').subscribe((r) => {
        result = r;
      });

      jest.advanceTimersByTime(500);

      expect(result).not.toBeNull();
      expect((result as AuditRecordResponse).id).toBe('rec-001');
      expect((result as AuditRecordResponse).factType).toBe('evento.confirmado');
    });

    it('emits an error for an unknown record id', () => {
      let caughtError: Error | null = null;
      adapter.get('does-not-exist').subscribe({
        error: (e: Error) => {
          caughtError = e;
        },
      });

      jest.advanceTimersByTime(500);

      expect(caughtError).not.toBeNull();
      expect((caughtError as Error).message).toContain('not found');
    });
  });

  // -------------------------------------------------------------------------
  // verify()
  // -------------------------------------------------------------------------

  describe('verify()', () => {
    it('returns a ChainVerificationResult with intact status', () => {
      let result: ChainVerificationResult | null = null;
      adapter.verify('paciente', 'pac-marcio').subscribe((r) => {
        result = r;
      });

      jest.advanceTimersByTime(700);

      expect(result).not.toBeNull();
      expect((result as ChainVerificationResult).streamKey).toBe('paciente:pac-marcio');
      expect((result as ChainVerificationResult).status).toBe('intact');
    });

    it('reports the number of records verified', () => {
      let result: ChainVerificationResult | null = null;
      adapter.verify('paciente', 'pac-test').subscribe((r) => {
        result = r;
      });

      jest.advanceTimersByTime(700);

      expect(result).not.toBeNull();
      const r = result as ChainVerificationResult;
      expect(typeof r.recordsVerified).toBe('number');
      expect(r.recordsVerified).toBeGreaterThanOrEqual(0);
    });

    it('includes a non-null verifiedAt ISO timestamp', () => {
      let result: ChainVerificationResult | null = null;
      adapter.verify('paciente', 'pac-marcio').subscribe((r) => {
        result = r;
      });

      jest.advanceTimersByTime(700);

      expect(result).not.toBeNull();
      expect((result as ChainVerificationResult).verifiedAt).toBeTruthy();
      expect(() => new Date((result as ChainVerificationResult).verifiedAt)).not.toThrow();
    });
  });
});
