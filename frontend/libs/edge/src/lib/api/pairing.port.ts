import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';
import {
  RegisterDeviceResult,
  DispositivoEdgeView,
} from '@neuroassist/shared/contracts';

// ---------------------------------------------------------------------------
// PairingPort — SPEC-04 §1 (pairing flow mutations)
// Abstraction over POST /api/v1/edge/devices (register) and
// POST /api/v1/edge/devices/{id}/pairings (pair). Consumed by PairingFacade.
// ---------------------------------------------------------------------------

export interface PairingPort {
  /** POST /api/v1/edge/devices — mint enrollment token + create device record.
   * Returns RegisterDeviceResult containing the one-time enrollment token
   * (shown once, then discarded — api-contract §2.1). */
  register(body: {
    label: string;
    pacienteHint?: string;
  }): Observable<RegisterDeviceResult>;

  /** POST /api/v1/edge/devices/{id}/pairings — bind device to patient.
   * Called after the agent claims the enrollment token. */
  pairToPatient(
    deviceId: string,
    body: { pacienteId: string },
  ): Observable<DispositivoEdgeView>;
}

export const PAIRING_PORT = new InjectionToken<PairingPort>('PairingPort');
