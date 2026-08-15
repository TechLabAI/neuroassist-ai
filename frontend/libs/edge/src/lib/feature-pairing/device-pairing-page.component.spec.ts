// =============================================================================
// device-pairing-page.component.spec.ts — FE-EDGE-011 DoD
//
// Covers:
//   - Renders "Solicitar código" button in idle phase
//   - Calls PairingFacade.register() when the button is clicked
//   - Does NOT transition to "paired" on register() alone (no-premature-paired)
//   - Shows awaiting-claim UI when phase is "awaiting-claim"
//   - Shows spinner when phase is "registering"
//   - Shows result panel when phase is "paired"
// =============================================================================

import { TestBed, ComponentFixture } from '@angular/core/testing';
import { signal, computed } from '@angular/core';
import { provideRouter } from '@angular/router';
import { DevicePairingPageComponent } from './device-pairing-page.component';
import { PairingCodeCardComponent } from './pairing-code-card.component';
import { PairingProgressComponent } from './pairing-progress.component';
import { PairingResultComponent } from './pairing-result.component';
import { PairingFacade } from '../edge-pairing.facade';
import {
  PairingPhase,
  PairingVM,
} from '@neuroassist/shared/contracts';

// ---------------------------------------------------------------------------
// Mock PairingFacade
// ---------------------------------------------------------------------------

function idleVm(): PairingVM {
  return {
    phase: 'idle',
    deviceId: null,
    enrollmentToken: null,
    enrollmentExpiresAtIso: null,
    pacienteId: null,
    versao: null,
    derivedState: 'not-paired',
    errorMessage: null,
  };
}

function makeMockFacade(initialPhase: PairingPhase = 'idle') {
  const _vm = signal<PairingVM>({ ...idleVm(), phase: initialPhase });

  return {
    state: _vm.asReadonly(),
    phase: computed<PairingPhase>(() => _vm().phase),
    countdown: computed<number>(() => {
      const exp = _vm().enrollmentExpiresAtIso;
      if (!exp) return 0;
      return Math.max(0, Math.floor((new Date(exp).getTime() - Date.now()) / 1000));
    }),
    lastOutcome: signal(null).asReadonly(),
    register: jest.fn(),
    retry: jest.fn(),
    stopPolling: jest.fn(),
    // Mutator for test scenarios
    _setPhase: (phase: PairingPhase, overrides: Partial<PairingVM> = {}) =>
      _vm.set({ ...idleVm(), phase, ...overrides }),
  };
}

describe('DevicePairingPageComponent', () => {
  let fixture: ComponentFixture<DevicePairingPageComponent>;
  let mockFacade: ReturnType<typeof makeMockFacade>;

  async function createComponent(initialPhase: PairingPhase = 'idle') {
    mockFacade = makeMockFacade(initialPhase);

    await TestBed.configureTestingModule({
      imports: [
        DevicePairingPageComponent,
        PairingCodeCardComponent,
        PairingProgressComponent,
        PairingResultComponent,
      ],
      providers: [
        provideRouter([]),
        { provide: PairingFacade, useValue: mockFacade },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DevicePairingPageComponent);
    fixture.detectChanges();
  }

  afterEach(() => TestBed.resetTestingModule());

  // ---------------------------------------------------------------------------
  // Idle phase
  // ---------------------------------------------------------------------------

  it('renders "Solicitar código" button in idle phase', async () => {
    await createComponent('idle');
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Solicitar código');
  });

  // ---------------------------------------------------------------------------
  // register() call — DoD P3
  // ---------------------------------------------------------------------------

  it('calls PairingFacade.register() when "Solicitar código" is clicked', async () => {
    await createComponent('idle');

    const buttons = fixture.nativeElement.querySelectorAll<HTMLElement>('button');
    const registerBtn = Array.from(buttons).find((b) =>
      b.textContent?.includes('Solicitar código'),
    );
    expect(registerBtn).toBeTruthy();
    registerBtn!.click();
    fixture.detectChanges();

    expect(mockFacade.register).toHaveBeenCalledTimes(1);
    expect(mockFacade.register).toHaveBeenCalledWith(
      'Dispositivo NeuroAssist',
      undefined,
    );
  });

  it('calls PairingFacade.register() with pacienteHint when input is provided', async () => {
    await createComponent('idle');
    fixture.componentRef.setInput('pacienteHint', 'pac-marcio');
    fixture.detectChanges();

    const buttons = fixture.nativeElement.querySelectorAll<HTMLElement>('button');
    const registerBtn = Array.from(buttons).find((b) =>
      b.textContent?.includes('Solicitar código'),
    );
    registerBtn!.click();
    fixture.detectChanges();

    expect(mockFacade.register).toHaveBeenCalledWith(
      'Dispositivo NeuroAssist',
      'pac-marcio',
    );
  });

  // ---------------------------------------------------------------------------
  // No-premature-paired invariant (DoD P3)
  // register() alone MUST NOT transition to 'paired'.
  // ---------------------------------------------------------------------------

  it('does NOT show the success result panel immediately after register() is called (no-premature-paired)', async () => {
    await createComponent('idle');

    // register() is called but the mock does NOT change phase
    const buttons = fixture.nativeElement.querySelectorAll<HTMLElement>('button');
    const registerBtn = Array.from(buttons).find((b) =>
      b.textContent?.includes('Solicitar código'),
    );
    registerBtn!.click();
    fixture.detectChanges();

    // Phase is still 'idle' (mock facade didn't transition)
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).not.toContain('Dispositivo pareado!');
  });

  it('only shows success panel when facade phase is "paired" (heartbeat confirmed)', async () => {
    await createComponent('idle');

    // Simulate facade having reached 'paired' AFTER heartbeat
    TestBed.runInInjectionContext(() => {
      mockFacade._setPhase('paired', { versao: '1.2.3' });
    });
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Dispositivo pareado!');
  });

  // ---------------------------------------------------------------------------
  // Registering phase
  // ---------------------------------------------------------------------------

  it('shows spinner text when phase is "registering"', async () => {
    await createComponent('registering');
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Solicitando código ao edge-service');
  });

  // ---------------------------------------------------------------------------
  // Awaiting-claim phase
  // ---------------------------------------------------------------------------

  it('shows the pairing code card when phase is "awaiting-claim"', async () => {
    await createComponent('idle');

    TestBed.runInInjectionContext(() => {
      mockFacade._setPhase('awaiting-claim', {
        enrollmentToken: 'XYZ-9999',
        enrollmentExpiresAtIso: new Date(Date.now() + 300_000).toISOString(),
      });
    });
    fixture.detectChanges();

    const codeCard = fixture.nativeElement.querySelector('edge-pairing-code-card');
    expect(codeCard).toBeTruthy();
  });

  // ---------------------------------------------------------------------------
  // retry()
  // ---------------------------------------------------------------------------

  it('calls PairingFacade.retry() when onRetry() is triggered (unit test)', async () => {
    await createComponent('idle');

    // Unit test: verify the delegation onRetry() → facade.retry().
    // Directly calling the protected method via type cast is the standard
    // Angular unit-test pattern for this (avoids JIT output() propagation
    // flakiness across component boundaries in ts-jest mode).
    (fixture.componentInstance as unknown as { onRetry(): void }).onRetry();

    expect(mockFacade.retry).toHaveBeenCalledTimes(1);
  });
});
