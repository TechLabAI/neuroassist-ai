// =============================================================================
// device-meta-list.component.spec.ts — FE-EDGE-013 DoD
//
// Covers:
//   - Renders deviceId value in the template
//   - Renders versao when provided; shows "—" when null
//   - Renders lastSyncRelative when provided
//   - Renders tenantRef in the template
//   - Shows "Desatualizado" (warn) when profileDrift is true
//   - Shows "Atualizado" when profileDrift is false
//   - Renders runningProfileVersion / currentProfileVersion when non-null
//   - Does NOT render the profile version row when runningProfileVersion is null
// =============================================================================

import { TestBed, ComponentFixture } from '@angular/core/testing';
import { DeviceMetaListComponent } from './device-meta-list.component';
import { DispositivoEdgeView } from '@neuroassist/shared/contracts';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeDevice(overrides: Partial<DispositivoEdgeView> = {}): DispositivoEdgeView {
  return {
    deviceId: 'dev-meta-001',
    tenantRef: 'tenant-spec',
    pacienteId: 'pac-test',
    pairingState: 'paired',
    status: 'online',
    ultimaSincronizacao: new Date().toISOString(),
    versao: '1.3.0',
    runningProfileVersion: 5,
    currentProfileVersion: 5,
    profileDrift: false,
    stalenessThresholdSeconds: 90,
    serverTime: new Date().toISOString(),
    ...overrides,
  };
}

describe('DeviceMetaListComponent', () => {
  let fixture: ComponentFixture<DeviceMetaListComponent>;

  function setDeviceAndDetect(
    device: DispositivoEdgeView,
    lastSyncRelative = 'há 1 min',
  ): void {
    fixture.componentRef.setInput('device', device);
    fixture.componentRef.setInput('lastSyncRelative', lastSyncRelative);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DeviceMetaListComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(DeviceMetaListComponent);
    setDeviceAndDetect(makeDevice());
  });

  afterEach(() => TestBed.resetTestingModule());

  // ---------------------------------------------------------------------------
  // Core metadata fields
  // ---------------------------------------------------------------------------

  it('renders the deviceId in the template', () => {
    setDeviceAndDetect(makeDevice({ deviceId: 'dev-xyz-123' }));
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('dev-xyz-123');
  });

  it('renders the tenantRef in the template', () => {
    setDeviceAndDetect(makeDevice({ tenantRef: 'tenant-acme' }));
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('tenant-acme');
  });

  it('renders versao when provided', () => {
    setDeviceAndDetect(makeDevice({ versao: '3.7.2' }));
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('3.7.2');
  });

  it('renders "—" when versao is null', () => {
    setDeviceAndDetect(makeDevice({ versao: null }));
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('—');
  });

  it('renders lastSyncRelative when provided', () => {
    setDeviceAndDetect(makeDevice(), 'há 5 min');
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('há 5 min');
  });

  // ---------------------------------------------------------------------------
  // Profile drift indicator (DoD P3)
  // ---------------------------------------------------------------------------

  it('shows "Desatualizado" in warn tone when profileDrift is true', () => {
    setDeviceAndDetect(makeDevice({ profileDrift: true }));
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Desatualizado');

    const warnEl = fixture.nativeElement.querySelector('.text-warn-ink');
    expect(warnEl).not.toBeNull();
  });

  it('shows "Atualizado" when profileDrift is false', () => {
    setDeviceAndDetect(makeDevice({ profileDrift: false }));
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Atualizado');
  });

  // ---------------------------------------------------------------------------
  // Profile version row
  // ---------------------------------------------------------------------------

  it('renders the profile version row when runningProfileVersion is not null', () => {
    setDeviceAndDetect(
      makeDevice({ runningProfileVersion: 7, currentProfileVersion: 9 }),
    );
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('7');
    expect(text).toContain('9');
    expect(text).toContain('Perfil (ativo / atual)');
  });

  it('does NOT render the profile version row when runningProfileVersion is null', () => {
    setDeviceAndDetect(
      makeDevice({ runningProfileVersion: null, currentProfileVersion: null }),
    );
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).not.toContain('Perfil (ativo / atual)');
  });

  // ---------------------------------------------------------------------------
  // Reactivity — input updates propagate
  // ---------------------------------------------------------------------------

  it('updates all fields when device input changes', () => {
    setDeviceAndDetect(makeDevice({ deviceId: 'dev-before', versao: '1.0.0' }));
    let text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('dev-before');

    setDeviceAndDetect(makeDevice({ deviceId: 'dev-after', versao: '2.0.0' }));
    text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('dev-after');
    expect(text).toContain('2.0.0');
    expect(text).not.toContain('dev-before');
  });
});
