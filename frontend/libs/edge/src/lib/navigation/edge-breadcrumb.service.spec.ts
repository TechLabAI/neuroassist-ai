// =============================================================================
// edge-breadcrumb.service.spec.ts — FE-EDGE-016 DoD
//
// Covers (SPEC-01 §2, SPEC-06 §3):
//   1. /edge/device        → BreadcrumbPort.push([{label:'Dispositivo'}])
//   2. /edge/device/:id    → BreadcrumbPort.push([{label:'Dispositivo'}])
//   3. /edge/pairing       → BreadcrumbPort.push([{label:'Dispositivo'},{label:'Parear'}])
//   4. /edge/device/history → BreadcrumbPort.push([{label:'Dispositivo'},{label:'Histórico'}])
//   5. /home  (composed context)  → BreadcrumbPort.push NOT called
//   6. /onboarding (composed context) → BreadcrumbPort.push NOT called
//   7. /pac/patients (non-EDGE) → BreadcrumbPort.push NOT called
// =============================================================================

import { TestBed } from '@angular/core/testing';
import { Router, NavigationEnd } from '@angular/router';
import { Subject } from 'rxjs';
import { EdgeBreadcrumbService } from './edge-breadcrumb.service';
import { EDGE_BREADCRUMB_PORT, BreadcrumbPort } from './breadcrumb.port';

// ---------------------------------------------------------------------------
// Test infrastructure
// ---------------------------------------------------------------------------

interface MockRouter {
  events: Subject<unknown>;
  url: string;
}

