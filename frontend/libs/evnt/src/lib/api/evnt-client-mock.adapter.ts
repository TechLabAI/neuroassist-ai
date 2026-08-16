import { Injectable } from '@angular/core';
import { Observable, of, timer, EMPTY, Subject, throwError } from 'rxjs';
import { map, switchMap } from 'rxjs/operators';
import {
  EvntClientPort,
  SseFrame,
  ActiveEventsDto,
  EventoDto,
  ConfirmationResponseDto,
  ConfirmationVerdict,
  EventQueryParams,
  EventoPageDto,
  EventoSummaryDto,
} from './evnt-client.port';

// =============================================================================
// EvntClientMockAdapter — dev/test (default adapter)
// Seed data mirrors project-context.json §data_model.mock and
// design-reference prototype state (alarme.component.ts scenario).
// Delay 400 ms on list/get operations (realistic latency simulation).
//
// E2E localStorage flags (set via page.addInitScript before page.goto):
//   __EVNT_E2E_NO_EMERGENCY__   → listActive() returns activeEvent: null (only routines)
//   __EVNT_E2E_SSE_FAIL__       → openStream() throws immediately (triggers degraded mode)
//   __EVNT_E2E_CONFIRM_FAIL__   → confirm() throws a network error
//   __EVNT_E2E_CONFIRM_409__    → confirm() throws /errors/feedback-terminal (concurrent confirm)
//
// These flags follow the ACS pattern (__ACS_E2E_LIST_FAIL__ etc.) and are only
// readable in a browser context (localStorage unavailable under Jest → safe to guard).
// =============================================================================

/** Guard: returns localStorage flag value only in browser context (not under Jest). */
function e2eFlag(key: string): boolean {
  try {
    return typeof localStorage !== 'undefined' && localStorage.getItem(key) === 'true';
  } catch {
    return false;
  }
}

// ---------------------------------------------------------------------------
// Seed mock data (matches prototype alarme.component.ts scenario)
// ---------------------------------------------------------------------------

const MOCK_CAREGIVER_ID = 'cuid-jamilly';

const MOCK_ACTIVE_EMERGENCY: EventoSummaryDto = {
  eventoId: 'evt-001',
  urgencia: 'emergencia',
  tier: 'full_screen_alarm',
  commandLabel: 'Preciso de ajuda',
  feedback: 'nao_avaliado',
  recognizedAt: new Date(Date.now() - 3 * 60_000).toISOString(), // 3 min ago
};

const MOCK_ROUTINE_1: EventoSummaryDto = {
  eventoId: 'evt-002',
  urgencia: 'rotina',
  tier: 'discreet',
  commandLabel: 'Água',
  feedback: 'nao_avaliado',
  recognizedAt: new Date(Date.now() - 12 * 60_000).toISOString(),
};

const MOCK_ROUTINE_2: EventoSummaryDto = {
  eventoId: 'evt-003',
  urgencia: 'rotina',
  tier: 'discreet',
  commandLabel: 'Coberta',
  feedback: 'nao_avaliado',
  recognizedAt: new Date(Date.now() - 25 * 60_000).toISOString(),
};

const MOCK_EVENTO_DTO: EventoDto = {
  eventoId: MOCK_ACTIVE_EMERGENCY.eventoId,
  pacienteId: 'pac-marcio',
  comandoRef: 'cmd-ajuda',
  commandLabelSnapshot: MOCK_ACTIVE_EMERGENCY.commandLabel,
  urgenciaSnapshot: 'emergencia',
  timingWindow: { startMs: 1753000000000, endMs: 1753000000850, durationMs: 850 },
  feedback: 'nao_avaliado',
  recognizedAt: MOCK_ACTIVE_EMERGENCY.recognizedAt,
};

// In-memory confirmed set (survives the session, reset on page reload)
const _confirmed = new Map<string, { confirmadoPor: string; confirmadoAt: string }>();

// SSE emitter subject — used to push frames to openStream() subscribers
const _sseSubject = new Subject<SseFrame>();
let _sseSeq = 100;

function emitSse(frame: Omit<SseFrame, 'seq'>): void {
  _sseSubject.next({ ...frame, seq: ++_sseSeq });
}

// ---------------------------------------------------------------------------

@Injectable({ providedIn: 'root' })
export class EvntClientMockAdapter implements EvntClientPort {
  /**
   * Simulates the SSE live stream. In dev, keeps the connection open until
   * the module is destroyed. To simulate a new event in e2e tests, call
   * EvntClientMockAdapter.pushMockEvent() on the DI-resolved instance.
   *
   * E2E flag __EVNT_E2E_SSE_FAIL__: throws immediately so EventDeliveryFacade
   * detects the error and activates degraded polling mode (BRIDGE-01 in P9 spec).
   */
  openStream(): Observable<SseFrame> {
    if (e2eFlag('__EVNT_E2E_SSE_FAIL__')) {
      return throwError(() => new Error('e2e: SSE forced failure'));
    }

    // Emit a keepalive every 30 s to simulate liveness
    const keepalive$ = timer(5_000, 30_000).pipe(
      map((i): SseFrame => ({ kind: 'keepalive', seq: ++_sseSeq + i, payload: null })),
    );

    return new Observable<SseFrame>((subscriber) => {
      const sub = _sseSubject.subscribe(subscriber);
      const keepSub = keepalive$.subscribe((frame) => subscriber.next(frame));
      return () => { sub.unsubscribe(); keepSub.unsubscribe(); };
    });
  }

