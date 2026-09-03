import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { TrainingSessionPageComponent } from './training-session-page.component';
import { TrainingSessionFacade } from '../trei.facade';
import { TrainingSessionScreenVm } from '../trei.types';

// ─── Minimal facade stub ─────────────────────────────────────────────────────
function makeIdleVm(): TrainingSessionScreenVm {
  return {
    session: null,
    messages: [],
    activeLocale: 'pt',
    status: 'idle',
    isAssistantThinking: false,
    error: null,
    // P7 fields (FE-025 / INT-005)
    isPatientNotFound: false,
    isDegraded: false,
    embedModelId: null,
  };
}

function createFacadeStub(overrides?: Partial<TrainingSessionScreenVm>) {
  const vm = { ...makeIdleVm(), ...overrides };
  return {
    screenVm: signal(vm),
    activeLocale: signal(vm.activeLocale),
    startSession: jest.fn().mockResolvedValue(undefined),
    endSession: jest.fn(),
    setActiveLocale: jest.fn(),
    sendTurn: jest.fn().mockResolvedValue(undefined),
    retry: jest.fn().mockResolvedValue(undefined),
  };
}

describe('TrainingSessionPageComponent', () => {
  let fixture: ComponentFixture<TrainingSessionPageComponent>;
  let component: TrainingSessionPageComponent;
  let facadeStub: ReturnType<typeof createFacadeStub>;

  beforeEach(async () => {
    facadeStub = createFacadeStub();

    await TestBed.configureTestingModule({
      imports: [TrainingSessionPageComponent],
      providers: [
        provideRouter([]),
        { provide: TrainingSessionFacade, useValue: facadeStub },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TrainingSessionPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should render EmptySessionState when status=idle and session=null', () => {
    const emptyState = fixture.nativeElement.querySelector('[data-testid="empty-state"]');
    expect(emptyState).toBeTruthy();
  });

  it('should NOT render the conversation panel when status=idle', () => {
    const panel = fixture.nativeElement.querySelector('[data-testid="conversation-panel"]');
    expect(panel).toBeNull();
  });

  it('should render loading indicator when status=loading', () => {
    facadeStub.screenVm.set({ ...makeIdleVm(), status: 'loading' });
    fixture.detectChanges();

    const loadingEl = fixture.nativeElement.querySelector('[role="status"]');
    expect(loadingEl).toBeTruthy();
  });

  it('should render ConversationErrorPanel when status=error', () => {
    facadeStub.screenVm.set({
      ...makeIdleVm(),
      status: 'error',
      error: { code: 'HTTP_503', message: 'Erro' },
    });
    fixture.detectChanges();

    const errorPanel = fixture.nativeElement.querySelector('[data-testid="session-error"]');
    expect(errorPanel).toBeTruthy();
  });

  it('should call facade.startSession when EmptyState start is emitted', () => {
    const btn = fixture.nativeElement.querySelector('[data-testid="start-session-btn"]') as HTMLButtonElement;
    btn.click();
    expect(facadeStub.startSession).toHaveBeenCalledWith('pac-marcio');
  });

  it('should render LocaleTabs', () => {
    const tabs = fixture.nativeElement.querySelector('[role="tablist"]');
    expect(tabs).toBeTruthy();
  });

  // ── P7 (FE-025 / G4): patient-not-found banner ───────────────────────────────

  it('P7/G4: should render patient-not-found banner when isPatientNotFound=true', () => {
    facadeStub.screenVm.set({
      ...makeIdleVm(),
      status: 'error',
      isPatientNotFound: true,
      error: { code: 'HTTP_404', message: 'Paciente não encontrado' },
    });
    fixture.detectChanges();

    const banner = fixture.nativeElement.querySelector('[data-testid="patient-not-found-banner"]') as HTMLElement;
    expect(banner).toBeTruthy();
    // Generic error panel should NOT be shown when isPatientNotFound is set
    const errorPanel = fixture.nativeElement.querySelector('[data-testid="session-error"]');
    expect(errorPanel).toBeNull();
  });

  it('P7/G4: should NOT render patient-not-found banner for non-404 errors', () => {
    facadeStub.screenVm.set({
      ...makeIdleVm(),
      status: 'error',
      isPatientNotFound: false,
      error: { code: 'HTTP_503', message: 'Serviço indisponível' },
    });
    fixture.detectChanges();

    const banner = fixture.nativeElement.querySelector('[data-testid="patient-not-found-banner"]');
    expect(banner).toBeNull();
    const errorPanel = fixture.nativeElement.querySelector('[data-testid="session-error"]');
    expect(errorPanel).toBeTruthy();
  });

  // ── P7 (FE-025): health-degraded affordance ──────────────────────────────────

  it('P7: should render health-degraded badge when isDegraded=true', () => {
    facadeStub.screenVm.set({ ...makeIdleVm(), isDegraded: true });
    fixture.detectChanges();

    const badge = fixture.nativeElement.querySelector('[data-testid="health-degraded-badge"]') as HTMLElement;
    expect(badge).toBeTruthy();
  });

  it('P7: should NOT render health-degraded badge when isDegraded=false', () => {
    facadeStub.screenVm.set({ ...makeIdleVm(), isDegraded: false });
    fixture.detectChanges();

    const badge = fixture.nativeElement.querySelector('[data-testid="health-degraded-badge"]');
    expect(badge).toBeNull();
  });

  // ── P7 (INT-005 / G5-link): embed model status display ───────────────────────

  it('INT-005: should render embed-model-status when embedModelId is set', () => {
    facadeStub.screenVm.set({
      ...makeIdleVm(),
      embedModelId: 'embed-mock:claude-3-5-sonnet',
    });
    fixture.detectChanges();

    const status = fixture.nativeElement.querySelector('[data-testid="embed-model-status"]') as HTMLElement;
    expect(status).toBeTruthy();
  });

  it('INT-005: should NOT render embed-model-status when embedModelId is null', () => {
    facadeStub.screenVm.set({ ...makeIdleVm(), embedModelId: null });
    fixture.detectChanges();

    const status = fixture.nativeElement.querySelector('[data-testid="embed-model-status"]');
    expect(status).toBeNull();
  });
});
