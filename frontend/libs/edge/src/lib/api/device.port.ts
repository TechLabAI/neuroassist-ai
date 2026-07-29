import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';
import {
  DispositivoEdgeView,
  LivenessView,
  NotModified,
} from '@neuroassist/shared/contracts';

// ---------------------------------------------------------------------------
// DevicePort — SPEC-04 §1 (read + management operations)
// Abstraction over GET /api/v1/edge/devices/{id}, liveness polls, and
// management commands (unpair, retire). Consumed by DeviceFacade.
// ---------------------------------------------------------------------------

export interface DevicePort {
  /** GET /api/v1/edge/devices/{deviceId} — full device snapshot. */
  getDevice(deviceId: string): Observable<DispositivoEdgeView>;

  /** GET /api/v1/edge/patients/{pacienteId}/device — resolve active device for patient. */
  resolveDeviceForPatient(
    pacienteId: string,
  ): Observable<DispositivoEdgeView>;

  /** GET /api/v1/edge/patients/{pacienteId}/liveness — ETag/304-capable liveness poll. */
  pollPatientLiveness(
    pacienteId: string,
    etag?: string,
  ): Observable<LivenessView | NotModified>;

  /** GET /api/v1/edge/devices/{deviceId}/liveness — ETag/304-capable liveness poll. */
  pollDeviceLiveness(
    deviceId: string,
    etag?: string,
  ): Observable<LivenessView | NotModified>;

  /** DELETE /api/v1/edge/devices/{id}/pairings/current — unpair (management, conductor only). */
  unpair(
    deviceId: string,
    body: { reason: string },
  ): Observable<void>;

  /** POST /api/v1/edge/devices/{id}:retire — retire device (management, conductor only). */
  retire(deviceId: string): Observable<DispositivoEdgeView>;
}

export const DEVICE_PORT = new InjectionToken<DevicePort>('DevicePort');
