// =============================================================================
// edge-pairing.facade.spec.ts — PairingFacade unit tests (FE-EDGE-008)
//
// Covers:
//   - Initial state (idle)
//   - register(): transitions to awaiting-claim
//   - Key invariant: NOT paired until real first heartbeat confirmed
//   - Claim poll: agent claim detected → advance to awaiting-first-heartbeat
//   - handleFirstHeartbeat: only advances to paired when fresh ultímaSincronizacao
//   - TTL expiration: enrollmentToken expired → phase='expired'
//   - reset(): returns to idle
//   - register() error path
// =============================================================================

import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
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

function makeRegisterResult(overrides: Partial<RegisterDeviceResult> = {}): RegisterDeviceResult {
  return {
    deviceId: 'dev-pairing-001',
    pairingState: 'registered',
    label: 'Dispositivo Teste',
    enrollmentToken: 'TOKEN-ABCD-1234',
    enrollmentExpiresAt: new Date(Date.now() + 5 * 60_000).toISOString(), // 5 min from now
    createdAt: new Date().toISOString(),
    ...overrides,
  };
}

/** Device registered but agent has NOT yet claimed (no heartbeat). */
function makeUnclaimedDevice(deviceId = 'dev-pairing-001'): DispositivoEdgeView {
  return {
    deviceId,
    tenantRef: 'tenant-test',
    pacienteId: null,
    pairingState: 'registered',
    status: 'n/a',
    ultimaSincronizacao: null, // Agent hasn't heartbeated yet
    versao: null,
    runningProfileVersion: null,
    currentProfileVersion: null,
    profileDrift: false,
    stalenessThresholdSeconds: 90,
    serverTime: new Date().toISOString(),
  };
}

