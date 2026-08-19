// =============================================================================
// edge-telemetry.spec.ts — Telemetry events unit tests (FE-EDGE-020 / P8)
//
// Covers:
//   - edge.device.paired  — emitted when pairing completes with first heartbeat
//   - edge.device.offline — emitted when poll detects derivedState transition to offline
//   - edge.device.poll_failure — emitted when poll request fails
//   - edge.device.retired — emitted when retire() command succeeds
//   - Privacy: no PII fields (enrollmentToken, clientSecret) in any event payload
// =============================================================================

import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { DeviceFacade } from './edge.facade';
import { PairingFacade } from './edge-pairing.facade';
import { DEVICE_PORT, DevicePort } from './api/device.port';
import { PAIRING_PORT, PairingPort } from './api/pairing.port';
import { ObservabilityService } from '@neuroassist/shared/observability';
import {
  DispositivoEdgeView,
  LivenessView,
  RegisterDeviceResult,
} from '@neuroassist/shared/contracts';

// ---------------------------------------------------------------------------
// Shared helpers
// ---------------------------------------------------------------------------

const FIXED_NOW = 1_000_000_000_000; // deterministic timestamp anchor

function makePairedDevice(
  overrides: Partial<DispositivoEdgeView> = {},
): DispositivoEdgeView {
  return {
    deviceId: 'dev-001',
    tenantRef: 'tenant-test',
    pacienteId: 'pac-test',
    pairingState: 'paired',
    status: 'online',
    ultimaSincronizacao: new Date(FIXED_NOW - 10_000).toISOString(), // 10 s ago (fresh)
    versao: '1.2.3',
    runningProfileVersion: 2,
    currentProfileVersion: 2,
    profileDrift: false,
    stalenessThresholdSeconds: 90,
    serverTime: new Date(FIXED_NOW).toISOString(),
    ...overrides,
  };
}

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
    ultimaSincronizacao: new Date(now - 5_000).toISOString(), // 5 s ago — within staleness window
    versao: '1.4.2',
    runningProfileVersion: 3,
    currentProfileVersion: 3,
    profileDrift: false,
    stalenessThresholdSeconds: 90,
    serverTime: new Date(now).toISOString(),
  };
}

function makeRegisterResult(
  overrides: Partial<RegisterDeviceResult> = {},
): RegisterDeviceResult {
  return {
    deviceId: 'dev-pairing-001',
    pairingState: 'registered',
    label: 'Dispositivo Teste',
    enrollmentToken: 'TOKEN-ABCD-1234',
    enrollmentExpiresAt: new Date(Date.now() + 5 * 60_000).toISOString(),
    createdAt: new Date().toISOString(),
    ...overrides,
  };
}

