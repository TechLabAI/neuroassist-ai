import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
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
import { parseSseLine, createSseBuffer } from './evnt-sse-parser';

// =============================================================================
// EvntClientHttpAdapter — production adapter (P6-A, P6-B refactor)
// Base path: /api/v1/evnt/events (GWY-normalized — ADR-008 / E2E-014).
// Previously: /api/v1/events (direct service path, pre-ADR-008).
//
// Multi-empresa (INT-FE-EVNT-001):
//   HttpClient calls: X-Empresa-Id added by evntHttpInterceptorFn (withInterceptors).
//   fetch()-based SSE: X-Empresa-Id read from EVNT_EMPRESA_CONTEXT in constructor
//   (fetch() does not pass through Angular interceptors).
//
// SSE (P6-B, INT-FE-EVNT-002):
//   openStream() uses fetch() + ReadableStream to support custom headers.
//   On stream close (not abort): Observable completes so facade can degrade to polling.
//   On fetch error: Observable errors so facade degradation path activates.
//
// Error mapping (INT-FE-EVNT-006): every HttpClient call pipes through
//   mapProblemDetailToAppError preserving ProblemDetail.type in AppError.type.
// =============================================================================

@Injectable({ providedIn: 'root' })
export class EvntClientHttpAdapter implements EvntClientPort {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/evnt/events';

  /**
   * EmpresaContext captured at construction time for use inside fetch() callbacks.
   * fetch() does not pass through HttpClient interceptors, so we read the context
   * directly here (P6-B risk mitigation — inject() cannot be called inside Observable
   * callback).
   */
  private readonly empresaContext = inject(EVNT_EMPRESA_CONTEXT, { optional: true });

  // ---------------------------------------------------------------------------
  // EvntClientPort implementation
  // ---------------------------------------------------------------------------

  /**
   * Open SSE live channel via fetch() + ReadableStream (P6-B, INT-FE-EVNT-002).
   *
   * Behaviour:
   * - Uses AbortController so the Observable teardown aborts the fetch cleanly.
   * - X-Empresa-Id added manually (fetch bypasses HttpClient interceptors).
   * - SSE lines parsed by evnt-sse-parser (pure function, no DI).
   * - On stream close (response body exhausted): subscriber.complete() → facade degrades.
   * - On fetch / decode error: subscriber.error(err) → facade degradation path.
   */
  openStream(): Observable<SseFrame> {
    const empresaId = this.empresaContext?.id ?? null;

    return new Observable<SseFrame>((subscriber) => {
      const controller = new AbortController();

      const headers: Record<string, string> = {
        Accept: 'text/event-stream',
        'Cache-Control': 'no-cache',
      };
      if (empresaId) {
        headers['X-Empresa-Id'] = empresaId;
      }

      const processStream = async () => {
        let response: Response;
        try {
          response = await fetch(`${this.base}/stream`, {
            headers,
            signal: controller.signal,
          });
        } catch (err) {
          if (!controller.signal.aborted) {
            subscriber.error(err);
          }
          return;
        }

        if (!response.ok) {
          subscriber.error(new Error(`SSE endpoint returned ${response.status}`));
          return;
        }

        const body = response.body;
        if (!body) {
          subscriber.error(new Error('SSE response body is null'));
          return;
        }

        const reader = body.getReader();
        const decoder = new TextDecoder();
        const sseBuffer = createSseBuffer();
        let textBuffer = '';

        try {
          while (!controller.signal.aborted) {
            const { done, value } = await reader.read();
            if (done) {
              // Stream closed gracefully — complete so facade degrades to polling
              subscriber.complete();
              break;
            }

            textBuffer += decoder.decode(value, { stream: true });
            const lines = textBuffer.split('\n');

            // Keep the last (potentially incomplete) line in the buffer
            textBuffer = lines.pop() ?? '';

            for (const line of lines) {
              const frame = parseSseLine(line, sseBuffer);
              if (frame !== null) {
                subscriber.next(frame);
              }
            }
          }
        } catch (err) {
          if (!controller.signal.aborted) {
            subscriber.error(err);
          }
        } finally {
          reader.releaseLock();
        }
      };

      void processStream();

      // Teardown: abort the fetch when Observable is unsubscribed
      return () => {
        controller.abort();
      };
    });
  }

  listActive(since?: string): Observable<ActiveEventsDto> {
    const params: Record<string, string> = {};
    if (since) params['since'] = since;
    return this.http
      .get<ActiveEventsDto>(`${this.base}/active`, { params })
      .pipe(catchError((err) => throwError(() => mapProblemDetailToAppError(err))));
  }

  get(eventoId: string): Observable<EventoDto> {
    return this.http
      .get<EventoDto>(`${this.base}/${eventoId}`)
      .pipe(catchError((err) => throwError(() => mapProblemDetailToAppError(err))));
  }

  confirm(eventoId: string, verdict: ConfirmationVerdict): Observable<ConfirmationResponseDto> {
    return this.http
      .post<ConfirmationResponseDto>(
        `${this.base}/${eventoId}/confirmation`,
        { verdict },
      )
      .pipe(catchError((err) => throwError(() => mapProblemDetailToAppError(err))));
  }

  ack(eventoId: string, dispatchId: string): Observable<void> {
    return this.http
      .post<void>(
        `${this.base}/${eventoId}/dispatches/${dispatchId}/ack`,
        {},
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
      .get<EventoPageDto>(this.base, { params: httpParams })
      .pipe(catchError((err) => throwError(() => mapProblemDetailToAppError(err))));
  }
}
