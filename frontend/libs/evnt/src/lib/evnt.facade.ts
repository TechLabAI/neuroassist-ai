import { Injectable, inject, signal, computed, DestroyRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { lastValueFrom, Subscription, timer, switchMap } from 'rxjs';
import { AppError } from '@neuroassist/shared/error-model';
import { EVNT_CLIENT_PORT } from './api/evnt-client.port';
import type { EventoSummaryDto } from './api/evnt-client.port';
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
// =============================================================================

/** Patient name resolved from tenant context.
 * TODO[FE-EVNT-012]: inject from @neuroassist/shared/empresa-context (patient roster).
 * Hardcoded for MVP prototype parity. */
const PATIENT_NAME_PLACEHOLDER = 'Márcio';

/** Default poll interval when live stream is unavailable (feature flag: EVNT_POLL_INTERVAL_MS). */
const DEFAULT_POLL_INTERVAL_MS = 15_000;

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

  // ---------------------------------------------------------------------------
  // Public read-only signals (SPEC-03 §5)
  // ---------------------------------------------------------------------------

  readonly activeEvent = this._activeEvent.asReadonly();
  readonly routineQueue = this._routineQueue.asReadonly();
  readonly recentEvents = this._recentEvents.asReadonly();
  readonly deliveryMode = this._deliveryMode.asReadonly();
  readonly loading = this._loading.asReadonly();
  readonly error = this._error.asReadonly();

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

  /** Open SSE live channel. On error falls back to polling (SPEC-04 §3/§4). */
  startLiveChannel(): void {
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
          this._deliveryMode.set('degraded');
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
    try {
      await lastValueFrom(this.port.confirm(eventoId, verdict));
      // Only clear active event after confirmed 200 (safety invariant)
      this._activeEvent.set(null);
    } catch (err: unknown) {
      // 409 feedback-terminal → treat as success (already confirmed by someone else)
      const isTerminal =
        err instanceof Object &&
        'status' in err &&
        (err as { status: number }).status === 409;
      if (isTerminal) {
        this._alreadyConfirmed.set(true);
        this._activeEvent.set(null);
      } else {
        this._confirmError.set(toAppError(err));
      }
    } finally {
      this._confirming.set(false);
    }
  }

  /** Client-only optimistic dismiss from routine queue (no server call). SPEC-04 §5. */
  dismiss(eventoId: string): void {
    this._routineQueue.update((q) => q.filter((e) => e.eventoId !== eventoId));
  }

  /** Load event detail for the detail page. */
  async loadDetail(eventoId: string): Promise<EventoVm | null> {
    try {
      const dto = await lastValueFrom(this.port.get(eventoId));
      const summary: EventoVm = {
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
      };
      return summary;
    } catch {
      return null;
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
    this.pollSub = timer(0, DEFAULT_POLL_INTERVAL_MS)
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
