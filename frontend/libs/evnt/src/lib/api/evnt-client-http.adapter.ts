import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, catchError, throwError } from 'rxjs';
import { EVNT_EMPRESA_CONTEXT } from '../access/evnt-access-tokens';
import {
  EvntClientPort,
  SseFrame,
  ActiveEventsDto,
  EventoDto,
  ConfirmationResponseDto,
  ConfirmationVerdict,
  EventQueryParams,
  EventoPageDto,
} from './evnt-client.port';
import { mapProblemDetailToAppError } from './evnt-error-mapper';

// =============================================================================
// EvntClientHttpAdapter — production adapter
// Base path: /api/v1/events (routed via GWY).
//
// Multi-empresa (INT-FE-EVNT-006): injects EMPRESA_CONTEXT token (Wave-0 stub,
// defaults to null). When a non-null empresa id is available, appends the
// X-Empresa-Id header on every HTTP call. Full wiring is blocked on promotion
// of @neuroassist/shared/empresa-context from Wave-0 stub to real service.
//
// Error mapping (INT-FE-EVNT-006): every HTTP method pipes through
// mapProblemDetailToAppError so callers receive AppError with the ProblemDetail
// `type` URI as `code` — enabling GUARD-04 / ERR-01 / ERR-02 distinctions.
//
// TODO[INT-FE-EVNT-002]: EventSource (native) does not support custom headers.
// See openStream() for evaluated strategies and deferred approach.
// =============================================================================

/** Minimal SSE parser state — tracks Last-Event-ID for reconnect. */
interface SseParseState {
  lastEventId: number;
}

@Injectable({ providedIn: 'root' })
export class EvntClientHttpAdapter implements EvntClientPort {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/events';

  /**
   * EmpresaContext (Wave-0: null until @neuroassist/shared/empresa-context is promoted).
   * The header is conditionally added — a null context means the call is sent without it
   * (e.g. during the auth token exchange or on first load before tenant resolution).
   * Token: EVNT_EMPRESA_CONTEXT from evnt-access-tokens.ts (INT-FE-EVNT-006).
   */
  private readonly empresaContext = inject(EVNT_EMPRESA_CONTEXT, { optional: true });

  // ---------------------------------------------------------------------------
  // Header helpers
  // ---------------------------------------------------------------------------

  /**
   * Returns HttpHeaders with X-Empresa-Id when a non-null empresa id is available.
   * The value passes through even if it is an empty string to avoid masking misconfig.
   */
  private get empresaHeaders(): HttpHeaders {
    const id = this.empresaContext?.id ?? null;
    return id !== null
      ? new HttpHeaders({ 'X-Empresa-Id': id })
      : new HttpHeaders();
  }

  // ---------------------------------------------------------------------------
  // EvntClientPort implementation
  // ---------------------------------------------------------------------------

  openStream(): Observable<SseFrame> {
    // TODO[FE-EVNT-007]: implement fetch-based SSE with Authorization + X-Empresa-Id headers.
    // The native EventSource() cannot carry custom headers; evaluated strategies:
    //   a) fetch() streaming + ReadableStream (requires CORS + Sec-Fetch-Mode)
    //   b) eventsource-parser npm polyfill with fetch transport
    //   c) HttpClient with observe:'events' + responseType:'text' (interceptors carry Auth)
    // For now, returns an observable that immediately errors so the facade falls back
    // to polling (SPEC-04 §4 degradation path).
    return new Observable<SseFrame>((subscriber) => {
      const state: SseParseState = { lastEventId: 0 };
      void state; // suppress unused warning until TODO is resolved
      subscriber.error(new Error('SSE HTTP adapter not yet implemented — degrading to poll'));
    });
  }

  listActive(since?: string): Observable<ActiveEventsDto> {
    const params: Record<string, string> = {};
    if (since) params['since'] = since;
    return this.http
      .get<ActiveEventsDto>(`${this.base}/active`, {
        params,
        headers: this.empresaHeaders,
      })
      .pipe(catchError((err) => throwError(() => mapProblemDetailToAppError(err))));
  }

  get(eventoId: string): Observable<EventoDto> {
    return this.http
      .get<EventoDto>(`${this.base}/${eventoId}`, { headers: this.empresaHeaders })
      .pipe(catchError((err) => throwError(() => mapProblemDetailToAppError(err))));
  }

  confirm(eventoId: string, verdict: ConfirmationVerdict): Observable<ConfirmationResponseDto> {
    return this.http
      .post<ConfirmationResponseDto>(
        `${this.base}/${eventoId}/confirmation`,
        { verdict },
        { headers: this.empresaHeaders },
      )
      .pipe(catchError((err) => throwError(() => mapProblemDetailToAppError(err))));
  }

  ack(eventoId: string, dispatchId: string): Observable<void> {
    return this.http
      .post<void>(
        `${this.base}/${eventoId}/dispatches/${dispatchId}/ack`,
        {},
        { headers: this.empresaHeaders },
      )
      .pipe(catchError((err) => throwError(() => mapProblemDetailToAppError(err))));
  }

  query(params?: EventQueryParams): Observable<EventoPageDto> {
    const httpParams: Record<string, string | number> = {};
    if (params?.page !== undefined) httpParams['page'] = params.page;
    if (params?.size !== undefined) httpParams['size'] = params.size;
    if (params?.urgencia) httpParams['urgencia'] = params.urgencia;
    if (params?.since) httpParams['since'] = params.since;
    if (params?.feedback) httpParams['feedback'] = params.feedback;
    return this.http
      .get<EventoPageDto>(this.base, { params: httpParams, headers: this.empresaHeaders })
      .pipe(catchError((err) => throwError(() => mapProblemDetailToAppError(err))));
  }
}