  /**
   * E2E flag __EVNT_E2E_NO_EMERGENCY__: returns activeEvent: null and events without
   * the emergency event. Used by notificacao-rotina spec to verify routine-only state.
   */
  listActive(since?: string): Observable<ActiveEventsDto> {
    return timer(400).pipe(
      switchMap(() => {
        const noEmergency = e2eFlag('__EVNT_E2E_NO_EMERGENCY__');
        const activeEvt = (noEmergency || _confirmed.has(MOCK_ACTIVE_EMERGENCY.eventoId))
          ? null
          : MOCK_ACTIVE_EMERGENCY;

        const allEvents: EventoSummaryDto[] = noEmergency
          ? [MOCK_ROUTINE_1, MOCK_ROUTINE_2]
          : [MOCK_ACTIVE_EMERGENCY, MOCK_ROUTINE_1, MOCK_ROUTINE_2];

        const filtered = since
          ? allEvents.filter((e) => e.recognizedAt > since)
          : allEvents;

        return of<ActiveEventsDto>({
          serverTime: new Date().toISOString(),
          activeEvent: activeEvt,
          events: filtered,
        });
      }),
    );
  }

  get(eventoId: string): Observable<EventoDto> {
    return timer(400).pipe(
      switchMap(() => {
        if (eventoId === MOCK_ACTIVE_EMERGENCY.eventoId) {
          const conf = _confirmed.get(eventoId);
          return of<EventoDto>({
            ...MOCK_EVENTO_DTO,
            feedback: conf ? 'real' : 'nao_avaliado',
            confirmadoPor: conf?.confirmadoPor,
            confirmadoAt: conf?.confirmadoAt,
          });
        }
        // 404 for unknown ids — let caller handle
        throw new Error(`mock: eventoId not found: ${eventoId}`);
      }),
    );
  }

  /**
   * E2E flag __EVNT_E2E_CONFIRM_FAIL__: throws a generic error simulating network failure.
   *   → overlay stays visible + error message appears (confirmar-atendimento scenario 2).
   * E2E flag __EVNT_E2E_CONFIRM_409__: throws a feedback-terminal error simulating concurrent
   *   confirmation by another caregiver → overlay clears (success-equivalent).
   *   → confirmacao-concorrente scenario.
   */
  confirm(eventoId: string, verdict: ConfirmationVerdict): Observable<ConfirmationResponseDto> {
    return timer(600).pipe(
      switchMap(() => {
        if (e2eFlag('__EVNT_E2E_CONFIRM_FAIL__')) {
          return throwError(() => new Error('e2e: confirm forced network failure'));
        }

        if (e2eFlag('__EVNT_E2E_CONFIRM_409__')) {
          const concurrentErr = {
            type: '/errors/feedback-terminal',
            code: '/errors/feedback-terminal',
            confirmadoPor: 'Outra Cuidadora',
            confirmadoAt: '2026-08-16T10:00:00Z',
            message: 'Evento já foi confirmado por outra cuidadora',
          };
          return throwError(() => concurrentErr);
        }

        const confirmadoAt = new Date().toISOString();
        _confirmed.set(eventoId, { confirmadoPor: MOCK_CAREGIVER_ID, confirmadoAt });

        // Push SSE frame so subscribers see the update
        emitSse({
          kind: 'evento.updated',
          payload: {
            eventoId,
            urgencia: 'emergencia',
            tier: 'full_screen_alarm',
            commandLabel: MOCK_ACTIVE_EMERGENCY.commandLabel,
            feedback: verdict,
            recognizedAt: MOCK_ACTIVE_EMERGENCY.recognizedAt,
          },
        });

        return of<ConfirmationResponseDto>({
          eventoId,
          feedback: verdict,
          confirmadoPor: MOCK_CAREGIVER_ID,
          confirmadoAt,
        });
      }),
    );
  }

  ack(_eventoId: string, _dispatchId: string): Observable<void> {
    return timer(200).pipe(switchMap(() => EMPTY as Observable<void>));
  }

  query(_params?: EventQueryParams): Observable<EventoPageDto> {
    return timer(400).pipe(
      switchMap(() =>
        of<EventoPageDto>({
          content: [MOCK_EVENTO_DTO],
          totalElements: 1,
          totalPages: 1,
          page: 0,
          size: 20,
        }),
      ),
    );
  }

  /** Test utility: push a synthetic SSE frame (e.g. from e2e Playwright helpers). */
  pushMockEvent(event: EventoSummaryDto): void {
    emitSse({ kind: 'evento.created', payload: event });
  }
}
