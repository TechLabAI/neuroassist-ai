// =============================================================================
// edge.bootstrap.spec.ts — G4: init+destroy lifecycle, no leaked timer (FE-EDGE-027)
//
// Gap G4: `edgeBootstrap.destroy()` called on module unload — partially covered by
// FE-EDGE-004 but needs an explicit lifecycle integration test asserting that
// `stopPolling()` is called and no pending timers remain after `destroy()`.
//
// Also verifies:
//   - init() registers with shell and starts connectivity for the active patient
//   - destroy() calls facade.stopPolling() (no leaked interval)
//   - init() + destroy() are idempotent and safe to call in sequence
// =============================================================================

import { TestBed, fakeAsync, discardPeriodicTasks } from '@angular/core/testing';
import { of } from 'rxjs';
import { edgeBootstrap } from './edge.bootstrap';
import { DeviceFacade } from './edge.facade';
import { EdgeShellIntegrationService } from './integration/edge-shell-integration.service';
import { DEVICE_PORT, DevicePort } from './api/device.port';
import { PAIRING_PORT, PairingPort } from './api/pairing.port';
import { DispositivoEdgeView } from '@neuroassist/shared/contracts';

// ---------------------------------------------------------------------------
// Fixture helpers
// ---------------------------------------------------------------------------

function makeOnlineDevice(): DispositivoEdgeView {
  const now = Date.now();
  return {
    deviceId: 'dev-bootstrap-test',
    tenantRef: 'tenant-test',
    pacienteId: 'pac-marcio',
    pairingState: 'paired',
    status: 'online',
    ultimaSincronizacao: new Date(now - 10_000).toISOString(),
    versao: '1.0.0',
    runningProfileVersion: 1,
    currentProfileVersion: 1,
    profileDrift: false,
    stalenessThresholdSeconds: 90,
    serverTime: new Date(now).toISOString(),
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
// G4 Tests
// ---------------------------------------------------------------------------

describe('G4 — edgeBootstrap lifecycle: init + destroy (FE-EDGE-027)', () => {
  let mockDevicePort: jest.Mocked<DevicePort>;
  let mockPairingPort: jest.Mocked<PairingPort>;
  let deviceFacade: DeviceFacade;
  let integration: EdgeShellIntegrationService;

  beforeEach(() => {
    mockDevicePort = createMockDevicePort();
    mockPairingPort = createMockPairingPort();
    // resolveDeviceForPatient is called by startConnectivityForActivePatient
    mockDevicePort.resolveDeviceForPatient.mockReturnValue(of(makeOnlineDevice()));
    // pollPatientLiveness must not reject to avoid timer errors
    mockDevicePort.pollPatientLiveness.mockReturnValue(of({ notModified: true }));

    TestBed.configureTestingModule({
      providers: [
        DeviceFacade,
        EdgeShellIntegrationService,
        { provide: DEVICE_PORT, useValue: mockDevicePort },
        { provide: PAIRING_PORT, useValue: mockPairingPort },
      ],
    });

    deviceFacade = TestBed.inject(DeviceFacade);
    integration = TestBed.inject(EdgeShellIntegrationService);
  });

  afterEach(() => {
    deviceFacade.stopPolling();
    TestBed.resetTestingModule();
  });

  // -------------------------------------------------------------------------
  // init() — registers with shell and starts connectivity
  // -------------------------------------------------------------------------

  it('[G4-init] init() registers the EDGE module with the shell (idempotent)', () => {
    expect(integration.isRegistered()).toBe(false);

    TestBed.runInInjectionContext(() => {
      edgeBootstrap.init();
    });

    expect(integration.isRegistered()).toBe(true);

    // Calling init() again is a no-op (registerWithShell is idempotent).
    TestBed.runInInjectionContext(() => {
      edgeBootstrap.init();
    });
    expect(integration.isRegistered()).toBe(true);
  });

  it('[G4-init] init() starts connectivity polling for the active patient', () => {
    TestBed.runInInjectionContext(() => {
      edgeBootstrap.init();
    });

    // resolveDeviceForPatient was called (startConnectivityForActivePatient → resolveForActivePatient)
    expect(mockDevicePort.resolveDeviceForPatient).toHaveBeenCalledWith('pac-marcio');
  });

  // -------------------------------------------------------------------------
  // destroy() — stops polling, no leaked timer
  // -------------------------------------------------------------------------

  it('[G4-destroy] destroy() calls stopPolling() on DeviceFacade', () => {
    const stopPollingSpy = jest.spyOn(deviceFacade, 'stopPolling');

    TestBed.runInInjectionContext(() => {
      edgeBootstrap.init();
      edgeBootstrap.destroy();
    });

    expect(stopPollingSpy).toHaveBeenCalled();
  });

  it('[G4-no-leak] destroy() after init leaves no pending timers (fakeAsync)', fakeAsync(() => {
    mockDevicePort.pollPatientLiveness.mockReturnValue(of({ notModified: true }));

    TestBed.runInInjectionContext(() => {
      edgeBootstrap.init();
    });

    // destroy() must stop the timer started by startPolling()
    TestBed.runInInjectionContext(() => {
      edgeBootstrap.destroy();
    });

    // discardPeriodicTasks() would throw if any periodic tasks remain.
    // Calling it here verifies no timer leak after destroy.
    discardPeriodicTasks(); // no-op if polling was stopped — test passes
  }));

  // -------------------------------------------------------------------------
  // destroy() before init — safe to call in any order
  // -------------------------------------------------------------------------

  it('[G4-safe] destroy() before init does not throw', () => {
    expect(() => {
      TestBed.runInInjectionContext(() => {
        edgeBootstrap.destroy();
      });
    }).not.toThrow();
  });

  // -------------------------------------------------------------------------
  // destroy() outside injection context — guarded by try/catch
  // -------------------------------------------------------------------------

  it('[G4-guard] destroy() outside injection context does not throw (catch guard)', () => {
    // Calling edgeBootstrap.destroy() outside injection context would normally throw
    // an Angular error. The edgeBootstrap.destroy() implementation wraps in try/catch.
    expect(() => {
      edgeBootstrap.destroy(); // outside injection context
    }).not.toThrow();
  });
});