function makeUnclaimedDevice(deviceId = 'dev-pairing-001'): DispositivoEdgeView {
  return {
    deviceId,
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

function createMockPairingPort(): jest.Mocked<PairingPort> {
  return {
    register: jest.fn(),
    pairToPatient: jest.fn(),
  };
}

// ---------------------------------------------------------------------------
// DeviceFacade telemetry tests
// ---------------------------------------------------------------------------

describe('DeviceFacade — Telemetry (FE-EDGE-020)', () => {
  let facade: DeviceFacade;
  let mockPort: jest.Mocked<DevicePort>;
  let observabilitySpy: jest.Mocked<ObservabilityService>;

  beforeEach(() => {
    mockPort = createMockDevicePort();
    observabilitySpy = { emit: jest.fn() } as unknown as jest.Mocked<ObservabilityService>;

    TestBed.configureTestingModule({
      providers: [
        DeviceFacade,
        { provide: DEVICE_PORT, useValue: mockPort },
        { provide: ObservabilityService, useValue: observabilitySpy },
      ],
    });

    facade = TestBed.inject(DeviceFacade);
  });

  afterEach(() => {
    facade.stopPolling();
    TestBed.resetTestingModule();
  });

  // -------------------------------------------------------------------------
  // edge.device.poll_failure
  // -------------------------------------------------------------------------

  describe('edge.device.poll_failure', () => {
    it('emits poll_failure event when poll request errors', fakeAsync(() => {
      const device = makePairedDevice({ pacienteId: 'pac-test' });
      mockPort.getDevice.mockReturnValue(of(device));
      facade.load('dev-001');

      mockPort.pollPatientLiveness.mockReturnValue(
        throwError(() => new Error('network timeout')),
      );

      facade.startPolling();
      tick(0); // fire initial poll at t=0

      expect(observabilitySpy.emit).toHaveBeenCalledWith(
        expect.objectContaining({
          event: 'edge.device.poll_failure',
          moduleKey: 'edge',
        }),
      );

      facade.stopPolling();
    }));

    it('poll_failure payload includes deviceId but no PII credential fields', fakeAsync(() => {
      const device = makePairedDevice({ pacienteId: 'pac-test' });
      mockPort.getDevice.mockReturnValue(of(device));
      facade.load('dev-001');

      mockPort.pollPatientLiveness.mockReturnValue(
        throwError(() => new Error('timeout')),
      );

      facade.startPolling();
      tick(0);

      const calls = observabilitySpy.emit.mock.calls;
      const failureCall = calls.find(([p]) => p['event'] === 'edge.device.poll_failure');
      expect(failureCall).toBeDefined();

      const payload = failureCall![0];
      expect(payload).not.toHaveProperty('enrollmentToken');
      expect(payload).not.toHaveProperty('clientSecret');
      expect(payload['deviceId']).toBe('dev-001');

      facade.stopPolling();
    }));
  });

  // -------------------------------------------------------------------------
  // edge.device.offline
  // -------------------------------------------------------------------------

  describe('edge.device.offline', () => {
    it('emits offline event when poll liveness detects transition to offline', fakeAsync(() => {
      // Start with online device
      const onlineDevice = makePairedDevice({
        pacienteId: 'pac-test',
        serverTime: new Date(FIXED_NOW).toISOString(),
        ultimaSincronizacao: new Date(FIXED_NOW - 10_000).toISOString(), // 10 s ago → online
      });
      mockPort.getDevice.mockReturnValue(of(onlineDevice));
      facade.load('dev-001');
      expect(facade.derivedState()).toBe('online');

      // Poll returns liveness where device is now offline (age > 90 s offlineWindowMs)
      const offlineLiveness: LivenessView = {
        deviceId: 'dev-001',
        pacienteId: 'pac-test',
        status: 'offline',
        ultimaSincronizacao: new Date(FIXED_NOW - 150_000).toISOString(), // 150 s ago → offline
        versao: '1.2.3',
        stalenessThresholdSeconds: 90,
        serverTime: new Date(FIXED_NOW).toISOString(),
      };
      mockPort.pollPatientLiveness.mockReturnValue(of(offlineLiveness));

      facade.startPolling();
      tick(0); // first poll fires immediately

      expect(observabilitySpy.emit).toHaveBeenCalledWith(
        expect.objectContaining({
          event: 'edge.device.offline',
          moduleKey: 'edge',
          derivedState: 'offline',
        }),
      );

      facade.stopPolling();
    }));

    it('offline payload does not include enrollmentToken or clientSecret', fakeAsync(() => {
      const onlineDevice = makePairedDevice({
        pacienteId: 'pac-test',
        serverTime: new Date(FIXED_NOW).toISOString(),
        ultimaSincronizacao: new Date(FIXED_NOW - 10_000).toISOString(),
      });
      mockPort.getDevice.mockReturnValue(of(onlineDevice));
      facade.load('dev-001');

      const offlineLiveness: LivenessView = {
        deviceId: 'dev-001',
        pacienteId: 'pac-test',
        status: 'offline',
        ultimaSincronizacao: new Date(FIXED_NOW - 150_000).toISOString(),
        versao: '1.2.3',
        stalenessThresholdSeconds: 90,
        serverTime: new Date(FIXED_NOW).toISOString(),
      };
      mockPort.pollPatientLiveness.mockReturnValue(of(offlineLiveness));

      facade.startPolling();
      tick(0);

      const calls = observabilitySpy.emit.mock.calls;
      const offlineCall = calls.find(([p]) => p['event'] === 'edge.device.offline');
      expect(offlineCall).toBeDefined();

      const payload = offlineCall![0];
      expect(payload).not.toHaveProperty('enrollmentToken');
      expect(payload).not.toHaveProperty('clientSecret');

      facade.stopPolling();
    }));

    it('does NOT emit offline when device stays online across polls', fakeAsync(() => {
      const onlineDevice = makePairedDevice({ pacienteId: 'pac-test' });
      mockPort.getDevice.mockReturnValue(of(onlineDevice));
      facade.load('dev-001');

      // Fresh liveness — still online
      const freshLiveness: LivenessView = {
        deviceId: 'dev-001',
        pacienteId: 'pac-test',
        status: 'online',
        ultimaSincronizacao: new Date(FIXED_NOW - 5_000).toISOString(), // 5 s ago
        versao: '1.2.3',
        stalenessThresholdSeconds: 90,
        serverTime: new Date(FIXED_NOW).toISOString(),
      };
      mockPort.pollPatientLiveness.mockReturnValue(of(freshLiveness));

      facade.startPolling();
      tick(0);

      const offlineCalls = observabilitySpy.emit.mock.calls.filter(
        ([p]) => p['event'] === 'edge.device.offline',
      );
      expect(offlineCalls).toHaveLength(0);

      facade.stopPolling();
    }));
  });

  // -------------------------------------------------------------------------
  // edge.device.retired
  // -------------------------------------------------------------------------

  describe('edge.device.retired', () => {
    it('emits retired event when retire() command succeeds', () => {
      const device = makePairedDevice();
      mockPort.getDevice.mockReturnValue(of(device));
      facade.load('dev-001');

      const retiredDevice: DispositivoEdgeView = {
        ...device,
        pairingState: 'retired',
        status: 'n/a',
      };
      mockPort.retire.mockReturnValue(of(retiredDevice));
      facade.retire();

      expect(observabilitySpy.emit).toHaveBeenCalledWith(
        expect.objectContaining({
          event: 'edge.device.retired',
          moduleKey: 'edge',
          deviceId: 'dev-001',
        }),
      );
    });

    it('retired payload does not include enrollmentToken or clientSecret', () => {
      const device = makePairedDevice();
      mockPort.getDevice.mockReturnValue(of(device));
      facade.load('dev-001');

      const retiredDevice: DispositivoEdgeView = {
        ...device,
        pairingState: 'retired',
        status: 'n/a',
      };
      mockPort.retire.mockReturnValue(of(retiredDevice));
      facade.retire();

      const calls = observabilitySpy.emit.mock.calls;
      const retiredCall = calls.find(([p]) => p['event'] === 'edge.device.retired');
      expect(retiredCall).toBeDefined();

      const payload = retiredCall![0];
      expect(payload).not.toHaveProperty('enrollmentToken');
      expect(payload).not.toHaveProperty('clientSecret');
    });
  });
});

// ---------------------------------------------------------------------------
// PairingFacade telemetry tests
// ---------------------------------------------------------------------------

describe('PairingFacade — Telemetry (FE-EDGE-020)', () => {
  let facade: PairingFacade;
  let mockPairingPort: jest.Mocked<PairingPort>;
  let mockDevicePort: jest.Mocked<DevicePort>;
  let observabilitySpy: jest.Mocked<ObservabilityService>;

  beforeEach(() => {
    mockPairingPort = createMockPairingPort();
    mockDevicePort = createMockDevicePort();
    observabilitySpy = { emit: jest.fn() } as unknown as jest.Mocked<ObservabilityService>;

    TestBed.configureTestingModule({
      providers: [
        PairingFacade,
        { provide: PAIRING_PORT, useValue: mockPairingPort },
        { provide: DEVICE_PORT, useValue: mockDevicePort },
        { provide: ObservabilityService, useValue: observabilitySpy },
      ],
    });

    facade = TestBed.inject(PairingFacade);
  });

  afterEach(() => {
    facade.stopPolling();
    TestBed.resetTestingModule();
  });

  // -------------------------------------------------------------------------
  // edge.device.paired
  // -------------------------------------------------------------------------

  describe('edge.device.paired', () => {
    it('emits paired event when pairing completes with confirmed first heartbeat', fakeAsync(() => {
      mockPairingPort.register.mockReturnValue(of(makeRegisterResult()));

      const pairedDevice = makeFreshlyPairedDevice('dev-pairing-001', 'pac-test');
      mockDevicePort.getDevice
        .mockReturnValueOnce(of(makeUnclaimedDevice())) // t=2000 — unclaimed
        .mockReturnValueOnce(of(pairedDevice));          // t=7000 — fresh heartbeat
      mockPairingPort.pairToPatient.mockReturnValue(of(pairedDevice));

      facade.register('My Device', 'pac-test');
      expect(facade.phase()).toBe('awaiting-claim');

      tick(2000); // first claim poll — unclaimed
      tick(5000); // second claim poll — fresh heartbeat detected → paired

      expect(observabilitySpy.emit).toHaveBeenCalledWith(
        expect.objectContaining({
          event: 'edge.device.paired',
          moduleKey: 'edge',
          derivedState: 'online',
        }),
      );

      facade.stopPolling();
    }));

    it('does NOT emit paired until real heartbeat is confirmed', fakeAsync(() => {
      mockPairingPort.register.mockReturnValue(of(makeRegisterResult()));
      mockDevicePort.getDevice.mockReturnValue(of(makeUnclaimedDevice()));

      facade.register('My Device', 'pac-test');
      tick(2000); // first poll — still unclaimed

      const pairedCalls = observabilitySpy.emit.mock.calls.filter(
        ([p]) => p['event'] === 'edge.device.paired',
      );
      expect(pairedCalls).toHaveLength(0);

      facade.stopPolling();
    }));

    it('paired payload does not include enrollmentToken or clientSecret', fakeAsync(() => {
      mockPairingPort.register.mockReturnValue(of(makeRegisterResult()));

      const pairedDevice = makeFreshlyPairedDevice('dev-pairing-001', 'pac-test');
      mockDevicePort.getDevice
        .mockReturnValueOnce(of(makeUnclaimedDevice()))
        .mockReturnValueOnce(of(pairedDevice));
      mockPairingPort.pairToPatient.mockReturnValue(of(pairedDevice));

      facade.register('My Device', 'pac-test');
      tick(2000);
      tick(5000);

      const calls = observabilitySpy.emit.mock.calls;
      const pairedCall = calls.find(([p]) => p['event'] === 'edge.device.paired');
      expect(pairedCall).toBeDefined();

      const payload = pairedCall![0];
      expect(payload).not.toHaveProperty('enrollmentToken');
      expect(payload).not.toHaveProperty('clientSecret');
      // Verified: moduleKey 'edge' is present, no biometric or credential fields
      expect(payload['moduleKey']).toBe('edge');
      expect(payload['deviceId']).toBe('dev-pairing-001');

      facade.stopPolling();
    }));
  });
});
