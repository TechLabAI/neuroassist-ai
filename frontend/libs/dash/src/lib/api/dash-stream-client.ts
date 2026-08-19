import { Injectable, inject, signal, computed } from '@angular/core';
import { Subject, Subscription } from 'rxjs';
import { DASH_NOTIFICATIONS_PORT } from './dash-notifications.port';
import { NotificationEnvelope } from '../dash.types';

// =============================================================================
// DashStreamClient — DASH real-time notification stream (DASH-FE-002)
// Manages EventSource connection to GET /panel/stream with:
//   - Exponential backoff reconnect (2s / 4s / … max 30s)
//   - Long-poll fallback via DashNotificationsPort.poll() after 3 failures
//   - Heartbeat handling: SSE `:heartbeat` comments produce empty data — ignored
//   - Server-initiated disconnect via `event: close` message
// Auth: Wave-0 — no custom headers (EventSource API limitation).
// TODO[DASH-005-AUTH]: coordinate with BE on cookie / query-param JWT for prod.
// =============================================================================

export type StreamStatus = 'disconnected' | 'connecting' | 'connected' | 'fallback';

export interface StreamError {
  readonly code: string;
  readonly message: string;
}

const MAX_RECONNECT_ATTEMPTS = 3;
const BASE_BACKOFF_MS = 2000;   // 2s initial delay
const MAX_BACKOFF_MS = 30000;   // 30s ceiling
const POLL_INTERVAL_MS = 30000; // 30s between long-polls

@Injectable({ providedIn: 'root' })
export class DashStreamClient {
  private readonly notificationsPort = inject(DASH_NOTIFICATIONS_PORT);

  // ---------------------------------------------------------------------------
  // Private writable signals
  // ---------------------------------------------------------------------------
  readonly #status         = signal<StreamStatus>('disconnected');
  readonly #error          = signal<StreamError | null>(null);
  readonly #fallbackActive = signal<boolean>(false);

  // ---------------------------------------------------------------------------
  // Public readonly signals (SPEC-04 §5 — stream contract)
  // ---------------------------------------------------------------------------
  readonly status        = this.#status.asReadonly();
  readonly isConnected   = computed(() => this.#status() === 'connected');
  readonly fallbackActive = this.#fallbackActive.asReadonly();
  readonly error         = this.#error.asReadonly();

  /**
   * Subject of notification envelopes.
   * NotificationsFacade subscribes via takeUntilDestroyed to accumulate items.
   * Emits both from SSE onmessage and from long-poll fallback responses.
   */
  readonly events$ = new Subject<NotificationEnvelope>();

  // ---------------------------------------------------------------------------
  // Private connection state
  // ---------------------------------------------------------------------------
  private eventSource: EventSource | null = null;
  private reconnectAttempts = 0;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private currentUrl = '';
  private pollSubscription: Subscription | null = null;
  private pollTimer: ReturnType<typeof setTimeout> | null = null;
  private pollCursor: string | undefined = undefined;

  // ---------------------------------------------------------------------------
  // Public lifecycle methods
  // ---------------------------------------------------------------------------

  /**
   * open(url) — Opens the SSE connection and starts automatic reconnect on error.
   * Idempotent: no-op if already connecting / connected / in fallback mode.
   */
  open(url: string): void {
    if (this.#status() !== 'disconnected') return;
    this.currentUrl = url;
    this.reconnectAttempts = 0;
    this.#error.set(null);
    this.#doConnect(url);
  }

  /**
   * close() — Closes SSE, cancels all reconnect timers and fallback polling.
   * Safe to call multiple times (idempotent).
   */
  close(): void {
    this.#clearReconnectTimer();
    this.#clearFallback();

    if (this.eventSource) {
      this.eventSource.close();
      this.eventSource = null;
    }

    this.reconnectAttempts = 0;
    this.currentUrl = '';
    this.#status.set('disconnected');
    this.#error.set(null);
  }

  // ---------------------------------------------------------------------------
  // Private — SSE connection
  // ---------------------------------------------------------------------------

  #doConnect(url: string): void {
    this.#status.set('connecting');

    // Guard: EventSource may be absent in SSR or very old browsers.
    if (typeof EventSource === 'undefined') {
      this.reconnectAttempts = MAX_RECONNECT_ATTEMPTS;
      this.#error.set({
        code: 'SSE_UNSUPPORTED',
        message: 'EventSource not available — activating long-poll fallback.',
      });
      this.#activateFallback();
      return;
    }

