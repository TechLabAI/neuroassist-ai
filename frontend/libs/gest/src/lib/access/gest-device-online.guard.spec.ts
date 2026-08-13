import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { EMPTY } from 'rxjs';
import { gestDeviceOnlineGuard } from './gest-device-online.guard';
import { GestureTestFacade } from '../gest.facade';
import { EDGE_DETECTION_PORT } from '../api/edge-detection.port';

// ---------------------------------------------------------------------------
// gestDeviceOnlineGuard — unit tests (GEST-FE-017)
// Non-blocking guard: navigation always returns true; its only job is to seed
// facade.deviceOnline from navigator.onLine before the live-test mounts.
// ---------------------------------------------------------------------------

const mockRoute = {} as ActivatedRouteSnapshot;
const mockState = {} as RouterStateSnapshot;

describe('gestDeviceOnlineGuard', () => {
  let mockFacade: { setDeviceOnline: jest.Mock };

  beforeEach(() => {
    mockFacade = { setDeviceOnline: jest.fn() };

    TestBed.configureTestingModule({
      providers: [
        { provide: GestureTestFacade, useValue: mockFacade },
        {
          provide: EDGE_DETECTION_PORT,
          useValue: {
            openStream: jest.fn().mockReturnValue(EMPTY),
            mockStream: jest.fn().mockReturnValue(EMPTY),
            close: jest.fn(),
          },
        },
      ],
    });
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  describe('when navigator.onLine is true', () => {
    beforeEach(() => {
      Object.defineProperty(navigator, 'onLine', {
        value: true,
        configurable: true,
      });
    });

    it('calls facade.setDeviceOnline(true, null)', () => {
      TestBed.runInInjectionContext(() => gestDeviceOnlineGuard(mockRoute, mockState));
      expect(mockFacade.setDeviceOnline).toHaveBeenCalledWith(true, null);
    });

    it('returns true (navigation proceeds)', () => {
      const result = TestBed.runInInjectionContext(() =>
        gestDeviceOnlineGuard(mockRoute, mockState),
      );
      expect(result).toBe(true);
    });
  });

  describe('when navigator.onLine is false', () => {
    beforeEach(() => {
      Object.defineProperty(navigator, 'onLine', {
        value: false,
        configurable: true,
      });
    });

    it('calls facade.setDeviceOnline(false, device-offline)', () => {
      TestBed.runInInjectionContext(() => gestDeviceOnlineGuard(mockRoute, mockState));
      expect(mockFacade.setDeviceOnline).toHaveBeenCalledWith(false, 'device-offline');
    });

    it('still returns true — navigation always proceeds (non-blocking guard)', () => {
      const result = TestBed.runInInjectionContext(() =>
        gestDeviceOnlineGuard(mockRoute, mockState),
      );
      expect(result).toBe(true);
    });

    it('seeds facade with honest-degraded mode before live-test mounts', () => {
      TestBed.runInInjectionContext(() => gestDeviceOnlineGuard(mockRoute, mockState));
      // Verify the guard seeds the offline state — the facade will start in
      // honest-degraded mode and use mockStream instead of openStream
      expect(mockFacade.setDeviceOnline).toHaveBeenCalledTimes(1);
      const [online, reason] = mockFacade.setDeviceOnline.mock.calls[0];
      expect(online).toBe(false);
      expect(reason).toBe('device-offline');
    });
  });
});
