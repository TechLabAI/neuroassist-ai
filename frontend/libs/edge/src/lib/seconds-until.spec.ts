// =============================================================================
// seconds-until.spec.ts — G2: countdown function unit tests (FE-EDGE-027)
//
// The `secondsUntil(isoStr)` helper drives `PairingFacade.countdown` computed
// signal. This spec validates:
//   - Positive value: isoStr in the future → positive integer returned
//   - Zero / past: isoStr in the past or equal to now → 0 (never negative)
//   - Null / undefined: no expiry date → 0
//
// We test through the PairingFacade.countdown public signal (the function
// itself is module-private). The facade's countdown signal is:
//   computed(() => secondsUntil(this._vm().enrollmentExpiresAtIso))
//
// Also verifies G2 transition: countdown ≤ 0 → phase = 'expired'
// (covered more extensively in edge-pairing.facade.spec.ts).
// =============================================================================

import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { of } from 'rxjs';
import { PairingFacade } from './edge-pairing.facade';
import { PAIRING_PORT, PairingPort } from './api/pairing.port';
import { DEVICE_PORT, DevicePort } from './api/device.port';
import {
  RegisterDeviceResult,
  DispositivoEdgeView,
} from '@neuroassist/shared/contracts';

// ---------------------------------------------------------------------------
// Fixture factories
// ---------------------------------------------------------------------------

function makeUnclaimedDevice(): DispositivoEdgeView {
  return {
    deviceId: 'dev-g2-001',
    tenantRef: 'tenant-test',
    pacienteId: null,
    pairingState: 'registered',
    status: 'n/a',
    ultimaSincronizacao: null,
    versao: null,
    runningProfileVersion: null,
    currentProfileVersion: null,
    profileDrift: false,
    stalenessThresholdSeconds: 90,
    serverTime: new Date().toISOString(),
  };
}

function makeRegisterResult(expiresInMs: number): RegisterDeviceResult {
  return {
    deviceId: 'dev-g2-001',
    pairingState: 'registered',
    label: 'G2 Test Device',
    enrollmentToken: 'TOKEN-G2-TEST',
    enrollmentExpiresAt: new Date(Date.now() + expiresInMs).toISOString(),
    createdAt: new Date().toISOString(),
  };
}

function createMockPairingPort(): jest.Mocked<PairingPort> {
  return {
    register: jest.fn(),
    pairToPatient: jest.fn(),
  };
}

function createMockDevicePort(): jest.Mocked<DevicePort> {
  return {
    getDevice: jest.fn(),
    resolveDeviceForPatient: jest.fn(),
    pollPatientLiveness: jest.fn(),
    pollDeviceLiveness: jest.fn(),
    unpair: jest.fn(),
    retire: jest.fn(),
  };
}

// ---------------------------------------------------------------------------
// G2 — countdown function behavior (via PairingFacade.countdown signal)
// ---------------------------------------------------------------------------

describe('G2 — secondsUntil / PairingFacade.countdown (FE-EDGE-027)', () => {
  let facade: PairingFacade;
  let mockPairingPort: jest.Mocked<PairingPort>;
  let mockDevicePort: jest.Mocked<DevicePort>;

  beforeEach(() => {
    mockPairingPort = createMockPairingPort();
    mockDevicePort = createMockDevicePort();

    TestBed.configureTestingModule({
      providers: [
        PairingFacade,
        { provide: PAIRING_PORT, useValue: mockPairingPort },
        { provide: DEVICE_PORT, useValue: mockDevicePort },
      ],
    });

    facade = TestBed.inject(PairingFacade);
  });

  afterEach(() => {
    facade.stopPolling();
    TestBed.resetTestingModule();
  });

  // -------------------------------------------------------------------------
  // [G2-null] null enrollmentExpiresAtIso → countdown = 0
  // -------------------------------------------------------------------------

  it('[G2-null] countdown is 0 when no token is active (idle phase)', () => {
    // Idle: enrollmentExpiresAtIso is null
    expect(facade.phase()).toBe('idle');
    expect(facade.countdown()).toBe(0);
  });

  // -------------------------------------------------------------------------
  // [G2-positive] Future expiry → countdown > 0
  // -------------------------------------------------------------------------

  it('[G2-positive] countdown returns positive integer for a future expiry', () => {
    // Register with a 5-minute TTL
    mockPairingPort.register.mockReturnValue(of(makeRegisterResult(5 * 60_000)));
    mockDevicePort.getDevice.mockReturnValue(of(makeUnclaimedDevice()));

    facade.register('G2 Test Device');

    // After register, enrollmentExpiresAtIso is set to ~5 min from now.
    const countdown = facade.countdown();
    // Should be ~300 seconds (allow small tolerance for test execution time).
    expect(countdown).toBeGreaterThan(0);
    expect(countdown).toBeLessThanOrEqual(300);
  });

  // -------------------------------------------------------------------------
  // [G2-zero] Past or now expiry → countdown = 0 (never negative)
  // -------------------------------------------------------------------------

  it('[G2-zero] countdown is 0 when token is already expired (past expiresAt)', fakeAsync(() => {
    // Token expired 2 seconds ago.
    mockPairingPort.register.mockReturnValue(of(makeRegisterResult(-2_000)));
    mockDevicePort.getDevice.mockReturnValue(of(makeUnclaimedDevice()));

    facade.register('G2 Expired Device');

    // enrollmentExpiresAtIso is in the past → secondsUntil returns 0.
    // Math.max(0, ...) ensures the signal never goes negative.
    expect(facade.countdown()).toBe(0);
  }));

  // -------------------------------------------------------------------------
  // [G2-transition] countdown ≤ 0 → phase = 'expired'
  // -------------------------------------------------------------------------

  it('[G2-transition] countdown reaching 0 triggers phase transition to expired', fakeAsync(() => {
    // Token expires in 2 seconds.
    mockPairingPort.register.mockReturnValue(of(makeRegisterResult(2_000)));
    mockDevicePort.getDevice.mockReturnValue(of(makeUnclaimedDevice()));

    facade.register('G2 Short-TTL Device');
    expect(facade.phase()).toBe('awaiting-claim');
    expect(facade.countdown()).toBeGreaterThan(0);

    // Advance fake time by 3 seconds (past the 2s TTL).
    tick(3_000);

    // Phase must have transitioned to 'expired'.
    expect(facade.phase()).toBe('expired');
    // countdown is now 0 (expiry in the past).
    expect(facade.countdown()).toBe(0);
    // Error message in pt-BR must be set.
    expect(facade.state().errorMessage).toBeTruthy();
  }));

  // -------------------------------------------------------------------------
  // [G2-floor] secondsUntil floors fractional seconds (integer result)
  // -------------------------------------------------------------------------

  it('[G2-floor] countdown returns an integer (Math.floor applied)', () => {
    // Token expires in 90.7 seconds from now.
    mockPairingPort.register.mockReturnValue(of(makeRegisterResult(90_700)));
    mockDevicePort.getDevice.mockReturnValue(of(makeUnclaimedDevice()));

    facade.register('G2 Floor Test');

    const countdown = facade.countdown();
    // Must be an integer (not 90.7).
    expect(Number.isInteger(countdown)).toBe(true);
    expect(countdown).toBeGreaterThanOrEqual(89);
    expect(countdown).toBeLessThanOrEqual(90);
  });
});