    // TODO[DASH-005-AUTH]: Wave-0 — EventSource does not support custom headers.
    // Coordinate with BE team on cookie or query-param JWT mechanism for production.
    this.eventSource = new EventSource(url);

    this.eventSource.onopen = () => {
      this.#status.set('connected');
      this.#error.set(null);
      this.reconnectAttempts = 0;
    };

    this.eventSource.onmessage = (event: MessageEvent) => {
      // SSE `:heartbeat` comment lines arrive as empty data — skip silently.
      if (!event.data) return;
      try {
        const envelope = JSON.parse(event.data as string) as NotificationEnvelope;
        this.events$.next(envelope);
      } catch {
        // Malformed JSON payload — ignore silently.
      }
    };

    // Handle server-initiated disconnect (`event: close`).
    this.eventSource.addEventListener('close', () => {
      this.close();
    });

    this.eventSource.onerror = () => {
      if (this.eventSource) {
        this.eventSource.close();
        this.eventSource = null;
      }

      this.reconnectAttempts++;

      if (this.reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
        this.#error.set({
          code: 'SSE_MAX_RETRIES',
          message: 'SSE connection failed after max retries — switching to long-poll fallback.',
        });
        this.#activateFallback();
        return;
      }

      // Exponential backoff: 2s → 4s → … capped at 30s.
      const delayMs = Math.min(
        BASE_BACKOFF_MS * Math.pow(2, this.reconnectAttempts - 1),
        MAX_BACKOFF_MS,
      );
      this.#status.set('disconnected');
      this.reconnectTimer = setTimeout(() => this.#doConnect(this.currentUrl), delayMs);
    };
  }

  // ---------------------------------------------------------------------------
  // Private — reconnect timer management
  // ---------------------------------------------------------------------------

  #clearReconnectTimer(): void {
    if (this.reconnectTimer !== null) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }

  // ---------------------------------------------------------------------------
  // Private — long-poll fallback
  // ---------------------------------------------------------------------------

  #activateFallback(): void {
    this.#status.set('fallback');
    this.#fallbackActive.set(true);
    this.#doPoll();
  }

  /**
   * Long-poll loop: calls port.poll() and schedules the next iteration.
   * The server holds the connection for `waitSeconds` (30s) before responding.
   * After each response, a POLL_INTERVAL_MS timer fires the next call.
   */
  #doPoll(): void {
    this.pollSubscription?.unsubscribe();
    this.pollSubscription = this.notificationsPort
      .poll(30, this.pollCursor)
      .subscribe({
        next: (result) => {
          this.pollCursor = result.cursor;
          result.notifications.forEach((n) => this.events$.next(n));
          // Schedule next poll (server-side long-poll controls effective cadence).
          this.pollTimer = setTimeout(() => {
            if (this.#fallbackActive()) {
              this.#doPoll();
            }
          }, POLL_INTERVAL_MS);
        },
        error: () => {
          // Poll failed — retry after interval.
          this.pollTimer = setTimeout(() => {
            if (this.#fallbackActive()) {
              this.#doPoll();
            }
          }, POLL_INTERVAL_MS);
        },
      });
  }

  #clearFallback(): void {
    if (this.pollSubscription) {
      this.pollSubscription.unsubscribe();
      this.pollSubscription = null;
    }
    if (this.pollTimer !== null) {
      clearTimeout(this.pollTimer);
      this.pollTimer = null;
    }
    this.pollCursor = undefined;
    this.#fallbackActive.set(false);
  }
}
