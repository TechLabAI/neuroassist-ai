import { TestBed } from '@angular/core/testing';
import { DashShellIntegrationService, DashShellRegistry } from './dash-shell-integration.service';
import { ShellOutletRegistry } from '@neuroassist/shared/ui-components';
import { signal } from '@angular/core';
import type { ActiveEventVm, DeviceBadgeVm } from '../access/dash-access-tokens';

// =============================================================================
// dash-shell-integration.service.spec.ts — FE-DASH-018 unit tests
//
// Verifies:
//   - registerWithShell() is idempotent (guard by _registered flag)
//   - releaseOutletRegion() deregisters 'dash.global-interrupt'
//   - bindActiveEvent() / bindDeviceBadge() do not throw and accept signals
//   - No imports of scope:evnt / scope:edge (structural — enforced by nx boundaries)
// =============================================================================

const mockShellRegistry: DashShellRegistry = {
  registerModule: jest.fn(),
  registerNavConfig: jest.fn(),
};

const mockOutletRegistry = {
  registerGlobalOutlet: jest.fn(),
  unregisterGlobalOutlet: jest.fn(),
  getOutlets: jest.fn().mockReturnValue(new Map()),
};

describe('DashShellIntegrationService', () => {
  let service: DashShellIntegrationService;

  beforeEach(() => {
    jest.clearAllMocks();

    TestBed.configureTestingModule({
      providers: [
        DashShellIntegrationService,
        { provide: ShellOutletRegistry, useValue: mockOutletRegistry },
      ],
    });

    service = TestBed.inject(DashShellIntegrationService);
  });

  // ---------------------------------------------------------------------------
  // registerWithShell
  // ---------------------------------------------------------------------------

  describe('registerWithShell', () => {
    it('should call registerModule and registerNavConfig on the registry', () => {
      service.registerWithShell(mockShellRegistry);

      expect(mockShellRegistry.registerModule).toHaveBeenCalledTimes(1);
      expect(mockShellRegistry.registerNavConfig).toHaveBeenCalledTimes(1);
    });

    it('should register the global-interrupt outlet region', () => {
      service.registerWithShell(mockShellRegistry);

      expect(mockOutletRegistry.registerGlobalOutlet).toHaveBeenCalledTimes(1);
      expect(mockOutletRegistry.registerGlobalOutlet).toHaveBeenCalledWith(
        'dash.global-interrupt',
        expect.objectContaining({ zLayer: 'interrupt' }),
      );
    });

    it('should be idempotent — second call is a no-op (coverage gap G6)', () => {
      service.registerWithShell(mockShellRegistry);
      service.registerWithShell(mockShellRegistry);

      // Both registerModule and registerNavConfig called exactly ONCE (not twice).
      expect(mockShellRegistry.registerModule).toHaveBeenCalledTimes(1);
      expect(mockShellRegistry.registerNavConfig).toHaveBeenCalledTimes(1);
      expect(mockOutletRegistry.registerGlobalOutlet).toHaveBeenCalledTimes(1);
    });

    it('should mark isRegistered() as true after first call', () => {
      expect(service.isRegistered()).toBe(false);

      service.registerWithShell(mockShellRegistry);

      expect(service.isRegistered()).toBe(true);
    });
  });

  // ---------------------------------------------------------------------------
  // releaseOutletRegion
  // ---------------------------------------------------------------------------

  describe('releaseOutletRegion', () => {
    it('should call unregisterGlobalOutlet with the interrupt region key', () => {
      service.registerWithShell(mockShellRegistry);
      service.releaseOutletRegion();

      expect(mockOutletRegistry.unregisterGlobalOutlet).toHaveBeenCalledWith('dash.global-interrupt');
    });

    it('should reset isRegistered() to false after release', () => {
      service.registerWithShell(mockShellRegistry);
      expect(service.isRegistered()).toBe(true);

      service.releaseOutletRegion();
      expect(service.isRegistered()).toBe(false);
    });

    it('should not throw when called without prior registerWithShell', () => {
      expect(() => service.releaseOutletRegion()).not.toThrow();
    });
  });

  // ---------------------------------------------------------------------------
  // bindActiveEvent
  // ---------------------------------------------------------------------------

  describe('bindActiveEvent', () => {
    it('should accept a null signal without throwing', () => {
      const src = signal<ActiveEventVm | null>(null);
      expect(() => service.bindActiveEvent(src)).not.toThrow();
    });

    it('should accept a non-null active event signal without throwing', () => {
      const src = signal<ActiveEventVm | null>({
        eventId: 'ev-1',
        urgencia: 'emergencia',
        patientId: 'p-1',
      });
      expect(() => service.bindActiveEvent(src)).not.toThrow();
    });
  });

  // ---------------------------------------------------------------------------
  // bindDeviceBadge
  // ---------------------------------------------------------------------------

  describe('bindDeviceBadge', () => {
    it('should accept a null signal without throwing', () => {
      const src = signal<DeviceBadgeVm | null>(null);
      expect(() => service.bindDeviceBadge(src)).not.toThrow();
    });

    it('should accept a device badge signal without throwing', () => {
      const src = signal<DeviceBadgeVm | null>({
        status: 'online',
        lastSeenAt: '2026-08-19T12:00:00Z',
        degraded: false,
      });
      expect(() => service.bindDeviceBadge(src)).not.toThrow();
    });
  });

  // ---------------------------------------------------------------------------
  // lifecycle: register → release → re-register
  // ---------------------------------------------------------------------------

  describe('full lifecycle', () => {
    it('should support register → release → re-register cycle', () => {
      service.registerWithShell(mockShellRegistry);
      expect(service.isRegistered()).toBe(true);

      service.releaseOutletRegion();
      expect(service.isRegistered()).toBe(false);

      // After release, a fresh registerWithShell should work again.
      service.registerWithShell(mockShellRegistry);
      expect(service.isRegistered()).toBe(true);

      // registerModule called twice total (once per full register cycle).
      expect(mockShellRegistry.registerModule).toHaveBeenCalledTimes(2);
      expect(mockOutletRegistry.registerGlobalOutlet).toHaveBeenCalledTimes(2);
    });
  });
});