/** Device fully paired with a fresh first heartbeat. */
function makeFreshlyPairedDevice(
  deviceId = 'dev-pairing-001',
  pacienteId = 'pac-test',
): DispositivoEdgeView {
  const now = Date.now();
  return {
    deviceId,
    tenantRef: 'tenant-test',
    pacienteId,
    pairingState: 'paired',
    status: 'online',
    ultimaSincronizacao: new Date(now - 5_000).toISOString(), // 5s ago — within staleness window
    versao: '1.4.2',
    runningProfileVersion: 3,
    currentProfileVersion: 3,
    profileDrift: false,
    stalenessThresholdSeconds: 90,
    serverTime: new Date(now).toISOString(),
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
// Tests
// ---------------------------------------------------------------------------

describe('PairingFacade', () => {
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
  // Initial state
  // -------------------------------------------------------------------------

  describe('initial state', () => {
    it('phase is idle', () => {
      expect(facade.phase()).toBe('idle');
    });

    it('state.enrollmentToken is null', () => {
      expect(facade.state().enrollmentToken).toBeNull();
    });

    it('state.deviceId is null', () => {
      expect(facade.state().deviceId).toBeNull();
    });

    it('state.derivedState is not-paired', () => {
      expect(facade.state().derivedState).toBe('not-paired');
    });

    it('lastOutcome is null', () => {
      expect(facade.lastOutcome()).toBeNull();
    });

    it('countdown is 0 (no active token)', () => {
      expect(facade.countdown()).toBe(0);
    });
  });

  // -------------------------------------------------------------------------
  // register() — happy path
  // -------------------------------------------------------------------------

  describe('register()', () => {
    it('transitions to awaiting-claim after successful register()', () => {
      mockPairingPort.register.mockReturnValue(of(makeRegisterResult()));
      mockDevicePort.getDevice.mockReturnValue(of(makeUnclaimedDevice()));

      facade.register('My Device');

      expect(facade.phase()).toBe('awaiting-claim');
    });

    it('stores enrollmentToken in state after register()', () => {
      mockPairingPort.register.mockReturnValue(of(makeRegisterResult()));
      mockDevicePort.getDevice.mockReturnValue(of(makeUnclaimedDevice()));

      facade.register('My Device');

      expect(facade.state().enrollmentToken).toBe('TOKEN-ABCD-1234');
    });

    it('stores deviceId in state after register()', () => {
      mockPairingPort.register.mockReturnValue(of(makeRegisterResult()));
      mockDevicePort.getDevice.mockReturnValue(of(makeUnclaimedDevice()));

      facade.register('My Device');

      expect(facade.state().deviceId).toBe('dev-pairing-001');
    });

    it('stores pacienteHint when provided', () => {
      mockPairingPort.register.mockReturnValue(of(makeRegisterResult()));
      mockDevicePort.getDevice.mockReturnValue(of(makeUnclaimedDevice()));

      facade.register('My Device', 'pac-marcio');

      expect(facade.state().pacienteId).toBe('pac-marcio');
    });

    it('transitions to error phase on register() API failure', () => {
      mockPairingPort.register.mockReturnValue(throwError(() => new Error('500')));

      facade.register('My Device');

      expect(facade.phase()).toBe('error');
    });

    it('sets error message in pt-BR on register() failure', () => {
      mockPairingPort.register.mockReturnValue(throwError(() => new Error('500')));

      facade.register('My Device');

      expect(facade.state().errorMessage).toBeTruthy();
    });
  });

  // -------------------------------------------------------------------------
  // No-premature-paired invariant (FE-EDGE-008 DoD)
  // KEY SAFETY INVARIANT: PairingFacade MUST NOT advance to 'paired' until
  // the first real heartbeat is confirmed (ultimaSincronizacao present and fresh).
  // -------------------------------------------------------------------------

  describe('no-premature-paired invariant (FE-EDGE-008 DoD)', () => {
    it('is NOT in paired phase immediately after register() — must await agent', () => {
      mockPairingPort.register.mockReturnValue(of(makeRegisterResult()));
      mockDevicePort.getDevice.mockReturnValue(of(makeUnclaimedDevice()));

      facade.register('My Device');

      // register() alone MUST NOT produce 'paired'
      expect(facade.phase()).not.toBe('paired');
      expect(facade.phase()).toBe('awaiting-claim');
    });

    it('remains in awaiting-claim while claim polls return unclaimed device', fakeAsync(() => {
      mockPairingPort.register.mockReturnValue(of(makeRegisterResult()));
      // Every poll returns unclaimed — agent hasn't heartbeated
      mockDevicePort.getDevice.mockReturnValue(of(makeUnclaimedDevice()));

      facade.register('My Device', 'pac-test');

      expect(facade.phase()).toBe('awaiting-claim');

      tick(2000); // first claim poll fires (timer(2000, 5000))
      expect(facade.phase()).not.toBe('paired');
      expect(facade.phase()).toBe('awaiting-claim');

      tick(5000); // second claim poll — still unclaimed
      expect(facade.phase()).not.toBe('paired');
      expect(facade.phase()).toBe('awaiting-claim');

      facade.stopPolling();
    }));

    it('advances to paired ONLY after first heartbeat is fresh and confirmed', fakeAsync(() => {
      mockPairingPort.register.mockReturnValue(of(makeRegisterResult()));

      const unclaimed = makeUnclaimedDevice();
      const pairedWithHeartbeat = makeFreshlyPairedDevice('dev-pairing-001', 'pac-test');

      mockDevicePort.getDevice
        .mockReturnValueOnce(of(unclaimed))          // t=2000 — not yet claimed
        .mockReturnValueOnce(of(pairedWithHeartbeat)); // t=7000 — first heartbeat arrived

      // pairToPatient is called once agent's heartbeat detected
      mockPairingPort.pairToPatient.mockReturnValue(of(pairedWithHeartbeat));

      facade.register('My Device', 'pac-test');
      expect(facade.phase()).toBe('awaiting-claim');

      tick(2000); // first claim poll — unclaimed device
      expect(facade.phase()).not.toBe('paired'); // still waiting

      tick(5000); // second claim poll — device has fresh heartbeat
      // PairingFacade detects ultimaSincronizacao → calls pairToPatient →
      // handleFirstHeartbeat confirms fresh sync → phase='paired'
      expect(facade.phase()).toBe('paired');

      facade.stopPolling();
    }));

    it('does not emit lastOutcome until phase="paired" is reached', fakeAsync(() => {
      mockPairingPort.register.mockReturnValue(of(makeRegisterResult()));
      mockDevicePort.getDevice.mockReturnValue(of(makeUnclaimedDevice()));

      facade.register('My Device', 'pac-test');

      tick(2000); // first poll — still unclaimed
      expect(facade.lastOutcome()).toBeNull(); // no outcome before paired

      facade.stopPolling();
    }));
  });

  // -------------------------------------------------------------------------
  // TTL expiration
  // -------------------------------------------------------------------------

  describe('enrollment token TTL expiration', () => {
    it('transitions to expired when token TTL elapses while in awaiting-claim', fakeAsync(() => {
      // Token that expires in 2 seconds from now
      const shortTtlResult = makeRegisterResult({
        enrollmentExpiresAt: new Date(Date.now() + 2_000).toISOString(),
      });
      mockPairingPort.register.mockReturnValue(of(shortTtlResult));
      mockDevicePort.getDevice.mockReturnValue(of(makeUnclaimedDevice()));

      facade.register('My Device');
      expect(facade.phase()).toBe('awaiting-claim');

      tick(3_000); // advance past the 2s TTL

      expect(facade.phase()).toBe('expired');
    }));

    it('sets user-facing pt-BR error message on expiry', fakeAsync(() => {
      const shortTtlResult = makeRegisterResult({
        enrollmentExpiresAt: new Date(Date.now() + 1_000).toISOString(),
      });
      mockPairingPort.register.mockReturnValue(of(shortTtlResult));
      mockDevicePort.getDevice.mockReturnValue(of(makeUnclaimedDevice()));

      facade.register('My Device');
      tick(2_000);

      expect(facade.phase()).toBe('expired');
      expect(facade.state().errorMessage).toBeTruthy();
    }));

    it('does NOT expire when already paired before TTL elapses', fakeAsync(() => {
      const shortTtlResult = makeRegisterResult({
        enrollmentExpiresAt: new Date(Date.now() + 5_000).toISOString(),
      });
      mockPairingPort.register.mockReturnValue(of(shortTtlResult));

      const pairedDevice = makeFreshlyPairedDevice('dev-pairing-001', 'pac-test');
      mockDevicePort.getDevice.mockReturnValue(of(pairedDevice));
      mockPairingPort.pairToPatient.mockReturnValue(of(pairedDevice));

      facade.register('My Device', 'pac-test');
      tick(2_000); // claim poll fires — device already paired with heartbeat → paired

      expect(facade.phase()).toBe('paired');

      tick(5_000); // TTL would expire here — but already paired, should stay paired
      expect(facade.phase()).toBe('paired'); // onTokenExpired only acts on 'awaiting-claim'

      facade.stopPolling();
    }));
  });

  // -------------------------------------------------------------------------
  // reset()
  // -------------------------------------------------------------------------

  describe('reset()', () => {
    it('returns to idle state and clears all fields', () => {
      mockPairingPort.register.mockReturnValue(of(makeRegisterResult()));
      mockDevicePort.getDevice.mockReturnValue(of(makeUnclaimedDevice()));
      facade.register('My Device');
      expect(facade.phase()).toBe('awaiting-claim');

      facade.reset();

      expect(facade.phase()).toBe('idle');
      expect(facade.state().enrollmentToken).toBeNull();
      expect(facade.state().deviceId).toBeNull();
    });
  });

  // -------------------------------------------------------------------------
  // retry()
  // -------------------------------------------------------------------------

  describe('retry()', () => {
    it('returns to idle when no deviceId is set', () => {
      expect(facade.phase()).toBe('idle');
      facade.retry();
      expect(facade.phase()).toBe('idle');
    });
  });
});
