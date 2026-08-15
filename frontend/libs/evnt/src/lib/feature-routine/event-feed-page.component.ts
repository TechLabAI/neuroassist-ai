import {
  Component,
  ChangeDetectionStrategy,
  inject,
  computed,
  signal,
  OnInit,
} from '@angular/core';
import { Router } from '@angular/router';
import { EventDeliveryFacade } from '../evnt.facade';
import { EVNT_MODULE_DESCRIPTOR } from '../evnt.descriptor';
import { RoutineNoticeComponent } from './routine-notice/routine-notice.component';
import { EventListComponent } from './event-list/event-list.component';

// =============================================================================
// EventFeedPageComponent — SPEC-03 §3, SPEC-02 §3 (FE-EVNT-013)
// Route: /eventos (registered via EVNT_ROUTES)
//
// Smart component: reads facade signals, composes two presentational children.
//   • RoutineNoticeComponent — dismissible discreet notices (FLAG-02 gated)
//   • EventListComponent     — recent-events history list
//
// Emergency alarm is handled EXCLUSIVELY by AlarmOverlayComponent (shell outlet,
// ADR-005) — this page is never shown during an active alarm event.
//
// FLAG-02: routineNoticesEnabled suppresses discreet notices when 'off'.
//   The emergency alarm is unaffected (handled by the shell, not by this flag).
//   TODO[INT-FE-EVNT-005]: replace with FeatureFlagService when available.
//
// DegradedModeBanner: inline (DegradedModeBannerComponent from
//   @neuroassist/shared/ui-feedback is a Wave-0 stub with no content).
// =============================================================================

@Component({
  selector: 'evnt-event-feed-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RoutineNoticeComponent, EventListComponent],
  template: `
    <div class="mx-auto max-w-2xl px-4 py-8">

      <!-- Degraded banner (SPEC-04 §4 — inline; @neuroassist/shared/ui-feedback is Wave-0 stub) -->
      @if (deliveryMode() === 'degraded') {
        <div
          class="mb-4 flex items-center gap-2 rounded-lg bg-warn/10 px-4 py-3 text-sm text-warn-ink"
          role="alert"
          aria-live="polite"
          data-testid="degraded-banner">
          <span class="material-symbols-outlined text-[18px]" aria-hidden="true">signal_disconnected</span>
          <span>Modo degradado — recebendo eventos via polling.</span>
        </div>
      }

      <h1 class="mb-6 font-display text-xl font-bold text-ink">Notificações de rotina</h1>

      <!-- Loading skeleton -->
      @if (loading()) {
        <div class="flex justify-center py-12" aria-label="Carregando notificações">
          <span
            class="material-symbols-outlined animate-spin text-[32px] text-ink-faint"
            aria-hidden="true">
            progress_activity
          </span>
        </div>
      }

      <!-- Error state -->
      @if (error()) {
        <div class="rounded-lg bg-error/10 px-4 py-3 text-sm text-error-ink" role="alert">
          Erro ao carregar notificações. Tente novamente.
        </div>
      }

      <!-- Routine queue — FLAG-02: suppressed when routineNotices='off' -->
      @if (routineNoticesEnabled()) {
        <div role="list" aria-label="Fila de notificações de rotina">
          @for (evt of routineQueue(); track evt.eventoId) {
            <evnt-routine-notice
              [evento]="evt"
              (dismiss)="onDismiss($event)"
              (open)="onOpen($event)" />
          }
        </div>
      }

      <!-- Recent-events history list -->
      @if (recentEvents().length > 0) {
        <h2 class="mb-4 mt-8 font-display text-base font-semibold text-ink">
          Histórico recente
        </h2>
        <evnt-event-list [events]="recentEvents()" (open)="onOpen($event)" />
      }

      <!-- Empty state: no queue items, no recent events, not loading -->
      @if (empty()) {
        <div
          class="flex flex-col items-center py-16 text-center"
          data-testid="empty-state">
          <span
            class="material-symbols-outlined text-[56px] text-ink-faint"
            aria-hidden="true">
            notifications_off
          </span>
          <p class="mt-4 text-sm text-ink-soft">Nenhuma notificação de rotina no momento.</p>
        </div>
      }

    </div>
  `,
})
export class EventFeedPageComponent implements OnInit {
  private readonly facade = inject(EventDeliveryFacade);
  private readonly router = inject(Router);

  // ---------------------------------------------------------------------------
  // Facade signals — all reads proxied through signals (OnPush-safe)
  // ---------------------------------------------------------------------------

  readonly routineQueue = this.facade.routineQueue;
  readonly recentEvents = this.facade.recentEvents;
  readonly deliveryMode = this.facade.deliveryMode;
  readonly loading = this.facade.loading;
  readonly error = this.facade.error;

  // ---------------------------------------------------------------------------
  // FLAG-02: discreet notice visibility (SPEC-05 §3)
  // Protected (not private) so unit tests can mutate it via (component as any).
  // ---------------------------------------------------------------------------

  protected readonly routineNoticesEnabled = signal(
    EVNT_MODULE_DESCRIPTOR.featureFlags.routineNotices === 'on',
  );

  // ---------------------------------------------------------------------------
  // Derived state
  // ---------------------------------------------------------------------------

  /** True when there is nothing to display and no active loading. */
  readonly empty = computed(
    () =>
      !this.loading() &&
      this.routineQueue().length === 0 &&
      this.recentEvents().length === 0,
  );

  // ---------------------------------------------------------------------------
  // Lifecycle
  // ---------------------------------------------------------------------------

  ngOnInit(): void {
    // Load recent-events history (SPEC-03 §3).
    // Hydration of routineQueue was already done by evnt.bootstrap on app init.
    void this.facade.loadHistory({ page: 0, size: 20 });
  }

  // ---------------------------------------------------------------------------
  // Actions
  // ---------------------------------------------------------------------------

  protected onDismiss(eventoId: string): void {
    this.facade.dismiss(eventoId);
  }

  protected onOpen(eventoId: string): void {
    void this.router.navigate(['/eventos', eventoId]);
  }
}
