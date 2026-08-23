// =============================================================================
// device.contract.spec.ts — CTR-09 (FE-E2E-005 / P3)
//
// Verifica que DeviceMockAdapter e DeviceHttpAdapter satisfazem
// DevicePort com o mesmo shape de dados.
//
// Base path: /api/v1/edge (routed via GWY)
// =============================================================================

import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  provideHttpClientTesting,
  HttpTestingController,
} from '@angular/common/http/testing';
import { DevicePort } from './device.port';
import { DeviceHttpAdapter } from './device-http.adapter';
import { DeviceMockAdapter } from './device-mock.adapter';
import type { DispositivoEdgeView, LivenessView } from '@neuroassist/shared/contracts';

const STUB_DEVICE: DispositivoEdgeView = {
  deviceId: 'edge-test-001',
  tenantRef: 'tenant-familia-amaral',
  pacienteId: 'pac-marcio',
  pairingState: 'paired',
  status: 'online',
  ultimaSincronizacao: '2026-08-22T10:00:00Z',
  versao: '1.4.2',
  runningProfileVersion: 3,
  currentProfileVersion: 3,
  profileDrift: false,
  stalenessThresholdSeconds: 90,
  serverTime: '2026-08-22T10:00:01Z',
};

const STUB_LIVENESS: LivenessView = {
  deviceId: 'edge-test-001',
  pacienteId: 'pac-marcio',
  status: 'online',
  ultimaSincronizacao: '2026-08-22T10:00:00Z',
  versao: '1.4.2',
  stalenessThresholdSeconds: 90,
  serverTime: '2026-08-22T10:00:01Z',
};

describe.each([
  ['mock' as const, () => new DeviceMockAdapter()],
  ['http' as const, () => TestBed.inject(DeviceHttpAdapter)],
])('%s satisfaz DevicePort', (label, adapterFactory) => {
  let adapter: DevicePort;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        DeviceHttpAdapter,
        DeviceMockAdapter,
      ],
    });
    controller = TestBed.inject(HttpTestingController);
    adapter = adapterFactory();
  });

  afterEach(() => {
    controller.verify();
    TestBed.resetTestingModule();
  });

  // ---------------------------------------------------------------------------
  // getDevice()
  // ---------------------------------------------------------------------------

  it('getDevice() retorna DispositivoEdgeView com shape correto', fakeAsync(() => {
    let result: DispositivoEdgeView | undefined;
    let error: unknown;

    adapter.getDevice('edge-marcio-01').subscribe({
      next: r => { result = r; },
      error: e => { error = e; },
    });

    if (label === 'http') {
      const req = controller.expectOne('/api/v1/edge/devices/edge-marcio-01');
      expect(req.request.method).toBe('GET');
      req.flush(STUB_DEVICE);
    } else {
      tick(1000);
    }

    expect(error).toBeUndefined();
    expect(result).toMatchObject({
      deviceId: expect.any(String),
      tenantRef: expect.any(String),
      pairingState: expect.stringMatching(/^(registered|paired|unpaired|retired)$/),
      status: expect.stringMatching(/^(online|offline|n\/a)$/),
      profileDrift: expect.any(Boolean),
      stalenessThresholdSeconds: expect.any(Number),
      serverTime: expect.any(String),
    });
  }));

  // ---------------------------------------------------------------------------
  // resolveDeviceForPatient()
  // ---------------------------------------------------------------------------

  it('resolveDeviceForPatient() retorna DispositivoEdgeView', fakeAsync(() => {
    let result: DispositivoEdgeView | undefined;
    let error: unknown;

    adapter.resolveDeviceForPatient('pac-marcio').subscribe({
      next: r => { result = r; },
      error: e => { error = e; },
    });

    if (label === 'http') {
      const req = controller.expectOne('/api/v1/edge/patients/pac-marcio/device');
      expect(req.request.method).toBe('GET');
      req.flush(STUB_DEVICE);
    } else {
      tick(1000);
    }

    expect(error).toBeUndefined();
    expect(result).toMatchObject({
      deviceId: expect.any(String),
      pairingState: expect.any(String),
      status: expect.any(String),
    });
  }));

  // ---------------------------------------------------------------------------
  // pollPatientLiveness()
  // ---------------------------------------------------------------------------

  it('pollPatientLiveness() retorna LivenessView ou NotModified', fakeAsync(() => {
    let result: LivenessView | { notModified: boolean } | undefined;
    let error: unknown;

    adapter.pollPatientLiveness('pac-marcio').subscribe({
      next: r => { result = r; },
      error: e => { error = e; },
    });

    if (label === 'http') {
      const req = controller.expectOne('/api/v1/edge/patients/pac-marcio/liveness');
      expect(req.request.method).toBe('GET');
      req.flush(STUB_LIVENESS);
    } else {
      tick(1000);
    }

    expect(error).toBeUndefined();
    expect(result).toBeDefined();

    // Pode ser LivenessView ou NotModified
    const isLiveness = result && 'deviceId' in result;
    const isNotModified = result && 'notModified' in result;
    expect(isLiveness || isNotModified).toBe(true);

    if (isLiveness) {
      expect(result).toMatchObject({
        deviceId: expect.any(String),
        status: expect.any(String),
        stalenessThresholdSeconds: expect.any(Number),
        serverTime: expect.any(String),
      });
    }
  }));

  // ---------------------------------------------------------------------------
  // unpair()
  // ---------------------------------------------------------------------------

  it('unpair() retorna void (Observable<void>)', fakeAsync(() => {
    let completed = false;
    let error: unknown;

    adapter.unpair('edge-marcio-01', { reason: 'test' }).subscribe({
      next: () => { completed = true; },
      complete: () => { completed = true; },
      error: e => { error = e; },
    });

    if (label === 'http') {
      const req = controller.expectOne('/api/v1/edge/devices/edge-marcio-01/pairings/current');
      expect(req.request.method).toBe('DELETE');
      req.flush(null, { status: 204, statusText: 'No Content' });
    } else {
      tick(1000);
    }

    expect(error).toBeUndefined();
    expect(completed).toBe(true);
  }));
});
