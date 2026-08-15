// =============================================================================
// device-detail-page.component.spec.ts — FE-EDGE-014 DoD
//
// Covers:
//   - Loading skeleton renders when facade.loading() is true
//   - Error message renders when facade.error() is set
//   - Device info panel renders when facade.detail() is populated
//   - Not-paired state renders when detail() is null and no error
//   - Unpair confirmation dialog appears when unpairAction is emitted
//   - Retire confirmation dialog appears when retireAction is emitted
//   - Calling doUnpair() delegates to DeviceFacade.unpair()
//   - ngOnInit: calls facade.load(deviceId) when deviceId param is present
//   - ngOnInit: calls facade.startPolling()
// =============================================================================

import { TestBed, ComponentFixture } from '@angular/core/testing';
import { signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { provideRouter } from '@angular/router';
import { DeviceDetailPageComponent } from './device-detail-page.component';
import { ConnectivityStatusCardComponent } from './connectivity-status-card.component';
import { DeviceMetaListComponent } from './device-meta-list.component';
import { DeviceActionsComponent } from './device-actions.component';
import { DeviceFacade } from '../edge.facade';
import {
  DispositivoEdgeView,
  DeviceDetailVM,
  DerivedState,
  DeviceConnectivityVM,
} from '@neuroassist/shared/contracts';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

const FIXED_NOW = 1_000_000_000_000;

function makePairedDevice(): DispositivoEdgeView {
  return {
    deviceId: 'dev-spec-001',
    tenantRef: 'tenant-test',
    pacienteId: 'pac-test',
    pairingState: 'paired',
    status: 'online',
    ultimaSincronizacao: new Date(FIXED_NOW - 10_000).toISOString(),
    versao: '1.5.0',
    runningProfileVersion: 3,
    currentProfileVersion: 3,
    profileDrift: false,
    stalenessThresholdSeconds: 90,
    serverTime: new Date(FIXED_NOW).toISOString(),
  };
}

function makeDetailVM(overrides: Partial<DeviceDetailVM> = {}): DeviceDetailVM {
  return {
    device: makePairedDevice(),
    derivedState: 'online',
    lastSyncRelative: 'há 10s',
    profileDrift: false,
    canManage: true,
    ...overrides,
  };
}

function makeMockFacade(opts: {
  loading?: boolean;
  error?: string | null;
  detail?: DeviceDetailVM | null;
  derivedState?: DerivedState;
  canManage?: boolean;
} = {}) {
  const _loading = signal(opts.loading ?? false);
  const _error = signal<string | null>(opts.error ?? null);
  const _detail = signal<DeviceDetailVM | null>(opts.detail ?? null);
  const _canManage = signal<boolean>(opts.canManage ?? true);

  return {
    loading: _loading.asReadonly(),
    error: _error.asReadonly(),
    detail: _detail.asReadonly(),
    canManage: _canManage.asReadonly(),
    device: signal<DispositivoEdgeView | null>(null).asReadonly(),
    derivedState: signal<DerivedState>(opts.derivedState ?? 'not-paired').asReadonly(),
    connectivity: signal<DeviceConnectivityVM>({
      paired: false,
      derivedState: 'not-paired',
      lastSyncIso: null,
      lastSyncRelative: '—',
      versao: null,
      tone: 'muted',
      label: 'Sem dispositivo',
    }).asReadonly(),
    load: jest.fn(),
    resolveForActivePatient: jest.fn(),
    startPolling: jest.fn(),
    stopPolling: jest.fn(),
    unpair: jest.fn(),
    retire: jest.fn(),
    // Test helpers
    _setLoading: (v: boolean) => _loading.set(v),
    _setError: (v: string | null) => _error.set(v),
    _setDetail: (v: DeviceDetailVM | null) => _detail.set(v),
    _setCanManage: (v: boolean) => _canManage.set(v),
  };
}

describe('DeviceDetailPageComponent', () => {
  let fixture: ComponentFixture<DeviceDetailPageComponent>;
  let mockFacade: ReturnType<typeof makeMockFacade>;

  async function createComponent(
    params: Record<string, string> = {},
    facadeOpts: Parameters<typeof makeMockFacade>[0] = {},
  ) {
    mockFacade = makeMockFacade(facadeOpts);

    await TestBed.configureTestingModule({
      // Provide the component plus its standalone child components so Angular
      // can resolve property bindings in the template (Angular 18 standalone pattern).
      imports: [
        DeviceDetailPageComponent,
        ConnectivityStatusCardComponent,
        DeviceMetaListComponent,
        DeviceActionsComponent,
      ],
      providers: [
        provideRouter([]),
        { provide: DeviceFacade, useValue: mockFacade },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: {
                get: (key: string) => params[key] ?? null,
              },
            },
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DeviceDetailPageComponent);
    fixture.detectChanges();
  }

  afterEach(() => TestBed.resetTestingModule());

  // ---------------------------------------------------------------------------
  // ngOnInit wiring
  // ---------------------------------------------------------------------------

  it('calls facade.load(deviceId) when deviceId param is present', async () => {
    await createComponent({ deviceId: 'dev-abc' });
    expect(mockFacade.load).toHaveBeenCalledWith('dev-abc');
  });

  it('calls facade.resolveForActivePatient() when no deviceId param', async () => {
    await createComponent({});
    expect(mockFacade.resolveForActivePatient).toHaveBeenCalled();
  });

  it('calls facade.startPolling() on init', async () => {
    await createComponent({});
    expect(mockFacade.startPolling).toHaveBeenCalledTimes(1);
  });

  // ---------------------------------------------------------------------------
  // Loading state
  // ---------------------------------------------------------------------------

  it('renders loading indicator when facade.loading() is true', async () => {
    await createComponent({}, { loading: true });

    const indicator = fixture.nativeElement.querySelector('[data-testid="loading-indicator"]');
    expect(indicator).not.toBeNull();
  });

  it('does not render loading indicator when loading is false', async () => {
    await createComponent({}, { loading: false });

    const indicator = fixture.nativeElement.querySelector('[data-testid="loading-indicator"]');
    expect(indicator).toBeNull();
  });

  // ---------------------------------------------------------------------------
  // Error state
  // ---------------------------------------------------------------------------

  it('renders the error message when facade.error() is set', async () => {
    await createComponent({}, { error: 'Falha de rede' });

    const errorEl = fixture.nativeElement.querySelector('[data-testid="error-message"]');
    expect(errorEl).not.toBeNull();
    expect(errorEl.textContent).toContain('Falha de rede');
  });

  // ---------------------------------------------------------------------------
  // Device detail panel
  // ---------------------------------------------------------------------------

  it('renders the connectivity status card when detail is available', async () => {
    await createComponent({}, { detail: makeDetailVM() });

    const card = fixture.nativeElement.querySelector('edge-connectivity-status-card');
    expect(card).not.toBeNull();
  });

  it('renders the device meta list when detail is available', async () => {
    await createComponent({}, { detail: makeDetailVM() });

    const metaList = fixture.nativeElement.querySelector('edge-device-meta-list');
    expect(metaList).not.toBeNull();
  });

  it('renders device actions when canManage is true', async () => {
    await createComponent({}, { detail: makeDetailVM({ canManage: true }) });

    const actions = fixture.nativeElement.querySelector('edge-device-actions');
    expect(actions).not.toBeNull();
  });

  it('does not render device actions when canManage is false', async () => {
    await createComponent({}, { detail: makeDetailVM({ canManage: false }) });

    const actions = fixture.nativeElement.querySelector('edge-device-actions');
    expect(actions).toBeNull();
  });

  // ---------------------------------------------------------------------------
  // Not-paired state
  // ---------------------------------------------------------------------------

  it('renders not-paired state when detail() is null and no error', async () => {
    await createComponent({}, { detail: null });

    const notPairedEl = fixture.nativeElement.querySelector('[data-testid="not-paired-state"]');
    expect(notPairedEl).not.toBeNull();
  });

  // ---------------------------------------------------------------------------
  // Confirmation dialogs
  // ---------------------------------------------------------------------------

  it('shows unpair confirmation dialog when DeviceActionsComponent emits unpairAction', async () => {
    await createComponent({}, { detail: makeDetailVM({ canManage: true }) });

    // Simulate the DeviceActionsComponent emitting unpairAction
    // Access component's method directly
    (fixture.componentInstance as unknown as { confirmUnpair: () => void }).confirmUnpair();
    fixture.detectChanges();

    const dialog = fixture.nativeElement.querySelector('[data-testid="unpair-confirm-dialog"]');
    expect(dialog).not.toBeNull();
  });

  it('shows retire confirmation dialog when DeviceActionsComponent emits retireAction', async () => {
    await createComponent({}, { detail: makeDetailVM({ canManage: true }) });

    (fixture.componentInstance as unknown as { confirmRetire: () => void }).confirmRetire();
    fixture.detectChanges();

    const dialog = fixture.nativeElement.querySelector('[data-testid="retire-confirm-dialog"]');
    expect(dialog).not.toBeNull();
  });

  it('calls facade.unpair() when confirm unpair button is clicked', async () => {
    await createComponent({}, { detail: makeDetailVM({ canManage: true }) });

    (fixture.componentInstance as unknown as { confirmUnpair: () => void }).confirmUnpair();
    fixture.detectChanges();

    const confirmBtn = fixture.nativeElement.querySelector('[data-testid="confirm-unpair-btn"]');
    expect(confirmBtn).not.toBeNull();
    confirmBtn.click();
    fixture.detectChanges();

    expect(mockFacade.unpair).toHaveBeenCalledTimes(1);
  });

  it('calls facade.retire() when confirm retire button is clicked', async () => {
    await createComponent({}, { detail: makeDetailVM({ canManage: true }) });

    (fixture.componentInstance as unknown as { confirmRetire: () => void }).confirmRetire();
    fixture.detectChanges();

    const confirmBtn = fixture.nativeElement.querySelector('[data-testid="confirm-retire-btn"]');
    expect(confirmBtn).not.toBeNull();
    confirmBtn.click();
    fixture.detectChanges();

    expect(mockFacade.retire).toHaveBeenCalledTimes(1);
  });

  it('hides unpair dialog when cancel is clicked', async () => {
    await createComponent({}, { detail: makeDetailVM({ canManage: true }) });

    (fixture.componentInstance as unknown as { confirmUnpair: () => void }).confirmUnpair();
    fixture.detectChanges();

    // Click cancel button (second button in dialog)
    const buttons = fixture.nativeElement
      .querySelector('[data-testid="unpair-confirm-dialog"]')
      ?.querySelectorAll('button');
    const cancelBtn = Array.from(buttons as NodeListOf<HTMLElement>).find((b) =>
      b.textContent?.includes('Cancelar'),
    );
    cancelBtn?.click();
    fixture.detectChanges();

    const dialog = fixture.nativeElement.querySelector('[data-testid="unpair-confirm-dialog"]');
    expect(dialog).toBeNull();
  });

  // ---------------------------------------------------------------------------
  // Loading skeleton — FE-EDGE-018 DoD
  // ---------------------------------------------------------------------------

  it('renders animated skeleton placeholders while loading (no flash of empty)', async () => {
    await createComponent({}, { loading: true });

    const skeleton = fixture.nativeElement.querySelector('[data-testid="loading-indicator"]');
    expect(skeleton).not.toBeNull();
    // Skeleton must contain animate-pulse elements (no spinner alone)
    const pulseEls = skeleton?.querySelectorAll('.animate-pulse');
    expect(pulseEls?.length).toBeGreaterThan(0);
  });

  it('does not render not-paired state while loading', async () => {
    await createComponent({}, { loading: true, detail: null });

    const notPairedEl = fixture.nativeElement.querySelector('[data-testid="not-paired-state"]');
    expect(notPairedEl).toBeNull();
  });

  // ---------------------------------------------------------------------------
  // Not-paired caregiver path — FE-EDGE-018 DoD
  // ---------------------------------------------------------------------------

  it('renders pairing CTA when user is conductor and device is not paired', async () => {
    await createComponent({}, { detail: null, canManage: true });

    const pairingCta = fixture.nativeElement.querySelector('[data-testid="pairing-cta"]');
    expect(pairingCta).not.toBeNull();
  });

  it('renders informational message when user is caregiver and device is not paired (no management affordance)', async () => {
    await createComponent({}, { detail: null, canManage: false });

    const caregiverMsg = fixture.nativeElement.querySelector('[data-testid="not-paired-caregiver-msg"]');
    expect(caregiverMsg).not.toBeNull();
    // Must NOT show the pairing CTA
    const pairingCta = fixture.nativeElement.querySelector('[data-testid="pairing-cta"]');
    expect(pairingCta).toBeNull();
  });

  it('caregiver not-paired message does not contain actionable elements', async () => {
    await createComponent({}, { detail: null, canManage: false });

    const notPairedEl = fixture.nativeElement.querySelector('[data-testid="not-paired-state"]');
    // No links or buttons in the caregiver path
    const links = notPairedEl?.querySelectorAll('a');
    const buttons = notPairedEl?.querySelectorAll('button');
    expect(links?.length ?? 0).toBe(0);
    expect(buttons?.length ?? 0).toBe(0);
  });
});
