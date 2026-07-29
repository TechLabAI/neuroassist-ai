import { Injectable } from '@angular/core';
import { Observable, of, delay } from 'rxjs';
import { PairingPort } from './pairing.port';
import {
  RegisterDeviceResult,
  DispositivoEdgeView,
} from '@neuroassist/shared/contracts';

// ---------------------------------------------------------------------------
// PairingMockAdapter — simulates the pairing flow for dev/test.
// register() returns a mock enrollment token; pairToPatient() returns a
// paired device view. Both carry 400ms delay to reflect real API latency.
// ---------------------------------------------------------------------------

@Injectable()
export class PairingMockAdapter implements PairingPort {
  register(body: {
    label: string;
    pacienteHint?: string;
  }): Observable<RegisterDeviceResult> {
    const deviceId = `edge-mock-${Date.now()}`;
    const expiresAt = new Date(Date.now() + 5 * 60 * 1000).toISOString(); // 5 min TTL
    const result: RegisterDeviceResult = {
      deviceId,
      pairingState: 'registered',
      label: body.label || 'Dispositivo Mocked',
      enrollmentToken: 'MOCK-TOKEN-ABCD-1234', // one-time claim code (mock)
      enrollmentExpiresAt: expiresAt,
      createdAt: new Date().toISOString(),
    };
    return of(result).pipe(delay(400));
  }

  pairToPatient(
    deviceId: string,
    body: { pacienteId: string },
  ): Observable<DispositivoEdgeView> {
    const paired: DispositivoEdgeView = {
      deviceId,
      tenantRef: 'tenant-familia-amaral',
      pacienteId: body.pacienteId,
      pairingState: 'paired',
      status: 'online',
      ultimaSincronizacao: new Date(Date.now() - 5000).toISOString(), // 5s ago (fresh)
      versao: '1.4.2',
      runningProfileVersion: 3,
      currentProfileVersion: 3,
      profileDrift: false,
      stalenessThresholdSeconds: 90,
      serverTime: new Date().toISOString(),
    };
    return of(paired).pipe(delay(800)); // longer delay to simulate real pairing
  }
}
