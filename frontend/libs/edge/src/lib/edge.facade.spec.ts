// =============================================================================
// edge.facade.spec.ts — DeviceFacade unit tests (FE-EDGE-007)
//
// Covers:
//   - Initial signal state
//   - load() success and error paths
//   - derivedState computed signal (via deriveState integration)
//   - startPolling(): ETag/304 path (device unchanged, pollOk=true)
//   - startPolling(): poll failure → unknown (honesty invariant)
//   - connectivity read-model VM shape
// =============================================================================

import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { DeviceFacade } from './edge.facade';
import { DEVICE_PORT, DevicePort } from './api/device.port';
import {
  DispositivoEdgeView,
  LivenessView,
  NotModified,
} from '@neuroassist/shared/contracts';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

const FIXED_NOW = 1_000_000_000_000; // deterministic timestamps

function makePairedDevice(overrides: Partial<DispositivoEdgeView> = {}): DispositivoEdgeView {
  return {
    deviceId: 'dev-001',
    tenantRef: 'tenant-test',
    pacienteId: 'pac-test',
    pairingState: 'paired',
    status: 'online',
    ultimaSincronizacao: new Date(FIXED_NOW - 10_000).toISOString(), // 10s ago (fresh)
    versao: '1.2.3',
    runningProfileVersion: 2,
    currentProfileVersion: 2,
    profileDrift: false,
    stalenessThresholdSeconds: 90,
    serverTime: new Date(FIXED_NOW).toISOString(),
    ...overrides,
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

describe('DeviceFacade', () => {
  let facade: DeviceFacade;
  let mockPort: jest.Mocked<DevicePort>;

  beforeEach(() => {
    mockPort = createMockDevicePort();

    TestBed.configureTestingModule({
      providers: [
        DeviceFacade,
        { provide: DEVICE_PORT, useValue: mockPort },
      ],
    });

    facade = TestBed.inject(DeviceFacade);
  });

  afterEach(() => {
    facade.stopPolling();
    TestBed.resetTestingModule();
  });

  // -------------------------------------------------------------------------
  // Initial state
  // -------------------------------------------------------------------------

  describe('initial state', () => {
    it('device signal is null before any load()', () => {
      expect(facade.device()).toBeNull();
    });

    it('derivedState is not-paired when no device is loaded', () => {
      expect(facade.derivedState()).toBe('not-paired');
    });

    it('loading signal is false initially', () => {
      expect(facade.loading()).toBe(false);
    });

    it('error signal is null initially', () => {
      expect(facade.error()).toBeNull();
    });

    it('connectivity.paired is false initially', () => {
      expect(facade.connectivity().paired).toBe(false);
    });

    it('connectivity.derivedState is not-paired initially', () => {
      expect(facade.connectivity().derivedState).toBe('not-paired');
    });

    it('detail is null before any load()', () => {
      expect(facade.detail()).toBeNull();
    });
  });

  // -------------------------------------------------------------------------
  // load() — success path
  // -------------------------------------------------------------------------

  describe('load() — success path', () => {
    it('populates device signal after successful API call', () => {
      const device = makePairedDevice();
      mockPort.getDevice.mockReturnValue(of(device));

      facade.load('dev-001');

      expect(facade.device()).toEqual(device);
    });

    it('clears loading flag after success', () => {
      mockPort.getDevice.mockReturnValue(of(makePairedDevice()));
      facade.load('dev-001');
      expect(facade.loading()).toBe(false);
    });

    it('clears error signal on success', () => {
      mockPort.getDevice.mockReturnValue(of(makePairedDevice()));
      facade.load('dev-001');
      expect(facade.error()).toBeNull();
    });

    it('derivedState becomes online for a freshly synced device', () => {
      const device = makePairedDevice({
        serverTime: new Date(FIXED_NOW).toISOString(),
        ultimaSincronizacao: new Date(FIXED_NOW - 10_000).toISOString(), // 10s ago
      });
      mockPort.getDevice.mockReturnValue(of(device));

      facade.load('dev-001');

      expect(facade.derivedState()).toBe('online');
    });

    it('derivedState becomes offline when device was paired but never reported', () => {
      const device = makePairedDevice({ ultimaSincronizacao: null });
      mockPort.getDevice.mockReturnValue(of(device));

      facade.load('dev-001');

      expect(facade.derivedState()).toBe('offline');
    });

    it('derivedState becomes not-paired when device is unpaired', () => {
      const device = makePairedDevice({ pairingState: 'unpaired' });
      mockPort.getDevice.mockReturnValue(of(device));

      facade.load('dev-001');

      expect(facade.derivedState()).toBe('not-paired');
    });
  });

  // -------------------------------------------------------------------------
  // load() — error path
  // -------------------------------------------------------------------------

  describe('load() — error path', () => {
    it('sets error signal on API failure', () => {
      mockPort.getDevice.mockReturnValue(throwError(() => new Error('Network error')));
      facade.load('dev-001');
      expect(facade.error()).toBeTruthy();
    });

    it('clears loading flag after error', () => {
      mockPort.getDevice.mockReturnValue(throwError(() => new Error('500')));
      facade.load('dev-001');
      expect(facade.loading()).toBe(false);
    });

    it('derivedState becomes unknown when load fails (poll health set to false)', () => {
      // First, load a valid device to populate state
      const device = makePairedDevice();
      mockPort.getDevice.mockReturnValueOnce(of(device));
      facade.load('dev-001');
      expect(facade.derivedState()).toBe('online');

      // Second load fails — sets _pollOk=false → derivedState=unknown
      mockPort.getDevice.mockReturnValueOnce(throwError(() => new Error('Network')));
      facade.load('dev-001');
      // _pollOk=false + existing device → unknown (honesty invariant)
      expect(facade.derivedState()).toBe('unknown');
      expect(facade.derivedState()).not.toBe('online');
    });
  });

  // -------------------------------------------------------------------------
  // startPolling() — ETag/304 path
  // -------------------------------------------------------------------------

  describe('startPolling() — ETag/304 not-modified', () => {
    it('ETag/304: device snapshot unchanged, derivedState preserved, pollOk=true', fakeAsync(() => {
      // Load a fresh device
      const device = makePairedDevice({ pacienteId: 'pac-test' });
      mockPort.getDevice.mockReturnValue(of(device));
      facade.load('dev-001');

      const snapshotBefore = facade.device();
      expect(facade.derivedState()).toBe('online');

      // Mock 304 Not Modified response
      const notModified: NotModified = { notModified: true };
      mockPort.pollPatientLiveness.mockReturnValue(of(notModified));

      facade.startPolling();
      tick(0); // fire initial timer(0, interval) at t=0

      // Device snapshot MUST be unchanged (304 means no new data)
      expect(facade.device()).toEqual(snapshotBefore);
      // pollOk set to true → derivedState reflects existing device (online)
      expect(facade.derivedState()).toBe('online');

      facade.stopPolling();
    }));

    it('ETag/304: startPolling is idempotent — second call is a no-op', fakeAsync(() => {
      const device = makePairedDevice({ pacienteId: 'pac-test' });
      mockPort.getDevice.mockReturnValue(of(device));
      facade.load('dev-001');

      mockPort.pollPatientLiveness.mockReturnValue(of({ notModified: true } as NotModified));

      facade.startPolling();
      facade.startPolling(); // second call — no-op per SPEC-04 §3

      tick(0);
      // Only one subscription active — only one poll call at t=0
      expect(mockPort.pollPatientLiveness).toHaveBeenCalledTimes(1);

      facade.stopPolling();
    }));
  });

  // -------------------------------------------------------------------------
  // startPolling() — liveness update path
  // -------------------------------------------------------------------------

  describe('startPolling() — fresh liveness response', () => {
    it('updates device snapshot with new serverTime and ultimaSincronizacao', fakeAsync(() => {
      const device = makePairedDevice({ pacienteId: 'pac-test' });
      mockPort.getDevice.mockReturnValue(of(device));
      facade.load('dev-001');

      const newSyncTime = new Date(FIXED_NOW - 5_000).toISOString();
      const newServerTime = new Date(FIXED_NOW + 1_000).toISOString();
      const liveness: LivenessView = {
        deviceId: 'dev-001',
        pacienteId: 'pac-test',
        status: 'online',
        ultimaSincronizacao: newSyncTime,
        versao: '1.2.3',
        stalenessThresholdSeconds: 90,
        serverTime: newServerTime,
      };
      mockPort.pollPatientLiveness.mockReturnValue(of(liveness));

      facade.startPolling();
      tick(0);

      expect(facade.device()?.ultimaSincronizacao).toBe(newSyncTime);
      expect(facade.device()?.serverTime).toBe(newServerTime);
      expect(facade.derivedState()).toBe('online');

      facade.stopPolling();
    }));
  });

  // -------------------------------------------------------------------------
  // startPolling() — poll failure → unknown (honesty invariant)
  // -------------------------------------------------------------------------

  describe('startPolling() — poll failure (honesty invariant)', () => {
    it('poll failure: derivedState transitions to unknown (never online)', fakeAsync(() => {
      const device = makePairedDevice({ pacienteId: 'pac-test' });
      mockPort.getDevice.mockReturnValue(of(device));
      facade.load('dev-001');
      expect(facade.derivedState()).toBe('online');

      // Poll fails (e.g. network timeout)
      mockPort.pollPatientLiveness.mockReturnValue(
        throwError(() => new Error('timeout')),
      );

      facade.startPolling();
      tick(0);

      // INVARIANT: unknown ≠ online when poll fails
      expect(facade.derivedState()).toBe('unknown');
      expect(facade.derivedState()).not.toBe('online');

      facade.stopPolling();
    }));

    it('poll failure does not wipe the device snapshot (device still visible)', fakeAsync(() => {
      const device = makePairedDevice({ pacienteId: 'pac-test' });
      mockPort.getDevice.mockReturnValue(of(device));
      facade.load('dev-001');

      mockPort.pollPatientLiveness.mockReturnValue(
        throwError(() => new Error('network')),
      );

      facade.startPolling();
      tick(0);

      // Device is still present — UI can show stale metadata
      expect(facade.device()).toBeTruthy();
      expect(facade.device()?.deviceId).toBe('dev-001');

      facade.stopPolling();
    }));
  });

  // -------------------------------------------------------------------------
  // connectivity computed read-model (DeviceConnectivityVM)
  // -------------------------------------------------------------------------

  describe('connectivity read-model (DeviceConnectivityVM)', () => {
    it('tone is "muted" when not paired', () => {
      expect(facade.connectivity().tone).toBe('muted');
    });

    it('tone is "ok" when device is online', () => {
      mockPort.getDevice.mockReturnValue(of(makePairedDevice()));
      facade.load('dev-001');
      expect(facade.connectivity().tone).toBe('ok');
      expect(facade.connectivity().derivedState).toBe('online');
    });

    it('paired is true when device pairingState is paired', () => {
      mockPort.getDevice.mockReturnValue(of(makePairedDevice()));
      facade.load('dev-001');
      expect(facade.connectivity().paired).toBe(true);
    });

    it('tone is "er" when device is offline', () => {
      const device = makePairedDevice({ ultimaSincronizacao: null }); // never reported
      mockPort.getDevice.mockReturnValue(of(device));
      facade.load('dev-001');
      expect(facade.connectivity().tone).toBe('er');
    });
  });
});
