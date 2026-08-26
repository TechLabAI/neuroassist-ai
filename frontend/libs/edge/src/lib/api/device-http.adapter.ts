import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { Observable, of, throwError } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { DevicePort } from './device.port';
import {
  DispositivoEdgeView,
  LivenessView,
  NotModified,
} from '@neuroassist/shared/contracts';

// ---------------------------------------------------------------------------
// DeviceHttpAdapter — compilable stub for Layer 1 (promote gate).
// Implements DevicePort against the frozen edge-service api-contract.
// Base path: /api/v1/edge (routed via GWY — api-contract §1.1).
//
// TODO[INT-FE-EDGE-001]: implement real HTTP calls (Layer 2)
// TODO[INT-FE-EDGE-003]: wire X-Correlation-Id via shared/access-http-base interceptor
// TODO[INT-FE-EDGE-005]: ETag / If-None-Match for liveness polls (304 handling)
// ---------------------------------------------------------------------------

const BASE = '/api/v1/edge';

/**
 * 304 é a única falha que significa "nada mudou". Todo o resto propaga.
 *
 * <p>Antes, os dois polls faziam `catchError(() => of({ notModified: true }))`:
 * uma queda de rede era reportada à facade como "sem novidades", `_pollOk`
 * permanecia `true` e o estado derivado seguia `online`. Isto é exatamente a
 * mentira que o ADR-005 existe para proibir — um dispositivo silencioso
 * aparecendo verde é um paciente sem voz com a tela dizendo que está tudo bem.
 * Falha de poll tem de virar `unknown`, e para isso ela precisa CHEGAR à facade.
 *
 * <p>O 304 é entregue pelo HttpClient como erro (só 2xx é sucesso), por isso o
 * teste vive aqui e não no `map`.
 */
function naoModificadoOuPropaga(
  err: HttpErrorResponse,
): Observable<LivenessView | NotModified> {
  if (err.status === 304) return of({ notModified: true } as NotModified);
  return throwError(() => err);
}


@Injectable()
export class DeviceHttpAdapter implements DevicePort {
  private readonly http = inject(HttpClient);

  getDevice(deviceId: string): Observable<DispositivoEdgeView> {
    // TODO[INT-FE-EDGE-001]: GET ${BASE}/devices/${deviceId}
    return this.http.get<DispositivoEdgeView>(`${BASE}/devices/${deviceId}`);
  }

  resolveDeviceForPatient(
    pacienteId: string,
  ): Observable<DispositivoEdgeView> {
    // TODO[INT-FE-EDGE-001]: GET ${BASE}/patients/${pacienteId}/device
    return this.http.get<DispositivoEdgeView>(
      `${BASE}/patients/${pacienteId}/device`,
    );
  }

  pollPatientLiveness(
    pacienteId: string,
    etag?: string,
  ): Observable<LivenessView | NotModified> {
    // TODO[INT-FE-EDGE-005]: attach If-None-Match header; handle 304 via catchError
    const headers = etag
      ? new HttpHeaders({ 'If-None-Match': etag })
      : new HttpHeaders();
    return this.http
      .get<LivenessView>(`${BASE}/patients/${pacienteId}/liveness`, {
        headers,
        observe: 'response',
      })
      .pipe(
        map((resp) => {
          if (resp.status === 304) return { notModified: true } as NotModified;
          return resp.body as LivenessView;
        }),
        catchError(naoModificadoOuPropaga),
      );
  }

  pollDeviceLiveness(
    deviceId: string,
    etag?: string,
  ): Observable<LivenessView | NotModified> {
    const headers = etag
      ? new HttpHeaders({ 'If-None-Match': etag })
      : new HttpHeaders();
    return this.http
      .get<LivenessView>(`${BASE}/devices/${deviceId}/liveness`, {
        headers,
        observe: 'response',
      })
      .pipe(
        map((resp) => {
          if (resp.status === 304) return { notModified: true } as NotModified;
          return resp.body as LivenessView;
        }),
        catchError(naoModificadoOuPropaga),
      );
  }

  unpair(deviceId: string, body: { reason: string }): Observable<void> {
    // TODO[INT-FE-EDGE-001]: DELETE ${BASE}/devices/${deviceId}/pairings/current
    return this.http.delete<void>(
      `${BASE}/devices/${deviceId}/pairings/current`,
      { body },
    );
  }

  retire(deviceId: string): Observable<DispositivoEdgeView> {
    // TODO[INT-FE-EDGE-001]: POST ${BASE}/devices/${deviceId}:retire
    return this.http.post<DispositivoEdgeView>(
      `${BASE}/devices/${deviceId}:retire`,
      {},
    );
  }
}
