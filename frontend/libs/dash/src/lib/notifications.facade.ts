import { Injectable, inject, signal, computed, DestroyRef } from '@angular/core';
import { lastValueFrom } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DASH_NOTIFICATIONS_PORT } from './api/dash-notifications.port';
import {
  NotificationEnvelope,
  NotificationsScreenVm,
  AppError,
  ScreenStatus,
} from './dash.types';

// =============================================================================
// NotificationsFacade — SPEC-05 §3/§4 (signals-based)
// Aggregates GET /panel/notifications/unacknowledged for NotificationCenterComponent.
// acknowledge() is write-through to EVNT (dash-service proxies; DASH stores nothing).
// Components interact ONLY with this facade; never with ports/adapters directly.
// TODO[INT-FE-DASH-005]: connect to real HTTP adapter once dash-service is deployed.
// =============================================================================

function mapError(err: unknown, fallback: string): AppError {
  if (err instanceof Object && 'status' in err) {
    const e = err as { status: number; message?: string };
    return { code: `HTTP_${e.status}`, message: e.message ?? fallback, status: e.status };
  }
  if (err instanceof Error) {
    return { code: 'UNKNOWN', message: err.message };
  }
  return { code: 'UNKNOWN', message: fallback };
}

@Injectable({ providedIn: 'root' })
export class NotificationsFacade {
  private readonly notificationsPort = inject(DASH_NOTIFICATIONS_PORT);
  private readonly destroyRef = inject(DestroyRef);

  // ---------------------------------------------------------------------------
  // Private writable signals
  // ---------------------------------------------------------------------------
  readonly #status        = signal<ScreenStatus>('idle');
  readonly #notifications = signal<NotificationEnvelope[]>([]);
  readonly #error         = signal<AppError | null>(null);

  // ---------------------------------------------------------------------------
  // Public readonly signals
  // ---------------------------------------------------------------------------
  readonly status        = this.#status.asReadonly();
  readonly notifications = this.#notifications.asReadonly();
  readonly error         = this.#error.asReadonly();
  readonly loading       = computed(() => this.#status() === 'loading');
  readonly count         = computed(() => this.#notifications().length);

  /** Assembled screen VM. */
  readonly screenVm = computed<NotificationsScreenVm>(() => ({
    status: this.#status(),
    notifications: this.#notifications(),
    error: this.#error(),
  }));

  // ---------------------------------------------------------------------------
  // Public methods
  // ---------------------------------------------------------------------------

  /** load() — on-connect catch-up; called from NotificationCenterComponent.ngOnInit(). */
  async load(): Promise<void> {
    this.#status.set('loading');
    this.#error.set(null);

    try {
      const result = await lastValueFrom(
        this.notificationsPort.listUnacknowledged().pipe(
          takeUntilDestroyed(this.destroyRef),
        ),
      );
      this.#notifications.set(result.notifications);
      this.#status.set(result.notifications.length === 0 ? 'empty' : 'loaded');
    } catch (err) {
      this.#error.set(mapError(err, 'Erro ao carregar notificações'));
      this.#status.set('error');
    }
  }

  /**
   * acknowledge(dispatchId) — write-through to EVNT via DASH.
   * Non-optimistic: evicts from local list only after backend confirms.
   */
  async acknowledge(dispatchId: string): Promise<void> {
    try {
      await lastValueFrom(this.notificationsPort.acknowledge(dispatchId));
      // Evict after confirmed ack (non-optimistic).
      this.#notifications.update((list) =>
        list.filter((n) => n.dispatchId !== dispatchId),
      );
      if (this.#notifications().length === 0) {
        this.#status.set('empty');
      }
    } catch (err) {
      // Non-fatal: log and keep the notification visible for retry.
      console.error('[NotificationsFacade] acknowledge failed', err);
    }
  }

  /** retry() — retry last load on error. */
  async retry(): Promise<void> {
    await this.load();
  }
}
