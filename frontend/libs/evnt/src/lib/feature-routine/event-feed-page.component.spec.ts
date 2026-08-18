import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { EventFeedPageComponent } from './event-feed-page.component';
import { EventDeliveryFacade } from '../evnt.facade';
import { EvntFeatureFlagsService } from '../api/evnt-feature-flags.service';
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

// ---------------------------------------------------------------------------
// Mock EvntFeatureFlagsService — controls routineNoticesEnabled and poll interval.
// Exposes mutable jest.fn() handles so tests can change return values per-test.
// ---------------------------------------------------------------------------
function createMockFlagService(opts: { routineNotices?: boolean; pollIntervalMs?: number } = {}) {
  return {
    areRoutineNoticesEnabled: jest.fn().mockReturnValue(opts.routineNotices ?? true),
    getPollIntervalMs: jest.fn().mockReturnValue(opts.pollIntervalMs ?? 15_000),
    isLiveChannelEnabled: jest.fn().mockReturnValue(true),
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

// ---------------------------------------------------------------------------
// Helper to create a fresh TestBed + fixture with a custom flag service.
// ---------------------------------------------------------------------------
async function createFixture(
  mockFacadeArg: ReturnType<typeof createMockFacade>,
  mockRouterArg: { navigate: jest.Mock },
  mockFlagServiceArg: ReturnType<typeof createMockFlagService>,
): Promise<ComponentFixture<EventFeedPageComponent>> {
  await TestBed.configureTestingModule({
    imports: [EventFeedPageComponent],
    providers: [
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      { provide: EventDeliveryFacade, useValue: mockFacadeArg as any },
      { provide: Router, useValue: mockRouterArg },
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      { provide: EvntFeatureFlagsService, useValue: mockFlagServiceArg as any },
    ],
  }).compileComponents();

  const f = TestBed.createComponent(EventFeedPageComponent);
  f.detectChanges();
  return f;
}

describe('EventFeedPageComponent', () => {
  let fixture: ComponentFixture<EventFeedPageComponent>;
  let mockFacade: ReturnType<typeof createMockFacade>;
  let mockRouter: { navigate: jest.Mock };
  let mockFlagService: ReturnType<typeof createMockFlagService>;

  beforeEach(async () => {
    mockFacade = createMockFacade();
    mockRouter = { navigate: jest.fn() };
    mockFlagService = createMockFlagService();

    fixture = await createFixture(mockFacade, mockRouter, mockFlagService);
  });

  afterEach(() => TestBed.resetTestingModule());

  it('should create the component', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('shows the page title "Eventos" in all states (FE-EVNT-022)', () => {
    const el: HTMLElement = fixture.nativeElement;
    const title = el.querySelector('.page-title');
    expect(title).toBeTruthy();
    expect(title?.textContent?.trim()).toBe('Eventos');
  });

  it('shows the skeleton when loading=true and hides content (FE-EVNT-022)', () => {
    mockFacade._loading.set(true);
    fixture.detectChanges();

    const skeleton = fixture.nativeElement.querySelector('evnt-feed-skeleton');
    expect(skeleton).toBeTruthy();

    // Routine notices should NOT be rendered during loading
    const notices = fixture.nativeElement.querySelectorAll('evnt-routine-notice');
    expect(notices.length).toBe(0);

    // Empty state should NOT be rendered during loading
    const emptyState = fixture.nativeElement.querySelector('[data-testid="empty-state"]');
    expect(emptyState).toBeFalsy();
  });

  it('hides the skeleton and shows content when loading=false (FE-EVNT-022)', () => {
    mockFacade._loading.set(false);
    fixture.detectChanges();

    const skeleton = fixture.nativeElement.querySelector('evnt-feed-skeleton');
    expect(skeleton).toBeFalsy();
  });

  it('shows the degraded banner when deliveryMode is "degraded" (FE-EVNT-024)', () => {
    mockFacade._deliveryMode.set('degraded');
    fixture.detectChanges();

    const banner = fixture.nativeElement.querySelector(
      '[data-testid="degraded-banner"]',
    );
    expect(banner).toBeTruthy();
    expect(banner.textContent).toContain('Modo degradado');
    expect(banner.textContent).toContain('15s');
  });

  it('does not render the degraded banner when deliveryMode is "live"', () => {
    mockFacade._deliveryMode.set('live');
    fixture.detectChanges();

    const banner = fixture.nativeElement.querySelector('[data-testid="degraded-banner"]');
    expect(banner).toBeFalsy();
  });

  it('shows the empty state when routineQueue is empty and not loading (FE-EVNT-022)', () => {
    mockFacade._routineQueue.set([]);
    mockFacade._recentEvents.set([]);
    mockFacade._loading.set(false);
    fixture.detectChanges();

    const emptyState = fixture.nativeElement.querySelector(
      '[data-testid="empty-state"]',
    );
    expect(emptyState).toBeTruthy();
    expect(emptyState.textContent).toContain('Sem notificações pendentes');
  });

  it('does not render evnt-routine-notice when routineNotices flag is off (FE-EVNT-024 FLAG-02)', async () => {
    // Create a fresh fixture with routineNotices=false flag service
    TestBed.resetTestingModule();
    const offFlagService = createMockFlagService({ routineNotices: false });
    const localFacade = createMockFacade();
    const localFixture = await createFixture(localFacade, { navigate: jest.fn() }, offFlagService);

    // Populate queue so notices would be rendered if flag were on
    localFacade._routineQueue.set([mockEvento('EVT-001'), mockEvento('EVT-002')]);
    localFacade._loading.set(false);
    localFixture.detectChanges();

    const notices = localFixture.nativeElement.querySelectorAll('evnt-routine-notice');
    expect(notices.length).toBe(0);
  });
});
