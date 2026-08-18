import {
  Component,
  ChangeDetectionStrategy,
  inject,
  computed,
  OnInit,
} from '@angular/core';
import { Router } from '@angular/router';
import { EventDeliveryFacade } from '../evnt.facade';
import { EvntFeatureFlagsService } from '../api/evnt-feature-flags.service';
import { RoutineNoticeComponent } from './routine-notice/routine-notice.component';
import { EventListComponent } from './event-list/event-list.component';
import { EvntFeedSkeletonComponent } from '../ui/feed-skeleton/evnt-feed-skeleton.component';
import { EvntDegradedModeBannerComponent } from '../ui/degraded-banner/evnt-degraded-mode-banner.component';

// =============================================================================
// EventFeedPageComponent — SPEC-03 §3, SPEC-02 §3 (FE-EVNT-013)
// Route: /eventos (registered via EVNT_ROUTES)
//
// Smart component: reads facade signals, composes two presentational children.
//   • EvntFeedSkeletonComponent     — loading placeholder (3 rows, P7/FE-EVNT-022)
//   • EvntDegradedModeBannerComponent — degraded-mode status (P7/FE-EVNT-024)
//   • RoutineNoticeComponent        — dismissible discreet notices (FLAG-02 gated)
//   • EventListComponent            — recent-events history list
//
// Emergency alarm is handled EXCLUSIVELY by AlarmOverlayComponent (shell outlet,
// ADR-005) — this page is never shown during an active alarm event.
//
// FLAG-02 (FE-EVNT-024): routineNoticesEnabled reads from EvntFeatureFlagsService.
//   Off → suppress discreet routine section; emergency alarm unaffected.
//
// ERR-03 (FE-EVNT-024): polling errors are silent (no error signal propagation).
//   Handled in EventDeliveryFacade._startPolling() — no change needed in this component.
//
// Loading states (FE-EVNT-022):
//   loading=true  → EvntFeedSkeletonComponent (hides content; breadcrumb always visible)
//   loading=false, empty=true → empty state message
//   loading=false, empty=false → full content
//
// Degraded banner (FE-EVNT-024): EvntDegradedModeBannerComponent shown above the
//   breadcrumb when deliveryMode='degraded'. Receives pollIntervalSec computed signal.
// =============================================================================

@Component({
  selector: 'evnt-event-feed-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RoutineNoticeComponent,
    EventListComponent,
    EvntFeedSkeletonComponent,
    EvntDegradedModeBannerComponent,
  ],
  template: `
    <div class="mx-auto max-w-2xl px-4 py-8">

      <!-- Degraded banner (SPEC-04 §4, FE-EVNT-024) — always above breadcrumb -->
      @if (deliveryMode() === 'degraded') {
        <evnt-degraded-mode-banner [pollIntervalSec]="pollIntervalSec()" />
      }

      <!-- Page breadcrumb — ALWAYS visible regardless of loading/empty/populated state (FE-EVNT-022) -->
      <h1 class="mb-6 font-display text-xl font-bold text-ink page-title">Eventos</h1>

      <!-- Loading skeleton (FE-EVNT-022): replaces content block while loading -->
      @if (loading()) {
        <evnt-feed-skeleton />
      } @else {

        <!-- Error state -->
        @if (error()) {
          <div class="rounded-lg bg-error/10 px-4 py-3 text-sm text-error-ink" role="alert">
            Erro ao carregar notificações. Tente novamente.
          </div>
        }

        <!-- Routine queue — FLAG-02 (FE-EVNT-024): suppressed when routineNotices='off' -->
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

        <!-- Empty state (FE-EVNT-022): no queue, no recent events, not loading -->
        @if (empty()) {
          <div
            class="flex flex-col items-center py-16 text-center"
            data-testid="empty-state">
            <span
              class="material-symbols-outlined text-[56px] text-ink-faint"
              aria-hidden="true">
              notifications_off
            </span>
            <p class="mt-4 text-sm text-ink-soft">Sem notificações pendentes. Aguardando eventos…</p>
          </div>
        }

      }
    </div>
  `,
})
export class EventFeedPageComponent implements OnInit {
  private readonly facade = inject(EventDeliveryFacade);
  private readonly router = inject(Router);
  private readonly flagService = inject(EvntFeatureFlagsService);

  // ---------------------------------------------------------------------------
  // Facade signals — all reads proxied through signals (OnPush-safe)
  // ---------------------------------------------------------------------------

  readonly routineQueue = this.facade.routineQueue;
  readonly recentEvents = this.facade.recentEvents;
  readonly deliveryMode = this.facade.deliveryMode;
  readonly loading = this.facade.loading;
  readonly error = this.facade.error;

  // ---------------------------------------------------------------------------
  // FLAG-02: discreet notice visibility (FE-EVNT-024, SPEC-05 §3)
  // Reads from EvntFeatureFlagsService — respects runtime overrides via
  // EVNT_FEATURE_FLAGS_OVERRIDE token (INT-FE-EVNT-005).
  // ---------------------------------------------------------------------------

  readonly routineNoticesEnabled = computed(() =>
    this.flagService.areRoutineNoticesEnabled(),
  );

  // ---------------------------------------------------------------------------
  // Degraded banner — poll interval (FE-EVNT-024)
  // Converts pollIntervalMs (feature flag, default 15000) to seconds for display.
  // ---------------------------------------------------------------------------

  readonly pollIntervalSec = computed(() =>
    Math.round(this.flagService.getPollIntervalMs() / 1000),
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
