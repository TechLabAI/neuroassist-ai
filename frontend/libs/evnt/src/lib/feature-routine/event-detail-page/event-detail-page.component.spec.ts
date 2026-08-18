import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { EventDetailPageComponent, classifyDetailError } from './event-detail-page.component';
import { EventDeliveryFacade } from '../../evnt.facade';
import { ActivatedRoute, Router } from '@angular/router';
import { EventoVm } from '../../evnt.types';
import { AppError } from '@neuroassist/shared/error-model';

// ---------------------------------------------------------------------------
// Mock factory — writable signal handles exposed for test mutations.
// ---------------------------------------------------------------------------
function createMockFacade(loadDetailImpl?: () => Promise<void>) {
  const _selectedEvent = signal<EventoVm | null>(null);
  const _loading = signal(false);
  const _confirming = signal(false);
  const _error = signal<AppError | null>(null);

  return {
    selectedEvent: _selectedEvent.asReadonly(),
    loading: _loading.asReadonly(),
    confirming: _confirming.asReadonly(),
    error: _error.asReadonly(),
    loadDetail: jest.fn(loadDetailImpl ?? (() => Promise.resolve())),
    confirm: jest.fn().mockResolvedValue(undefined),
    // Writable handles for test mutations:
    _selectedEvent,
    _loading,
    _confirming,
    _error,
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

// ---------------------------------------------------------------------------
// Standard suite (default loadDetail — resolves immediately, selectedEvent stays null)
// ---------------------------------------------------------------------------
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

// ---------------------------------------------------------------------------
// P6-G: forbidden error suite — uses a facade that sets error to /errors/forbidden
// ---------------------------------------------------------------------------
describe('EventDetailPageComponent — P6-G forbidden error', () => {
  let fixture: ComponentFixture<EventDetailPageComponent>;
  let mockRouter: { navigate: jest.Mock };
  let mockFacade: ReturnType<typeof createMockFacade>;

  beforeEach(async () => {
    mockRouter = { navigate: jest.fn() };
    // loadDetail implementation: sets error to /errors/forbidden (selectedEvent stays null)
    mockFacade = createMockFacade();
    mockFacade.loadDetail.mockImplementation(() => {
      mockFacade._error.set({
        code: '/errors/forbidden',
        type: '/errors/forbidden',
        message: 'Forbidden',
      });
      return Promise.resolve();
    });

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
    // Flush the async _loadWithErrorHandling microtask
    await Promise.resolve();
    fixture.detectChanges();
  });

  it('shows forbidden inline message and does not navigate away', () => {
    const forbiddenEl = fixture.nativeElement.querySelector('[data-testid="forbidden-message"]');
    expect(forbiddenEl).not.toBeNull();
    expect(mockRouter.navigate).not.toHaveBeenCalled();
  });
});

// ---------------------------------------------------------------------------
// classifyDetailError unit tests (P6-G helper — isolated, no DI)
// ---------------------------------------------------------------------------
describe('classifyDetailError', () => {
  it('returns "not-found" for /errors/event-not-found via type', () => {
    expect(
      classifyDetailError({ code: '/errors/event-not-found', type: '/errors/event-not-found', message: 'x' }),
    ).toBe('not-found');
  });

  it('returns "tenant-mismatch" for /errors/tenant-mismatch via type', () => {
    expect(
      classifyDetailError({ code: '/errors/tenant-mismatch', type: '/errors/tenant-mismatch', message: 'x' }),
    ).toBe('tenant-mismatch');
  });

  it('returns "forbidden" for /errors/forbidden via type', () => {
    expect(
      classifyDetailError({ code: '/errors/forbidden', type: '/errors/forbidden', message: 'x' }),
    ).toBe('forbidden');
  });

  it('falls back to code when type is absent', () => {
    expect(classifyDetailError({ code: '/errors/forbidden', message: 'x' })).toBe('forbidden');
  });

  it('returns "other" for unrecognized errors', () => {
    expect(classifyDetailError({ code: 'EVNT_HTTP_500', message: 'x' })).toBe('other');
  });

  it('returns "other" for null', () => {
    expect(classifyDetailError(null)).toBe('other');
  });
});
