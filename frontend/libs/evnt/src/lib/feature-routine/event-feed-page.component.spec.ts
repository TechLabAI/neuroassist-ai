import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { EventFeedPageComponent } from './event-feed-page.component';
import { EventDeliveryFacade } from '../evnt.facade';
import { Router } from '@angular/router';
import { EventoVm, DeliveryMode } from '../evnt.types';

// ---------------------------------------------------------------------------
// Mock factory — writable signals exposed as _underscore handles for mutations.
// ---------------------------------------------------------------------------
function createMockFacade() {
  const _routineQueue = signal<readonly EventoVm[]>([]);
  const _recentEvents = signal<readonly EventoVm[]>([]);
  const _deliveryMode = signal<DeliveryMode>('live');
  const _loading = signal(false);
  const _error = signal<null>(null);

  return {
    routineQueue: _routineQueue.asReadonly(),
    recentEvents: _recentEvents.asReadonly(),
    deliveryMode: _deliveryMode.asReadonly(),
    loading: _loading.asReadonly(),
    error: _error.asReadonly(),
    dismiss: jest.fn(),
    loadHistory: jest.fn().mockResolvedValue(undefined),
    // Writable handles for test mutations:
    _routineQueue,
    _recentEvents,
    _deliveryMode,
    _loading,
  };
}

function mockEvento(id: string): EventoVm {
  return {
    eventoId: id,
    urgencia: 'rotina',
    tier: 'discreet',
    commandLabel: `Comando ${id}`,
    patientName: 'Márcio',
    recognizedAt: '2026-08-13T14:00:00Z',
    displayTime: 'às 14h00',
    feedback: 'nao_avaliado',
    confirmadoPor: null,
    confirmadoAt: null,
  };
}

describe('EventFeedPageComponent', () => {
  let fixture: ComponentFixture<EventFeedPageComponent>;
  let mockFacade: ReturnType<typeof createMockFacade>;
  let mockRouter: { navigate: jest.Mock };

  beforeEach(async () => {
    mockFacade = createMockFacade();
    mockRouter = { navigate: jest.fn() };

    await TestBed.configureTestingModule({
      imports: [EventFeedPageComponent],
      providers: [
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        { provide: EventDeliveryFacade, useValue: mockFacade as any },
        { provide: Router, useValue: mockRouter },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(EventFeedPageComponent);
    fixture.detectChanges();
  });

  it('should create the component', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('shows the degraded banner when deliveryMode is "degraded"', () => {
    mockFacade._deliveryMode.set('degraded');
    fixture.detectChanges();

    const banner = fixture.nativeElement.querySelector(
      '[data-testid="degraded-banner"]',
    );
    expect(banner).toBeTruthy();
    expect(banner.textContent).toContain('Modo degradado');
  });

  it('shows the empty state when routineQueue is empty and not loading', () => {
    mockFacade._routineQueue.set([]);
    mockFacade._recentEvents.set([]);
    mockFacade._loading.set(false);
    fixture.detectChanges();

    const emptyState = fixture.nativeElement.querySelector(
      '[data-testid="empty-state"]',
    );
    expect(emptyState).toBeTruthy();
  });

  it('does not render evnt-routine-notice when routineNotices flag is off', () => {
    // Populate queue so notices would be rendered if flag were on
    mockFacade._routineQueue.set([mockEvento('EVT-001'), mockEvento('EVT-002')]);
    fixture.detectChanges();

    // Disable the flag (simulates EVNT_MODULE_DESCRIPTOR.featureFlags.routineNotices='off')
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    (fixture.componentInstance as any).routineNoticesEnabled.set(false);
    fixture.detectChanges();

    const notices = fixture.nativeElement.querySelectorAll('evnt-routine-notice');
    expect(notices.length).toBe(0);
  });
});
