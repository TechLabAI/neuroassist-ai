import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { HistoryFacade } from './hist.facade';
import { HIST_CLIENT_PORT } from './api/hist-client.port';
import { HIST_FEEDBACK_PORT } from './api/hist-feedback.port';
import {
  AuditRecordResponse,
  FeedbackResponse,
  PagedResponse,
} from './hist.types';

// =============================================================================
// HistoryFacade spec — P2 (FE-HIST-002)
// Tests facade signals + async methods with immediate Observable mocks.
// No timer delay — mocks use of() directly for fast unit tests.
// =============================================================================

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeMockRecord(id: string, verdict: 'real' | 'falso_positivo' | null = null): AuditRecordResponse {
  return {
    id,
    factId: `fact-${id}`,
    factType: 'evento.confirmado',
    subject: { type: 'paciente', id: 'pac-test' },
    actor: { caregiverId: 'cuid-test', displayNameSnapshot: 'Test User' },
    payload: { eventoId: `evt-${id}`, rotulo: 'água', urgencia: 'rotina', verdict },
    occurredAt: new Date('2026-08-16T14:00:00Z').toISOString(),
    recordedAt: new Date('2026-08-16T14:00:00Z').toISOString(),
    streamKey: 'paciente:pac-test',
    sequenceNo: 1,
    prevHash: '0'.repeat(64),
    recordHash: 'a'.repeat(64),
    correctsRecordId: null,
  };
}

function makePagedResponse(
  items: AuditRecordResponse[],
  total?: number,
  page = 0,
  size = 50,
): PagedResponse<AuditRecordResponse> {
  return { items, page, size, total: total ?? items.length };
}

function makeFeedbackResponse(eventId: string, verdict: 'real' | 'falso_positivo' = 'real'): FeedbackResponse {
  return { eventId, verdict, confirmedAt: new Date().toISOString() };
}

function createMockClientPort() {
  return {
    list: jest.fn(),
    get: jest.fn(),
    verify: jest.fn(),
  };
}

function createMockFeedbackPort() {
  return {
    markFeedback: jest.fn(),
  };
}

// ---------------------------------------------------------------------------
// Suite
// ---------------------------------------------------------------------------

