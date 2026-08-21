import { TestBed } from '@angular/core/testing';
import { of, EMPTY } from 'rxjs';
import { EvntShellIntegrationService } from './evnt-shell-integration.service';
import { EventDeliveryFacade } from '../evnt.facade';
import { ShellOutletRegistry } from '@neuroassist/shared/ui-components';
import { EVNT_CLIENT_PORT } from '../api/evnt-client.port';

// =============================================================================
// EvntShellIntegrationService spec — INT-FE-EVNT-001 / P6-F
// Verifies that registerAlarmOutlet() calls ShellOutletRegistry.registerGlobalOutlet()
// with the correct parameters (component loader, visible signal, zLayer).
// =============================================================================

describe('EvntShellIntegrationService', () => {
  let service: EvntShellIntegrationService;
  let registry: { registerGlobalOutlet: jest.Mock; unregisterGlobalOutlet: jest.Mock };

  beforeEach(() => {
    const mockRegistry = {
      registerGlobalOutlet: jest.fn(),
      unregisterGlobalOutlet: jest.fn(),
      getOutlets: jest.fn(() => new Map()),
    };

    const mockPort = {
      openStream: jest.fn(() => EMPTY),
      listActive: jest.fn(() => of({
        serverTime: '2026-08-16T10:00:00Z',
        activeEvent: null,
        events: [],
      })),
      get: jest.fn(() => EMPTY),
      confirm: jest.fn(() => EMPTY),
      ack: jest.fn(() => EMPTY),
      query: jest.fn(() => EMPTY),
    };

    TestBed.configureTestingModule({
      providers: [
        EvntShellIntegrationService,
        EventDeliveryFacade,
        { provide: ShellOutletRegistry, useValue: mockRegistry },
        { provide: EVNT_CLIENT_PORT, useValue: mockPort },
      ],
    });

    service = TestBed.inject(EvntShellIntegrationService);
    registry = mockRegistry as typeof registry;
  });

  it('calls registry.registerGlobalOutlet with key "alarm", a component loader, a visible signal and zLayer "interrupt"', () => {
    service.registerAlarmOutlet();

    expect(registry.registerGlobalOutlet).toHaveBeenCalledTimes(1);

    const [key, registration] = registry.registerGlobalOutlet.mock.calls[0] as [
      string,
      { component: () => Promise<unknown>; visible: unknown; zLayer: string },
    ];

    expect(key).toBe('alarm');
    expect(typeof registration.component).toBe('function');
    expect(registration.zLayer).toBe('interrupt');
    // visible is a computed Signal<AlarmVm> — verify it's signal-shaped
    expect(typeof registration.visible).toBe('function');
  });

  it('does not register twice when called multiple times', () => {
    service.registerAlarmOutlet();
    service.registerAlarmOutlet();

    expect(registry.registerGlobalOutlet).toHaveBeenCalledTimes(1);
  });

  it('isRegistered() reflects registration state correctly', () => {
    expect(service.isRegistered()).toBe(false);

    service.registerAlarmOutlet();
    expect(service.isRegistered()).toBe(true);

    service.unregisterAlarmOutlet();
    expect(service.isRegistered()).toBe(false);
  });
});
