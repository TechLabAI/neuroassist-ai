import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { EventDetailPageComponent } from './event-detail-page.component';
import { EventDeliveryFacade } from '../../evnt.facade';
import { ActivatedRoute, Router } from '@angular/router';
import { EventoVm } from '../../evnt.types';

// ---------------------------------------------------------------------------
// Mock factory — writable signal handles exposed for test mutations.
// ---------------------------------------------------------------------------
function createMockFacade() {
  const _selectedEvent = signal<EventoVm | null>(null);
  const _loading = signal(false);
  const _confirming = signal(false);

  return {
    selectedEvent: _selectedEvent.asReadonly(),
    loading: _loading.asReadonly(),
    confirming: _confirming.asReadonly(),
    loadDetail: jest.fn().mockResolvedValue(undefined),
    confirm: jest.fn().mockResolvedValue(undefined),
    // Writable handles for test mutations:
    _selectedEvent,
    _loading,
    _confirming,
  };
}

function mockEvento(overrides: Partial<EventoVm> = {}): EventoVm {
  return {
    eventoId: 'EVT-001',
    urgencia: 'rotina',
    tier: 'discreet',
    commandLabel: 'Ajustar travesseiro',
    patientName: 'Márcio',
    recognizedAt: '2026-08-13T14:00:00Z',
    displayTime: 'às 14h00',
    feedback: 'nao_avaliado',
    confirmadoPor: null,
    confirmadoAt: null,
    ...overrides,
  };
}

const MOCK_PARAM_MAP = {
  get: (key: string) => (key === 'eventoId' ? 'EVT-001' : null),
};
const MOCK_ROUTE = { snapshot: { paramMap: MOCK_PARAM_MAP } };

describe('EventDetailPageComponent', () => {
  let fixture: ComponentFixture<EventDetailPageComponent>;
  let mockFacade: ReturnType<typeof createMockFacade>;
  let mockRouter: { navigate: jest.Mock };

  beforeEach(async () => {
    mockFacade = createMockFacade();
    mockRouter = { navigate: jest.fn() };

    await TestBed.configureTestingModule({
      imports: [EventDetailPageComponent],
      providers: [
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        { provide: EventDeliveryFacade, useValue: mockFacade as any },
        { provide: ActivatedRoute, useValue: MOCK_ROUTE },
        { provide: Router, useValue: mockRouter },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(EventDetailPageComponent);
    fixture.detectChanges();
  });

  it('should create the component', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('navigates to /eventos when selectedEvent is null after loadDetail resolves', async () => {
    // loadDetail mock resolves immediately with undefined; selectedEvent stays null.
    // Flush the promise microtask chain spawned in ngOnInit.
    await Promise.resolve();
    expect(mockRouter.navigate).toHaveBeenCalledWith(['/eventos']);
  });

  it('does not render evnt-feedback-verdict-control when feedback is not "nao_avaliado"', () => {
    mockFacade._selectedEvent.set(
      mockEvento({ feedback: 'real', confirmadoPor: 'Jamilly', confirmadoAt: '2026-08-13T14:05:00Z' }),
    );
    fixture.detectChanges();

    // canConfirm() should be false → FeedbackVerdictControl should NOT be in DOM
    const control = fixture.nativeElement.querySelector('evnt-feedback-verdict-control');
    expect(control).toBeNull();
  });
});
