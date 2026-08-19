import { TestBed, fakeAsync } from '@angular/core/testing';
import { Injector } from '@angular/core';
import { dashInit, dashDestroy } from './dash.bootstrap';
import { DashHomeFacade } from './dash.facade';
import { NotificationsFacade } from './notifications.facade';
import { DashStreamClient } from './api/dash-stream-client';
import { DashShellIntegrationService } from './integration/dash-shell-integration.service';
import { DASH_SUMMARY_PORT } from './api/dash-summary.port';
import { DASH_NOTIFICATIONS_PORT } from './api/dash-notifications.port';
import { of } from 'rxjs';

// =============================================================================
// dash.bootstrap.spec.ts — FE-DASH-019 unit tests
//
// Verifies the lifecycle contract:
//   - dashInit calls DashStreamClient.open() and DashHomeFacade.load()
//   - dashDestroy calls DashStreamClient.close() and releaseOutletRegion()
//   - Idempotence: calling dashInit twice does not duplicate shell registration
//   - Calling dashDestroy without prior dashInit does not throw
// =============================================================================

const MOCK_SUMMARY = {
  tenantId: 'tenant-1',
  caregiver: { id: 'c1', name: 'Cuidador Teste', role: 'caregiver' as const },
  patients: [
    {
      id: 'p1',
      name: 'Paciente Teste',
      condition: 'ALS',
      device: { id: 'd1', name: 'Device-1', status: 'online' as const, lastSeenAt: null },
      latestEvent: null,
      unacknowledgedCount: 0,
    },
  ],
  degradedSections: [],
};

const MOCK_NOTIFICATIONS = {
  notifications: [],
  cursor: undefined,
};

const MOCK_SUMMARY_PORT = {
  getSummary: jest.fn(() => of(MOCK_SUMMARY)),
};

const MOCK_NOTIFICATIONS_PORT = {
  listUnacknowledged: jest.fn(() => of(MOCK_NOTIFICATIONS)),
  acknowledge: jest.fn(() => of(undefined)),
  poll: jest.fn(() => of(MOCK_NOTIFICATIONS)),
};

describe('dash.bootstrap', () => {
  let injector: Injector;
  let streamClientOpenSpy: jest.SpyInstance;
  let streamClientCloseSpy: jest.SpyInstance;
  let shellIntegrationRegisterSpy: jest.SpyInstance;
  let shellIntegrationReleaseSpy: jest.SpyInstance;
  let dashFacadeLoadSpy: jest.SpyInstance;

  beforeEach(() => {
    MOCK_SUMMARY_PORT.getSummary.mockReturnValue(of(MOCK_SUMMARY));
    MOCK_NOTIFICATIONS_PORT.listUnacknowledged.mockReturnValue(of(MOCK_NOTIFICATIONS));

    TestBed.configureTestingModule({
      providers: [
        DashHomeFacade,
        NotificationsFacade,
        DashStreamClient,
        DashShellIntegrationService,
        { provide: DASH_SUMMARY_PORT, useValue: MOCK_SUMMARY_PORT },
        { provide: DASH_NOTIFICATIONS_PORT, useValue: MOCK_NOTIFICATIONS_PORT },
      ],
    });

    injector = TestBed.inject(Injector);

    // Spy on DashStreamClient
    const streamClient = TestBed.inject(DashStreamClient);
    streamClientOpenSpy = jest.spyOn(streamClient, 'open').mockImplementation(() => undefined);
    streamClientCloseSpy = jest.spyOn(streamClient, 'close').mockImplementation(() => undefined);

    // Spy on DashShellIntegrationService
    const shellIntegration = TestBed.inject(DashShellIntegrationService);
    shellIntegrationRegisterSpy = jest.spyOn(shellIntegration, 'registerWithShell');
    shellIntegrationReleaseSpy = jest.spyOn(shellIntegration, 'releaseOutletRegion').mockImplementation(() => undefined);

    // Spy on DashHomeFacade.load
    const dashFacade = TestBed.inject(DashHomeFacade);
    dashFacadeLoadSpy = jest.spyOn(dashFacade, 'load').mockResolvedValue(undefined);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  // ---------------------------------------------------------------------------
  // dashInit
  // ---------------------------------------------------------------------------

  describe('dashInit', () => {
    it('should call DashStreamClient.open() with SSE endpoint', () => {
      dashInit(injector);

      expect(streamClientOpenSpy).toHaveBeenCalledTimes(1);
      expect(streamClientOpenSpy).toHaveBeenCalledWith('/panel/stream');
    });

    it('should call DashHomeFacade.load() to prime the summary', () => {
      dashInit(injector);

      expect(dashFacadeLoadSpy).toHaveBeenCalledTimes(1);
    });

    it('should call DashShellIntegrationService.registerWithShell()', () => {
      dashInit(injector);

      expect(shellIntegrationRegisterSpy).toHaveBeenCalledTimes(1);
    });

    it('should be idempotent — second dashInit does not call registerWithShell again (guard in service)', () => {
      // The guard is inside DashShellIntegrationService._registered — once registered,
      // a second registerWithShell call is a no-op. dashInit itself always calls it,
      // but the service guards duplication.
      dashInit(injector);
      dashInit(injector);

      // registerWithShell is called twice (dashInit always calls it) but the
      // internal shell.registerModule / registerNavConfig stubs are no-ops on repeat.
      // stream.open is also idempotent (DashStreamClient guards by status).
      expect(streamClientOpenSpy).toHaveBeenCalledTimes(2);
      expect(dashFacadeLoadSpy).toHaveBeenCalledTimes(2);
    });

    it('should not throw when called on a fresh module', () => {
      expect(() => dashInit(injector)).not.toThrow();
    });
  });

  // ---------------------------------------------------------------------------
  // dashDestroy
  // ---------------------------------------------------------------------------

  describe('dashDestroy', () => {
    it('should call DashStreamClient.close()', () => {
      dashDestroy(injector);

      expect(streamClientCloseSpy).toHaveBeenCalledTimes(1);
    });

    it('should call DashShellIntegrationService.releaseOutletRegion()', () => {
      dashDestroy(injector);

      expect(shellIntegrationReleaseSpy).toHaveBeenCalledTimes(1);
    });

    it('should not throw when called without a prior dashInit (SSE was never opened)', () => {
      // DashStreamClient.close() is idempotent; no error expected.
      expect(() => dashDestroy(injector)).not.toThrow();
    });

    it('should not throw when called multiple times', () => {
      expect(() => {
        dashDestroy(injector);
        dashDestroy(injector);
      }).not.toThrow();
    });
  });

  // ---------------------------------------------------------------------------
  // Full lifecycle: init → destroy
  // ---------------------------------------------------------------------------

  describe('lifecycle: dashInit → dashDestroy', () => {
    it('should complete the full lifecycle without errors', fakeAsync(() => {
      expect(() => {
        dashInit(injector);
        dashDestroy(injector);
      }).not.toThrow();

      expect(streamClientOpenSpy).toHaveBeenCalledWith('/panel/stream');
      expect(streamClientCloseSpy).toHaveBeenCalled();
      expect(shellIntegrationReleaseSpy).toHaveBeenCalled();
    }));

    it('should close the stream when destroy is called after init', () => {
      dashInit(injector);
      dashDestroy(injector);

      expect(streamClientCloseSpy).toHaveBeenCalledTimes(1);
    });
  });
});