describe('EdgeBreadcrumbService — FE-EDGE-016', () => {
  let routerEvents$: Subject<unknown>;
  let mockRouter: MockRouter;
  let breadcrumbPortSpy: jest.Mocked<BreadcrumbPort>;

  /** Simulate a completed navigation to the given URL. */
  function navigate(url: string): void {
    mockRouter.url = url;
    routerEvents$.next(new NavigationEnd(1, url, url));
  }

  beforeEach(() => {
    routerEvents$ = new Subject<unknown>();
    mockRouter = { events: routerEvents$, url: '/' };
    breadcrumbPortSpy = { push: jest.fn() };

    TestBed.configureTestingModule({
      providers: [
        { provide: Router, useValue: mockRouter },
        { provide: EDGE_BREADCRUMB_PORT, useValue: breadcrumbPortSpy },
      ],
    });
  });

  afterEach(() => {
    routerEvents$.complete();
  });

  // -------------------------------------------------------------------------
  // EDGE routes — breadcrumb must be pushed
  // -------------------------------------------------------------------------

  describe('EDGE route: /edge/device', () => {
    it('pushes single-segment trail [{label: Dispositivo}]', () => {
      TestBed.inject(EdgeBreadcrumbService);
      navigate('/edge/device');

      expect(breadcrumbPortSpy.push).toHaveBeenCalledTimes(1);
      expect(breadcrumbPortSpy.push).toHaveBeenCalledWith([
        { label: 'Dispositivo', url: '/edge/device' },
      ]);
    });
  });

  describe('EDGE route: /edge/device/:deviceId (device detail)', () => {
    it('pushes single-segment trail [{label: Dispositivo}]', () => {
      TestBed.inject(EdgeBreadcrumbService);
      navigate('/edge/device/abc-123-def');

      expect(breadcrumbPortSpy.push).toHaveBeenCalledTimes(1);
      expect(breadcrumbPortSpy.push).toHaveBeenCalledWith([
        { label: 'Dispositivo', url: '/edge/device' },
      ]);
    });
  });

  describe('EDGE route: /edge/pairing', () => {
    it('pushes two-segment trail [Dispositivo › Parear]', () => {
      TestBed.inject(EdgeBreadcrumbService);
      navigate('/edge/pairing');

      expect(breadcrumbPortSpy.push).toHaveBeenCalledTimes(1);
      expect(breadcrumbPortSpy.push).toHaveBeenCalledWith([
        { label: 'Dispositivo', url: '/edge/device' },
        { label: 'Parear', url: '/edge/pairing' },
      ]);
    });
  });

  describe('EDGE route: /edge/device/history (reserved path)', () => {
    it('pushes two-segment trail [Dispositivo › Histórico]', () => {
      TestBed.inject(EdgeBreadcrumbService);
      navigate('/edge/device/history');

      expect(breadcrumbPortSpy.push).toHaveBeenCalledTimes(1);
      expect(breadcrumbPortSpy.push).toHaveBeenCalledWith([
        { label: 'Dispositivo', url: '/edge/device' },
        { label: 'Histórico', url: '/edge/device/history' },
      ]);
    });
  });

  describe('EDGE root: /edge (redirect target)', () => {
    it('pushes single-segment trail [{label: Dispositivo}]', () => {
      TestBed.inject(EdgeBreadcrumbService);
      navigate('/edge');

      expect(breadcrumbPortSpy.push).toHaveBeenCalledTimes(1);
      expect(breadcrumbPortSpy.push).toHaveBeenCalledWith([
        { label: 'Dispositivo', url: '/edge/device' },
      ]);
    });
  });

  // -------------------------------------------------------------------------
  // Composed context — breadcrumb must NOT be pushed (SPEC-06 §3)
  // -------------------------------------------------------------------------

  describe('composed context — non-EDGE URL', () => {
    it('does NOT push when URL is /home (topbar chip composition)', () => {
      TestBed.inject(EdgeBreadcrumbService);
      navigate('/home');

      expect(breadcrumbPortSpy.push).not.toHaveBeenCalled();
    });

    it('does NOT push when URL is /onboarding (onboarding step-3 composition)', () => {
      TestBed.inject(EdgeBreadcrumbService);
      navigate('/onboarding');

      expect(breadcrumbPortSpy.push).not.toHaveBeenCalled();
    });

    it('does NOT push when URL is /pac/patients (different module)', () => {
      TestBed.inject(EdgeBreadcrumbService);
      navigate('/pac/patients');

      expect(breadcrumbPortSpy.push).not.toHaveBeenCalled();
    });

    it('does NOT push when URL is / (root — shell home)', () => {
      TestBed.inject(EdgeBreadcrumbService);
      navigate('/');

      expect(breadcrumbPortSpy.push).not.toHaveBeenCalled();
    });
  });

  // -------------------------------------------------------------------------
  // Multiple navigations in one session
  // -------------------------------------------------------------------------

  describe('sequential navigations', () => {
    it('pushes the correct trail for each EDGE navigation in sequence', () => {
      TestBed.inject(EdgeBreadcrumbService);

      navigate('/edge/device');
      navigate('/edge/pairing');
      navigate('/edge/device');

      expect(breadcrumbPortSpy.push).toHaveBeenCalledTimes(3);
      expect(breadcrumbPortSpy.push).toHaveBeenNthCalledWith(1, [
        { label: 'Dispositivo', url: '/edge/device' },
      ]);
      expect(breadcrumbPortSpy.push).toHaveBeenNthCalledWith(2, [
        { label: 'Dispositivo', url: '/edge/device' },
        { label: 'Parear', url: '/edge/pairing' },
      ]);
      expect(breadcrumbPortSpy.push).toHaveBeenNthCalledWith(3, [
        { label: 'Dispositivo', url: '/edge/device' },
      ]);
    });

    it('does NOT push for a non-EDGE navigation between EDGE navigations', () => {
      TestBed.inject(EdgeBreadcrumbService);

      navigate('/edge/device');
      navigate('/home');
      navigate('/edge/pairing');

      // Only the 2 EDGE navigations should trigger push
      expect(breadcrumbPortSpy.push).toHaveBeenCalledTimes(2);
    });
  });

  // -------------------------------------------------------------------------
  // Query string handling
  // -------------------------------------------------------------------------

  describe('URL with query string', () => {
    it('strips query string before matching (/edge/device?tab=info → Dispositivo)', () => {
      TestBed.inject(EdgeBreadcrumbService);
      navigate('/edge/device?tab=info');

      expect(breadcrumbPortSpy.push).toHaveBeenCalledWith([
        { label: 'Dispositivo', url: '/edge/device' },
      ]);
    });
  });

  // -------------------------------------------------------------------------
  // buildTrail() — unit coverage for the pure mapping function
  // -------------------------------------------------------------------------

  describe('buildTrail() — pure URL → trail mapping', () => {
    let service: EdgeBreadcrumbService;

    beforeEach(() => {
      service = TestBed.inject(EdgeBreadcrumbService);
    });

    it('/edge/device → [{label:Dispositivo}]', () => {
      expect(service.buildTrail('/edge/device')).toEqual([
        { label: 'Dispositivo', url: '/edge/device' },
      ]);
    });

    it('/edge/pairing → [{label:Dispositivo}, {label:Parear}]', () => {
      expect(service.buildTrail('/edge/pairing')).toEqual([
        { label: 'Dispositivo', url: '/edge/device' },
        { label: 'Parear', url: '/edge/pairing' },
      ]);
    });

    it('/edge/device/history → [{label:Dispositivo}, {label:Histórico}]', () => {
      expect(service.buildTrail('/edge/device/history')).toEqual([
        { label: 'Dispositivo', url: '/edge/device' },
        { label: 'Histórico', url: '/edge/device/history' },
      ]);
    });
  });
});
