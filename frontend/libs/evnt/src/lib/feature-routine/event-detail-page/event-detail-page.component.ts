import {
  Component,
  ChangeDetectionStrategy,
  inject,
  computed,
  OnInit,
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { EventDeliveryFacade } from '../../evnt.facade';
import { UrgencyBadgeComponent } from '../../ui/urgency-badge/urgency-badge.component';
import { EventTimestampComponent } from '../../ui/event-timestamp/event-timestamp.component';
import { FeedbackVerdictControl } from '../feedback-verdict-control/feedback-verdict-control.component';
import { ConfirmationTrailComponent } from '../confirmation-trail/confirmation-trail.component';
import { ConfirmationVerdict } from '../../evnt.types';

// =============================================================================
// EventDetailPageComponent — SPEC-03 §4, SPEC-02 §2 (FE-EVNT-016)
// Route: /eventos/:eventoId (registered via EVNT_ROUTES)
//
// Smart component: reads facade.selectedEvent() signal, composes three presentational
// children (FeedbackVerdictControl, ConfirmationTrailComponent, plus the two ui leaves).
//
// CONCUR-01: FeedbackVerdictControl [disabled]="confirming()" — same signal used by
//   AlarmOverlayComponent. A confirmation in flight blocks all verdict buttons.
//
// canConfirm: feedback === 'nao_avaliado'.
//   TODO[FE-EVNT-020/P6]: also check hasPermission('evnt.confirm') via AccessDecision.
//
// NAV-02: breadcrumb rendered inline ("<Eventos > {label} ({displayTime})").
//   Will be replaced by global breadcrumb infra once available.
//
// 404 treatment: if loadDetail resolves with no event (null), navigate to /eventos.
// =============================================================================

@Component({
  selector: 'evnt-event-detail-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    UrgencyBadgeComponent,
    EventTimestampComponent,
    FeedbackVerdictControl,
    ConfirmationTrailComponent,
  ],
  template: `
    <div class="mx-auto max-w-2xl px-4 py-8">

      <!-- Breadcrumb (NAV-02 — inline; global breadcrumb infra is P6) -->
      <nav aria-label="Navegação" class="mb-6 flex items-center gap-2 text-sm text-ink-soft">
        <a
          href="/eventos"
          class="transition-colors hover:text-ink"
          aria-label="Voltar para lista de eventos">
          Eventos
        </a>
        @if (selectedEvent()) {
          <span aria-hidden="true" class="select-none">›</span>
          <span class="truncate text-ink" aria-current="page">
            {{ selectedEvent()!.commandLabel }}
            ({{ selectedEvent()!.displayTime }})
          </span>
        }
      </nav>

      <!-- Loading state -->
      @if (loading()) {
        <div
          class="flex justify-center py-12"
          role="status"
          aria-label="Carregando detalhe do evento">
          <span
            class="material-symbols-outlined animate-spin text-[32px] text-ink-faint"
            aria-hidden="true">
            progress_activity
          </span>
        </div>
      }

      <!-- Not-found / loading-complete-but-null state -->
      @if (!loading() && !selectedEvent()) {
        <div
          class="flex flex-col items-center py-16 text-center"
          data-testid="not-found"
          role="alert">
          <span
            class="material-symbols-outlined text-[48px] text-ink-faint"
            aria-hidden="true">
            event_busy
          </span>
          <p class="mt-4 text-sm text-ink-soft">Evento não encontrado.</p>
        </div>
      }

      <!-- Detail view — rendered once event is loaded -->
      @if (!loading() && selectedEvent()) {
        <div class="space-y-6">

          <!-- Header row: urgency badge + timestamp -->
          <div class="flex items-center gap-3">
            <evnt-urgency-badge [urgencia]="selectedEvent()!.urgencia" />
            <evnt-event-timestamp [recognizedAt]="selectedEvent()!.recognizedAt" />
          </div>

          <!-- Command label -->
          <h1 class="font-display text-xl font-bold text-ink">
            "{{ selectedEvent()!.commandLabel }}"
          </h1>

          <!-- Patient name -->
          <p class="text-sm text-ink-soft">
            Paciente: {{ selectedEvent()!.patientName }}
          </p>

          <!-- Verdict section — only when canConfirm (feedback === 'nao_avaliado') -->
          @if (canConfirm()) {
            <div class="border-t border-surface-border pt-4">
              <p class="mb-3 text-sm font-medium text-ink">
                Como você avalia este evento?
              </p>
              <evnt-feedback-verdict-control
                [feedback]="selectedEvent()!.feedback"
                [disabled]="confirming()"
                (verdict)="onVerdict($event)"
                data-testid="feedback-verdict-control" />
            </div>
          }

          <!-- Confirmation trail — shows after a verdict is recorded -->
          <evnt-confirmation-trail
            [confirmadoPor]="selectedEvent()!.confirmadoPor"
            [confirmadoAt]="selectedEvent()!.confirmadoAt" />

        </div>
      }

    </div>
  `,
})
export class EventDetailPageComponent implements OnInit {
  private readonly facade = inject(EventDeliveryFacade);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  // ---------------------------------------------------------------------------
  // Facade signals — proxied (OnPush-safe)
  // ---------------------------------------------------------------------------

  readonly selectedEvent = this.facade.selectedEvent;
  readonly loading = this.facade.loading;

  /** CONCUR-01: confirmation POST in flight — disables FeedbackVerdictControl buttons. */
  readonly confirming = this.facade.confirming;

  // ---------------------------------------------------------------------------
  // Derived state
  // ---------------------------------------------------------------------------

  /** canConfirm: feedback pending evaluation.
   * TODO[FE-EVNT-020/P6]: also verify hasPermission('evnt.confirm') via AccessDecision. */
  readonly canConfirm = computed(() => {
    const event = this.selectedEvent();
    return event !== null && event.feedback === 'nao_avaliado';
  });

  // ---------------------------------------------------------------------------
  // Lifecycle
  // ---------------------------------------------------------------------------

  private eventoId = '';

  ngOnInit(): void {
    this.eventoId = this.route.snapshot.paramMap.get('eventoId') ?? '';
    // Non-optimistic load: only react after server response.
    void this.facade.loadDetail(this.eventoId).then(() => {
      // 404 redirect: loadDetail sets selectedEvent to null on error/not-found.
      if (!this.facade.selectedEvent()) {
        void this.router.navigate(['/eventos']);
      }
    });
  }

  // ---------------------------------------------------------------------------
  // Actions
  // ---------------------------------------------------------------------------

  protected onVerdict(verdict: ConfirmationVerdict): void {
    void this.facade.confirm(this.eventoId, verdict);
  }
}
