import { TestBed } from '@angular/core/testing';
import { of, EMPTY } from 'rxjs';
import { EventDeliveryFacade } from './evnt.facade';
import {
  EVNT_CLIENT_PORT,
  EventoSummaryDto,
  EventoDto,
  ActiveEventsDto,
  EventoPageDto,
  ConfirmationResponseDto,
} from './api/evnt-client.port';

// =============================================================================
// EventDeliveryFacade spec — P5 (FE-EVNT-007/008/009)
// Tests the three core facade actions: confirm, dismiss, loadHistory.
// Port is mocked with immediate Observables (no timer delay) to keep tests fast.
// =============================================================================

// ---------------------------------------------------------------------------
// Mock port factory — returns immediate Observables, no 400ms timer.
// ---------------------------------------------------------------------------
function createMockPort() {
  return {
    openStream: jest.fn(() => EMPTY),
    listActive: jest.fn(),
    get: jest.fn(),
    confirm: jest.fn(),
    ack: jest.fn(() => EMPTY as ReturnType<typeof EMPTY>),
    query: jest.fn(),
  };
}

// ---------------------------------------------------------------------------
// Reusable seed DTOs
// ---------------------------------------------------------------------------
const EMERGENCY_SUMMARY: EventoSummaryDto = {
  eventoId: 'evt-001',
  urgencia: 'emergencia',
  tier: 'full_screen_alarm',
  commandLabel: 'Preciso de ajuda',
  feedback: 'nao_avaliado',
  recognizedAt: '2026-08-13T14:00:00Z',
};

const ROUTINE_SUMMARY_A: EventoSummaryDto = {
  eventoId: 'evt-002',
  urgencia: 'rotina',
  tier: 'discreet',
  commandLabel: 'Água',
  feedback: 'nao_avaliado',
  recognizedAt: '2026-08-13T13:00:00Z',
};

const ROUTINE_SUMMARY_B: EventoSummaryDto = {
  eventoId: 'evt-003',
  urgencia: 'rotina',
  tier: 'discreet',
  commandLabel: 'Coberta',
  feedback: 'nao_avaliado',
  recognizedAt: '2026-08-13T12:00:00Z',
};

const HISTORY_DTO: EventoDto = {
  eventoId: 'evt-001',
  pacienteId: 'pac-marcio',
  comandoRef: 'cmd-ajuda',
  commandLabelSnapshot: 'Preciso de ajuda',
  urgenciaSnapshot: 'emergencia',
  timingWindow: { startMs: 1753000000000, endMs: 1753000000850, durationMs: 850 },
  feedback: 'nao_avaliado',
  recognizedAt: '2026-08-13T14:00:00Z',
};

// ---------------------------------------------------------------------------

describe('EventDeliveryFacade', () => {
  let facade: EventDeliveryFacade;
  let mockPort: ReturnType<typeof createMockPort>;

  beforeEach(() => {
    mockPort = createMockPort();

    TestBed.configureTestingModule({
      providers: [
        EventDeliveryFacade,
        { provide: EVNT_CLIENT_PORT, useValue: mockPort },
      ],
    });
    facade = TestBed.inject(EventDeliveryFacade);
  });

  // -----------------------------------------------------------------------
  // confirm() — SPEC-04 §5: non-optimistic; clears activeEvent only on 200
  // -----------------------------------------------------------------------
  it('confirm() clears activeEvent signal after a successful POST (non-optimistic)', async () => {
    // Arrange: hydrate to populate _activeEvent with an emergency event
    const hydrateDto: ActiveEventsDto = {
      serverTime: '2026-08-13T14:00:00Z',
      activeEvent: EMERGENCY_SUMMARY,
      events: [EMERGENCY_SUMMARY],
    };
    mockPort.listActive.mockReturnValue(of(hydrateDto));
    await facade.hydrate();
    expect(facade.activeEvent()?.eventoId).toBe('evt-001');

    // Act: confirm with a verdict
    const confirmResponse: ConfirmationResponseDto = {
      eventoId: 'evt-001',
      feedback: 'real',
      confirmadoPor: 'cuid-jamilly',
      confirmadoAt: '2026-08-13T14:01:00Z',
    };
    mockPort.confirm.mockReturnValue(of(confirmResponse));
    await facade.confirm('evt-001', 'real');

    // Assert: activeEvent must be null (alarm closed after confirm 200)
    expect(facade.activeEvent()).toBeNull();
  });

  // -----------------------------------------------------------------------
  // dismiss() — SPEC-04 §5: optimistic client-side removal (no server call)
  // -----------------------------------------------------------------------
  it('dismiss() removes the specified event from routineQueue', async () => {
    // Arrange: hydrate to populate routine queue with two items
    const hydrateDto: ActiveEventsDto = {
      serverTime: '2026-08-13T14:00:00Z',
      activeEvent: null,
      events: [ROUTINE_SUMMARY_A, ROUTINE_SUMMARY_B],
    };
    mockPort.listActive.mockReturnValue(of(hydrateDto));
    await facade.hydrate();
    expect(facade.routineQueue().length).toBe(2);

    // Act: dismiss first item
    facade.dismiss('evt-002');

    // Assert: queue shrinks by one; remaining item is evt-003
    expect(facade.routineQueue().length).toBe(1);
    expect(facade.routineQueue()[0].eventoId).toBe('evt-003');
  });

  // -----------------------------------------------------------------------
  // loadHistory() — populates recentEvents signal from paged query result
  // -----------------------------------------------------------------------
  it('loadHistory() populates recentEvents signal with mapped view-models', async () => {
    // Arrange
    const pageDto: EventoPageDto = {
      content: [HISTORY_DTO],
      totalElements: 1,
      totalPages: 1,
      page: 0,
      size: 20,
    };
    mockPort.query.mockReturnValue(of(pageDto));
    expect(facade.recentEvents().length).toBe(0);

    // Act
    await facade.loadHistory();

    // Assert
    expect(facade.recentEvents().length).toBe(1);
    expect(facade.recentEvents()[0].eventoId).toBe('evt-001');
    expect(facade.recentEvents()[0].commandLabel).toBe('Preciso de ajuda');
    expect(facade.recentEvents()[0].urgencia).toBe('emergencia');
  });
});
