// =============================================================================
// pairing.contract.spec.ts — CTR-10 (FE-E2E-005 / P3)
//
// Verifica que PairingMockAdapter e PairingHttpAdapter satisfazem
// PairingPort com o mesmo shape de dados.
//
// Base path: /api/v1/edge (routed via GWY)
// =============================================================================

import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  provideHttpClientTesting,
  HttpTestingController,
} from '@angular/common/http/testing';
import { PairingPort } from './pairing.port';
import { PairingHttpAdapter } from './pairing-http.adapter';
import { PairingMockAdapter } from './pairing-mock.adapter';
import type { RegisterDeviceResult, DispositivoEdgeView } from '@neuroassist/shared/contracts';

const STUB_REGISTER_RESULT: RegisterDeviceResult = {
  deviceId: 'edge-new-001',
  pairingState: 'registered',
  label: 'Dispositivo Novo',
  enrollmentToken: 'TOKEN-ABCD-1234',
  enrollmentExpiresAt: '2026-08-22T10:30:00Z',
  createdAt: '2026-08-22T10:00:00Z',
};

const STUB_DEVICE_PAIRED: DispositivoEdgeView = {
  deviceId: 'edge-new-001',
  tenantRef: 'tenant-familia-amaral',
  pacienteId: 'pac-marcio',
  pairingState: 'paired',
  status: 'online',
  ultimaSincronizacao: '2026-08-22T10:05:00Z',
  versao: '1.4.2',
  runningProfileVersion: 3,
  currentProfileVersion: 3,
  profileDrift: false,
  stalenessThresholdSeconds: 90,
  serverTime: '2026-08-22T10:05:01Z',
};

describe.each([
  ['mock' as const, () => new PairingMockAdapter()],
  ['http' as const, () => TestBed.inject(PairingHttpAdapter)],
])('%s satisfaz PairingPort', (label, adapterFactory) => {
  let adapter: PairingPort;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        PairingHttpAdapter,
        PairingMockAdapter,
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
  // register()
  // ---------------------------------------------------------------------------

  it('register() retorna RegisterDeviceResult com token de enrollment', fakeAsync(() => {
    let result: RegisterDeviceResult | undefined;
    let error: unknown;

    adapter.register({ label: 'Edge Teste', pacienteHint: 'pac-marcio' }).subscribe({
      next: r => { result = r; },
      error: e => { error = e; },
    });

    if (label === 'http') {
      const req = controller.expectOne('/api/v1/edge/devices');
      expect(req.request.method).toBe('POST');
      req.flush(STUB_REGISTER_RESULT);
    } else {
      tick(1000);
    }

    expect(error).toBeUndefined();
    expect(result).toMatchObject({
      deviceId: expect.any(String),
      pairingState: expect.any(String),
      label: expect.any(String),
      enrollmentToken: expect.any(String),  // one-time claim code
      enrollmentExpiresAt: expect.any(String),
      createdAt: expect.any(String),
    });
  }));

  // ---------------------------------------------------------------------------
  // pairToPatient()
  // ---------------------------------------------------------------------------

  it('pairToPatient() retorna DispositivoEdgeView com pairingState paired', fakeAsync(() => {
    let result: DispositivoEdgeView | undefined;
    let error: unknown;

    adapter.pairToPatient('edge-new-001', { pacienteId: 'pac-marcio' }).subscribe({
      next: r => { result = r; },
      error: e => { error = e; },
    });

    if (label === 'http') {
      const req = controller.expectOne('/api/v1/edge/devices/edge-new-001/pairings');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toMatchObject({ pacienteId: 'pac-marcio' });
      req.flush(STUB_DEVICE_PAIRED);
    } else {
      tick(1000);
    }

    expect(error).toBeUndefined();
    expect(result).toMatchObject({
      deviceId: expect.any(String),
      pairingState: expect.any(String),
      status: expect.any(String),
      profileDrift: expect.any(Boolean),
      stalenessThresholdSeconds: expect.any(Number),
      serverTime: expect.any(String),
    });
  }));
});
