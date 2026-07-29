import { Injectable } from '@angular/core';
import { Observable, of, delay } from 'rxjs';
import { DevicePort } from './device.port';
import {
  DispositivoEdgeView,
  LivenessView,
  NotModified,
} from '@neuroassist/shared/contracts';

// ---------------------------------------------------------------------------
// DeviceMockAdapter — seed data derived from prototype mock-data.ts.
// Default in dev (provideEdge({ adapter: 'mock' })). 400ms delay.
// ---------------------------------------------------------------------------

const MOCK_DEVICE: DispositivoEdgeView = {
  deviceId: 'edge-marcio-01',
  tenantRef: 'tenant-familia-amaral',
  pacienteId: 'pac-marcio',
  pairingState: 'paired',
  status: 'online',
  ultimaSincronizacao: new Date(Date.now() - 12000).toISOString(), // 12s ago (fresh)
  versao: '1.4.2',
  runningProfileVersion: 3,
  currentProfileVersion: 3,
  profileDrift: false,
  stalenessThresholdSeconds: 90,
  serverTime: new Date().toISOString(),
};

const MOCK_LIVENESS: LivenessView = {
  deviceId: 'edge-marcio-01',
  pacienteId: 'pac-marcio',
  status: 'online',
  ultimaSincronizacao: new Date(Date.now() - 12000).toISOString(),
  versao: '1.4.2',
  stalenessThresholdSeconds: 90,
  serverTime: new Date().toISOString(),
};

@Injectable()
export class DeviceMockAdapter implements DevicePort {
  getDevice(_deviceId: string): Observable<DispositivoEdgeView> {
    return of({ ...MOCK_DEVICE, serverTime: new Date().toISOString() }).pipe(
      delay(400),
    );
  }

  resolveDeviceForPatient(
    _pacienteId: string,
  ): Observable<DispositivoEdgeView> {
    return of({ ...MOCK_DEVICE, serverTime: new Date().toISOString() }).pipe(
      delay(400),
    );
  }

  pollPatientLiveness(
    _pacienteId: string,
    _etag?: string,
  ): Observable<LivenessView | NotModified> {
    // Mock: always return fresh liveness (simulate online device)
    return of({
      ...MOCK_LIVENESS,
      ultimaSincronizacao: new Date(Date.now() - 12000).toISOString(),
      serverTime: new Date().toISOString(),
    }).pipe(delay(200));
  }

  pollDeviceLiveness(
    _deviceId: string,
    _etag?: string,
  ): Observable<LivenessView | NotModified> {
    return of({
      ...MOCK_LIVENESS,
      ultimaSincronizacao: new Date(Date.now() - 12000).toISOString(),
      serverTime: new Date().toISOString(),
    }).pipe(delay(200));
  }

  unpair(_deviceId: string, _body: { reason: string }): Observable<void> {
    return of(undefined).pipe(delay(400));
  }

  retire(_deviceId: string): Observable<DispositivoEdgeView> {
    return of({
      ...MOCK_DEVICE,
      pairingState: 'retired' as const,
      status: 'n/a' as const,
      serverTime: new Date().toISOString(),
    }).pipe(delay(400));
  }
}
