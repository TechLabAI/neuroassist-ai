import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
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

// =============================================================================
// EvntClientHttpAdapter — production adapter (stub compilable)
// Base path: /api/v1/events (routed via GWY — @neuroassist/shared/access-http-base
// injects X-Empresa-Id + Authorization on each HttpClient call).
//
// TODO[INT-FE-EVNT-002]: EventSource (native) does not support custom headers.
// Strategies evaluated:
//   a) fetch() streaming + ReadableStream (requires CORS + Sec-Fetch-Mode)
//   b) eventsource-parser npm polyfill with fetch transport
//   c) HttpClient + text/event-stream + XHR streaming (Angular interceptors carry Auth)
// Decision deferred to FE-EVNT-007; stub uses XHR-based approach placeholder.
// =============================================================================

/** Minimal SSE parser state — tracks Last-Event-ID for reconnect. */
interface SseParseState {
  lastEventId: number;
}

@Injectable({ providedIn: 'root' })
export class EvntClientHttpAdapter implements EvntClientPort {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/events';

  openStream(): Observable<SseFrame> {
    // TODO[FE-EVNT-007]: implement fetch-based SSE with Authorization header.
    // The native EventSource() cannot carry headers; use one of the strategies above.
    // For now, returns an observable that immediately errors so the facade falls back
    // to polling (SPEC-04 §4 degradation path).
    return new Observable<SseFrame>((subscriber) => {
      const state: SseParseState = { lastEventId: 0 };

      // TODO[INT-FE-EVNT-002]: replace with real fetch stream implementation
      // Example skeleton (eventsource-parser approach):
      //   const url = `${this.base}/stream?lastEventId=${state.lastEventId}`;
      //   fetch(url, { headers: { Authorization: `Bearer ${token}` } })
      //     .then(res => parseEventStream(res.body!, (frame) => subscriber.next(frame)))
      //     .catch(err => subscriber.error(err));

      void state; // suppress unused warning until TODO is resolved
      subscriber.error(new Error('SSE HTTP adapter not yet implemented — degrading to poll'));
    });
  }

  listActive(since?: string): Observable<ActiveEventsDto> {
    const params: Record<string, string> = {};
    if (since) params['since'] = since;
    return this.http.get<ActiveEventsDto>(`${this.base}/active`, { params });
  }

  get(eventoId: string): Observable<EventoDto> {
    return this.http.get<EventoDto>(`${this.base}/${eventoId}`);
  }

  confirm(eventoId: string, verdict: ConfirmationVerdict): Observable<ConfirmationResponseDto> {
    return this.http.post<ConfirmationResponseDto>(
      `${this.base}/${eventoId}/confirmation`,
      { verdict },
    );
  }

  ack(eventoId: string, dispatchId: string): Observable<void> {
    return this.http.post<void>(
      `${this.base}/${eventoId}/dispatches/${dispatchId}/ack`,
      {},
    );
  }

  query(params?: EventQueryParams): Observable<EventoPageDto> {
    const httpParams: Record<string, string | number> = {};
    if (params?.page !== undefined) httpParams['page'] = params.page;
    if (params?.size !== undefined) httpParams['size'] = params.size;
    if (params?.urgencia) httpParams['urgencia'] = params.urgencia;
    if (params?.since) httpParams['since'] = params.since;
    if (params?.feedback) httpParams['feedback'] = params.feedback;
    return this.http.get<EventoPageDto>(this.base, { params: httpParams });
  }
}

