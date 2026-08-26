import {
  Component,
  ChangeDetectionStrategy,
  Input,
  output,
} from '@angular/core';
import { EventoVm, Feedback } from '../../evnt.types';
import { UrgencyBadgeComponent } from '../../ui/urgency-badge/urgency-badge.component';
import { EventTimestampComponent } from '../../ui/event-timestamp/event-timestamp.component';

// =============================================================================
// EventListComponent — SPEC-02 §2, FE-EVNT-015
// Presentational list of recent events (history).
// Renders each EventoVm as a clickable card; emits (open) with eventoId.
// Displays an inline empty state when events.length === 0.
// Zero DI — all data via @Input(), navigation via output().
//
// NOTE: @Input() is used instead of the input() signal API because ts-jest's
// JIT compilation of components with child imports does not support setInput()
// for signal inputs (P2 ts-jest limitation extended to P3).
// =============================================================================

// Rótulos pt-BR do veredito. A cuidadora nunca deve ler o identificador do enum
// ("Nao_avaliado" aparecia cru na tela, com underscore e capitalize do CSS).
const FEEDBACK_LABELS: Record<Feedback, string> = {
  nao_avaliado: 'Sem revisão',
  real: 'Confirmado real',
  falso_positivo: 'Falso positivo',
};

@Component({
  selector: 'evnt-event-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [UrgencyBadgeComponent, EventTimestampComponent],
  template: `
    @if (events.length === 0) {
      <p class="py-6 text-center text-sm text-ink-soft">Sem eventos recentes.</p>
    }

    @for (evt of events; track evt.eventoId) {
      <button
        type="button"
        (click)="onOpen(evt.eventoId)"
        class="mb-2 flex w-full items-start gap-3 rounded-xl border border-line bg-surface px-4 py-3 text-left shadow-sm transition-colors hover:bg-surface-2"
        [attr.aria-label]="'Ver detalhe de: ' + evt.commandLabel">

        <!-- Urgency badge -->
        <div class="mt-1 flex-shrink-0">
          <evnt-urgency-badge [urgencia]="evt.urgencia" />
        </div>

        <!-- Event details -->
        <div class="min-w-0 flex-1">
          <p class="text-sm font-semibold text-ink">"{{ evt.commandLabel }}"</p>
          <p class="mt-0.5 flex items-center gap-1 text-xs text-ink-soft">
            <span>{{ evt.patientName }}</span>
            <span aria-hidden="true">·</span>
            <evnt-event-timestamp [recognizedAt]="evt.recognizedAt" />
          </p>
          <span class="mt-1 inline-block text-xs text-ink-faint" data-testid="event-feedback">
            {{ feedbackLabel(evt.feedback) }}
          </span>
        </div>

        <!-- Chevron -->
        <span class="mt-1 flex-shrink-0 text-ink-faint" aria-hidden="true">
          <span class="material-symbols-outlined text-[18px]">chevron_right</span>
        </span>

      </button>
    }
  `,
})
export class EventListComponent {
  /** Ordered list of recent events to display. Defaults to empty array. */
  @Input() events: readonly EventoVm[] = [];

  /** Rótulo humano do veredito — nunca o valor cru do enum. */
  protected feedbackLabel(feedback: Feedback): string {
    return FEEDBACK_LABELS[feedback] ?? 'Sem revisão';
  }

  /** Emits the eventoId when a card is clicked (navigate to detail page). */
  readonly open = output<string>();

  protected onOpen(eventoId: string): void {
    this.open.emit(eventoId);
  }
}
