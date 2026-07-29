import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PairingPort } from './pairing.port';
import {
  RegisterDeviceResult,
  DispositivoEdgeView,
} from '@neuroassist/shared/contracts';

// ---------------------------------------------------------------------------
// PairingHttpAdapter — compilable stub for Layer 1.
// Implements PairingPort against the frozen edge-service api-contract.
//
// TODO[INT-FE-EDGE-001]: implement real HTTP calls (Layer 2)
// TODO[INT-FE-EDGE-002]: ACS<->EDGE dynamic client registration (EDGE-INT-002, P5)
// ---------------------------------------------------------------------------

const BASE = '/api/v1/edge';

@Injectable()
export class PairingHttpAdapter implements PairingPort {
  private readonly http = inject(HttpClient);

  register(body: {
    label: string;
    pacienteHint?: string;
  }): Observable<RegisterDeviceResult> {
    // TODO[INT-FE-EDGE-001]: POST ${BASE}/devices
    return this.http.post<RegisterDeviceResult>(`${BASE}/devices`, body);
  }

  pairToPatient(
    deviceId: string,
    body: { pacienteId: string },
  ): Observable<DispositivoEdgeView> {
    // TODO[INT-FE-EDGE-001]: POST ${BASE}/devices/${deviceId}/pairings
    return this.http.post<DispositivoEdgeView>(
      `${BASE}/devices/${deviceId}/pairings`,
      body,
    );
  }
}
