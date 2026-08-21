import { Injectable, inject, signal, computed, DestroyRef, Signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { lastValueFrom, Subscription, timer, switchMap } from 'rxjs';
import { AppError } from '@neuroassist/shared/error-model';
import { EVNT_CLIENT_PORT } from './api/evnt-client.port';
import type { EventoSummaryDto } from './api/evnt-client.port';
import { EvntFeatureFlagsService } from './api/evnt-feature-flags.service';
import { EvntTelemetryService } from './telemetry/evnt-telemetry.service';
import {
  EventoVm,
  AlarmVm,
  DeliveryMode,
  ConfirmationVerdict,
  toDisplayTime,
} from './evnt.types';

// =============================================================================
// EventDeliveryFacade — SPEC-03 §5, SPEC-04
// Single state owner for EVNT: alarm state, routine queue, live-delivery mode.
// Signals-first (Angular 21, no NgRx). OnPush-safe: all reads via .asReadonly().
//
// P6-D: injected EvntFeatureFlagsService — reads liveChannel flag before opening
//   SSE. If liveChannel === 'off': starts polling directly (no SSE attempt).
//   pollIntervalMs from feature flag used in _startPolling().
// =============================================================================

/** Patient name resolved from tenant context.
 * TODO[FE-EVNT-012]: inject from @neuroassist/shared/empresa-context (patient roster).
 * Hardcoded for MVP prototype parity. */
const PATIENT_NAME_PLACEHOLDER = 'Márcio';

function mapSummaryToVm(dto: EventoSummaryDto): EventoVm {
  return {
    eventoId: dto.eventoId,
    urgencia: dto.urgencia,
    tier: dto.tier,
    commandLabel: dto.commandLabel,
    patientName: PATIENT_NAME_PLACEHOLDER,
    recognizedAt: dto.recognizedAt,
    displayTime: toDisplayTime(dto.recognizedAt),
    feedback: dto.feedback,
    confirmadoPor: null,
    confirmadoAt: null,
  };
}

function toAppError(err: unknown): AppError {
  if (err instanceof Error) return { code: 'EVNT_ERROR', message: err.message };
  return { code: 'EVNT_UNKNOWN', message: 'Erro desconhecido' };
}

@Injectable({ providedIn: 'root' })
export class EventDeliveryFacade {
  private readonly port = inject(EVNT_CLIENT_PORT);
  private readonly destroyRef = inject(DestroyRef);
  private readonly flagService = inject(EvntFeatureFlagsService);
  private readonly telemetry = inject(EvntTelemetryService);

  // ---------------------------------------------------------------------------
  // Core state signals (private — mutated only by facade methods)
  // ---------------------------------------------------------------------------

  private readonly _activeEvent = signal<EventoVm | null>(null);
  private readonly _routineQueue = signal<readonly EventoVm[]>([]);
  private readonly _recentEvents = signal<readonly EventoVm[]>([]);
  private readonly _deliveryMode = signal<DeliveryMode>('live');
  private readonly _loading = signal(false);
  private readonly _error = signal<AppError | null>(null);

  // Alarm-specific transient state
  private readonly _confirming = signal(false);
  private readonly _confirmError = signal<AppError | null>(null);
  private readonly _alreadyConfirmed = signal(false);
  private readonly _alreadyConfirmedBy = signal<string | null>(null);

  // Detail-page state
  private readonly _selectedEvent = signal<EventoVm | null>(null);

  // ---------------------------------------------------------------------------
  // Public read-only signals (SPEC-03 §5)
  // ---------------------------------------------------------------------------

  readonly activeEvent = this._activeEvent.asReadonly();
  readonly routineQueue = this._routineQueue.asReadonly();
  readonly recentEvents = this._recentEvents.asReadonly();
  readonly deliveryMode = this._deliveryMode.asReadonly();
  readonly loading = this._loading.asReadonly();
  readonly error = this._error.asReadonly();

  /** Selected event for the detail page (FE-EVNT-016). Null before load or on 404. */
  readonly selectedEvent: Signal<EventoVm | null> = this._selectedEvent.asReadonly();

  /** Confirmation in flight (CONCUR-01) — drives FeedbackVerdictControl[disabled] in detail page. */
  readonly confirming = this._confirming.asReadonly();

  /** Name of the caregiver who already confirmed (409 concurrent-confirmation path). */
  readonly alreadyConfirmedBy = this._alreadyConfirmedBy.asReadonly();

  /** Composite AlarmVm signal (SPEC-03 §2) — drives AlarmOverlayComponent. */
  readonly alarmVm = computed<AlarmVm>(() => ({
    event: this._activeEvent(),
    visible: this._activeEvent() !== null,
    confirming: this._confirming(),
    confirmError: this._confirmError(),
    alreadyConfirmed: this._alreadyConfirmed(),
  }));

  // ---------------------------------------------------------------------------
  // Internal stream management
  // ---------------------------------------------------------------------------

  private streamSub: Subscription | null = null;
  private pollSub: Subscription | null = null;
  private lastSseSeq = 0;

  // ---------------------------------------------------------------------------
  // Lifecycle — called by evnt.bootstrap.ts (SPEC-06 §3)
  // ---------------------------------------------------------------------------

  /** Hydrate active events from GET /events/active before opening the stream.
   * Guarantees overlay renders on refresh during an active alarm (SPEC-04 §4). */
  async hydrate(): Promise<void> {
    this._loading.set(true);
    try {
      const dto = await lastValueFrom(this.port.listActive());
      if (dto.activeEvent && dto.activeEvent.urgencia === 'emergencia') {
        this._activeEvent.set(mapSummaryToVm(dto.activeEvent));
        // P8: alarm received via hydration (urgency only — zero PII)
        this.telemetry.trackAlarmReceived(dto.activeEvent.urgencia);
      }
      const routines = dto.events
        .filter((e) => e.urgencia === 'rotina' && e.feedback === 'nao_avaliado')
        .map(mapSummaryToVm);
      this._routineQueue.set(routines);
    } catch (err) {
      this._error.set(toAppError(err));
    } finally {
      this._loading.set(false);
    }
  }

  /**
   * Open SSE live channel. On error falls back to polling (SPEC-04 §3/§4).
   * P6-D: checks liveChannel feature flag before attempting SSE.
   *   If liveChannel === 'off': skip SSE and start polling directly.
   */
  startLiveChannel(): void {
    // P6-D: respect liveChannel feature flag
    if (!this.flagService.isLiveChannelEnabled()) {
      this._deliveryMode.set('degraded');
      // P8: SSE disabled by flag → degraded activated with 'sse_timeout' reason
      this.telemetry.trackDegradedActivated('sse_timeout');
      this._startPolling();
      return;
    }

    this.streamSub = this.port
      .openStream()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (frame) => {
          if (frame.kind === 'keepalive') return;
          if (frame.seq > 0) this.lastSseSeq = frame.seq;
          if (!frame.payload) return;

          const dto = frame.payload;
          if (frame.kind === 'evento.created') {
            if (dto.urgencia === 'emergencia') {
              this._activeEvent.set(mapSummaryToVm(dto));
            } else {
              this._routineQueue.update((q) => [...q, mapSummaryToVm(dto)]);
            }
          } else if (frame.kind === 'evento.updated') {
            // Patch activeEvent if it's the same event and now has feedback
            const active = this._activeEvent();
            if (active && active.eventoId === dto.eventoId && dto.feedback !== 'nao_avaliado') {
              this._activeEvent.set(null);
            }
          }
        },
        error: () => {
          // Stream error → degrade to polling (SPEC-04 §4)
          // P8: emit SSE error event first, then degraded activation
          this.telemetry.trackSseError();
          this._deliveryMode.set('degraded');
          this.telemetry.trackDegradedActivated('sse_error');
          this._startPolling();
        },
      });
  }

  /** Stop SSE connection and any polling interval. Called on module destroy. */
  stopLiveChannel(): void {
    this.streamSub?.unsubscribe();
    this.streamSub = null;
    this._stopPolling();
  }

  // ---------------------------------------------------------------------------
  // Actions — called by components
  // ---------------------------------------------------------------------------

  /** Confirm attendance (non-optimistic — clears activeEvent only on 200). SPEC-04 §5. */
  async confirm(eventoId: string, verdict: ConfirmationVerdict): Promise<void> {
    if (this._confirming()) return; // guard concurrent clicks (CONCUR-01)
    this._confirming.set(true);
    this._confirmError.set(null);
    this._alreadyConfirmed.set(false);
    this._alreadyConfirmedBy.set(null);
    // P8: confirmation flow initiated (opaque eventoId — not PII)
    this.telemetry.trackConfirmStart(eventoId);
    try {
      await lastValueFrom(this.port.confirm(eventoId, verdict));
      // Only clear active event after confirmed 200 (safety invariant)
      this._activeEvent.set(null);
      // P8: confirmation succeeded
      this.telemetry.trackConfirmSuccess(eventoId);
    } catch (err: unknown) {
      // 409 feedback-terminal → treat as success (already confirmed by someone else)
      const appErr = err as { code?: string; type?: string; confirmadoPor?: string } | null;
      const isFeedbackTerminal =
        appErr !== null &&
        typeof appErr === 'object' &&
        (appErr.code === '/errors/feedback-terminal' ||
          appErr.type === '/errors/feedback-terminal');
      if (isFeedbackTerminal) {
        this._alreadyConfirmed.set(true);
        this._alreadyConfirmedBy.set(appErr?.confirmadoPor ?? null);
        this._activeEvent.set(null);
        // P8: concurrent confirmation — 409 feedback-terminal
        this.telemetry.trackConfirmConflict(eventoId);
      } else {
        this._confirmError.set(toAppError(err));
        // P8: confirmation failed with non-409 error
        this.telemetry.trackConfirmError();
      }
    } finally {
      this._confirming.set(false);
    }
  }

  /** Client-only optimistic dismiss from routine queue (no server call). SPEC-04 §5. */
  dismiss(eventoId: string): void {
    this._routineQueue.update((q) => q.filter((e) => e.eventoId !== eventoId));
  }

  /** Load event detail for the detail page (FE-EVNT-016).
   * On success: sets selectedEvent signal, clears error.
   * On error: sets selectedEvent to null AND sets error signal (P6-G) so the
   * component can distinguish /errors/forbidden from /errors/tenant-mismatch etc.
   * Returns Promise<void> — detail page reads via facade.selectedEvent() and error() signals. */
  async loadDetail(eventoId: string): Promise<void> {
    this._loading.set(true);
    this._error.set(null);
    try {
      const dto = await lastValueFrom(this.port.get(eventoId));
      this._selectedEvent.set({
        eventoId: dto.eventoId,
        urgencia: dto.urgenciaSnapshot,
        tier: dto.urgenciaSnapshot === 'emergencia' ? 'full_screen_alarm' : 'discreet',
        commandLabel: dto.commandLabelSnapshot,
        patientName: PATIENT_NAME_PLACEHOLDER,
        recognizedAt: dto.recognizedAt,
        displayTime: toDisplayTime(dto.recognizedAt),
        feedback: dto.feedback,
        confirmadoPor: dto.confirmadoPor ?? null,
        confirmadoAt: dto.confirmadoAt ?? null,
      });
    } catch (err) {
      this._selectedEvent.set(null);
      // P6-G: surface the typed error so EventDetailPageComponent can route on it
      this._error.set(toAppError(err));
    } finally {
      this._loading.set(false);
    }
  }

  /** Load history page into recentEvents signal. */
  async loadHistory(params?: { page?: number; size?: number }): Promise<void> {
    this._loading.set(true);
    try {
      const page = await lastValueFrom(this.port.query(params));
      this._recentEvents.set(
        page.content.map((dto) => ({
          eventoId: dto.eventoId,
          urgencia: dto.urgenciaSnapshot,
          tier: dto.urgenciaSnapshot === 'emergencia' ? 'full_screen_alarm' : 'discreet',
          commandLabel: dto.commandLabelSnapshot,
          patientName: PATIENT_NAME_PLACEHOLDER,
          recognizedAt: dto.recognizedAt,
          displayTime: toDisplayTime(dto.recognizedAt),
          feedback: dto.feedback,
          confirmadoPor: dto.confirmadoPor ?? null,
          confirmadoAt: dto.confirmadoAt ?? null,
        } satisfies EventoVm)),
      );
    } catch (err) {
      this._error.set(toAppError(err));
    } finally {
      this._loading.set(false);
    }
  }

  // ---------------------------------------------------------------------------
  // Private helpers
  // ---------------------------------------------------------------------------

  private _startPolling(): void {
    this._stopPolling();
    const pollIntervalMs = this.flagService.getPollIntervalMs();
    this.pollSub = timer(0, pollIntervalMs)
      .pipe(
        switchMap(() => this.port.listActive(new Date().toISOString())),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (dto) => {
          if (dto.activeEvent && dto.activeEvent.urgencia === 'emergencia') {
            this._activeEvent.set(mapSummaryToVm(dto.activeEvent));
          } else if (!dto.activeEvent) {
            // Poll confirmed no active emergency — keep current until user confirms
          }
        },
        error: () => { /* poll errors are silently retried on next tick */ },
      });
  }

  private _stopPolling(): void {
    this.pollSub?.unsubscribe();
    this.pollSub = null;
  }
}