describe('HistoryFacade', () => {
  let facade: HistoryFacade;
  let mockClientPort: ReturnType<typeof createMockClientPort>;
  let mockFeedbackPort: ReturnType<typeof createMockFeedbackPort>;

  beforeEach(() => {
    mockClientPort = createMockClientPort();
    mockFeedbackPort = createMockFeedbackPort();

    TestBed.configureTestingModule({
      providers: [
        HistoryFacade,
        { provide: HIST_CLIENT_PORT, useValue: mockClientPort },
        { provide: HIST_FEEDBACK_PORT, useValue: mockFeedbackPort },
      ],
    });
    facade = TestBed.inject(HistoryFacade);
  });

  afterEach(() => TestBed.resetTestingModule());

  // -------------------------------------------------------------------------
  // load() — basic success path
  // -------------------------------------------------------------------------

  it('load("semana") sets status to loaded and populates items', async () => {
    const records = [makeMockRecord('001'), makeMockRecord('002')];
    mockClientPort.list.mockReturnValue(of(makePagedResponse(records)));

    await facade.load('semana');

    expect(facade.status()).toBe('loaded');
    expect(facade.items().length).toBe(2);
    expect(facade.error()).toBeNull();
  });

  // -------------------------------------------------------------------------
  // load() — empty list
  // -------------------------------------------------------------------------

  it('load() with empty list sets status to empty', async () => {
    mockClientPort.list.mockReturnValue(of(makePagedResponse([])));

    await facade.load('semana');

    expect(facade.status()).toBe('empty');
    expect(facade.items().length).toBe(0);
  });

  // -------------------------------------------------------------------------
  // load() — error path
  // -------------------------------------------------------------------------

  it('load() with port error sets status to error and populates error signal', async () => {
    mockClientPort.list.mockReturnValue(throwError(() => new Error('Network failure')));

    await facade.load('semana');

    expect(facade.status()).toBe('error');
    expect(facade.error()).not.toBeNull();
    expect(facade.error()?.code).toBe('UNKNOWN');
  });

  // -------------------------------------------------------------------------
  // setPeriod() — delegates to load() and updates period signal
  // -------------------------------------------------------------------------

  it('setPeriod("hoje") updates period signal and reloads', async () => {
    mockClientPort.list.mockReturnValue(of(makePagedResponse([makeMockRecord('001')])));

    await facade.setPeriod('hoje');

    expect(facade.period()).toBe('hoje');
    expect(facade.status()).toBe('loaded');
    expect(mockClientPort.list).toHaveBeenCalledTimes(1);
  });

  // -------------------------------------------------------------------------
  // loadMore() — appends items when hasMore = true
  // -------------------------------------------------------------------------

  it('loadMore() appends items to the list when hasMore is true', async () => {
    // page=0, size=1, total=2 → hasMore = (0+1)*1 < 2 = true
    mockClientPort.list
      .mockReturnValueOnce(of(makePagedResponse([makeMockRecord('001')], 2, 0, 1)))
      .mockReturnValueOnce(of(makePagedResponse([makeMockRecord('002')], 2, 1, 1)));

    await facade.load('semana');

    expect(facade.hasMore()).toBe(true);
    expect(facade.items().length).toBe(1);

    await facade.loadMore();

    expect(facade.items().length).toBe(2);
    expect(facade.items()[1].id).toBe('002');
    expect(facade.status()).toBe('loaded');
  });

  // -------------------------------------------------------------------------
  // markFeedback() — success: marking → marked, verdict updated
  // -------------------------------------------------------------------------

  it('markFeedback(id, "real") transitions marking to marked and updates item verdict', async () => {
    mockClientPort.list.mockReturnValue(of(makePagedResponse([makeMockRecord('001')])));
    await facade.load('semana');

    mockFeedbackPort.markFeedback.mockReturnValue(
      of(makeFeedbackResponse('001', 'real')),
    );

    await facade.markFeedback('001', 'real');

    const marking = facade.markings().get('001');
    expect(marking?.state).toBe('marked');

    const item = facade.items().find((i) => i.id === '001');
    expect(item?.verdict).toBe('real');
  });

  // -------------------------------------------------------------------------
  // markFeedback() — 409: reconcile verdict from backend
  // -------------------------------------------------------------------------

  it('markFeedback with 409 error reconciles verdict and sets marking to marked', async () => {
    mockClientPort.list.mockReturnValue(of(makePagedResponse([makeMockRecord('001')])));
    await facade.load('semana');

    const error409 = Object.assign(new Error('Feedback already confirmed'), {
      status: 409,
      response: { eventId: '001', verdict: 'real' as const, confirmedAt: new Date().toISOString() },
    });
    mockFeedbackPort.markFeedback.mockReturnValue(throwError(() => error409));

    await facade.markFeedback('001', 'real');

    const marking = facade.markings().get('001');
    expect(marking?.state).toBe('marked');

    const item = facade.items().find((i) => i.id === '001');
    expect(item?.verdict).toBe('real');
  });

  // -------------------------------------------------------------------------
  // retry() — re-runs last load()
  // -------------------------------------------------------------------------

  it('retry() re-loads with the last period used', async () => {
    mockClientPort.list.mockReturnValue(of(makePagedResponse([makeMockRecord('001')])));

    await facade.load('hoje');
    expect(facade.period()).toBe('hoje');

    await facade.retry();

    expect(facade.period()).toBe('hoje');
    expect(facade.status()).toBe('loaded');
    expect(mockClientPort.list).toHaveBeenCalledTimes(2);
  });

  // -------------------------------------------------------------------------
  // screenVm computed — aggregates all signals
  // -------------------------------------------------------------------------

  it('screenVm computed signal reflects current items/status/period/hasMore/error', async () => {
    const records = [makeMockRecord('001')];
    mockClientPort.list.mockReturnValue(of(makePagedResponse(records)));

    await facade.load('mes');

    const vm = facade.screenVm();
    expect(vm.period).toBe('mes');
    expect(vm.status).toBe('loaded');
    expect(vm.items.length).toBe(1);
    expect(vm.hasMore).toBe(false);
    expect(vm.error).toBeNull();
  });
});
