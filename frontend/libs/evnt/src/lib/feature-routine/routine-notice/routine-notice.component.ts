import {
  Component,
  ChangeDetectionStrategy,
  Input,
  output,
} from '@angular/core';
import { EventoVm } from '../../evnt.types';
import { UrgencyBadgeComponent } from '../../ui/urgency-badge/urgency-badge.component';
import { EventTimestampComponent } from '../../ui/event-timestamp/event-timestamp.component';

// =============================================================================
// RoutineNoticeComponent — SPEC-02 §2, FE-EVNT-014
// Presentational component for a single routine event notice.
// Renders a compact card: urgency badge + commandLabel + timestamp + action btns.
// Dismiss is optimistic (client-only, FLAG-02). Emergency alarm unaffected (ADR-005).
// Zero DI — all data via @Input(), actions via output().
//
// NOTE: @Input() is used instead of the input() signal API because ts-jest's
// JIT compilation of components with child imports does not support setInput()
// for signal inputs (P2 ts-jest limitation extended to P3).
// =============================================================================

/** Placeholder EventoVm used as the @Input default value. */
const PLACEHOLDER_EVENTO: EventoVm = {
  eventoId: '',
  urgencia: 'rotina',
  tier: 'discreet',
  commandLabel: '',
  patientName: '',
  recognizedAt: new Date(0).toISOString(),
  displayTime: 'às 00h00',
  feedback: 'nao_avaliado',
  confirmadoPor: null,
  confirmadoAt: null,
};

@Component({
  selector: 'evnt-routine-notice',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [UrgencyBadgeComponent, EventTimestampComponent],
  template: `
    <div
      class="mb-3 flex items-start gap-3 rounded-xl border border-line bg-surface px-4 py-3 shadow-sm"
      role="listitem"
      data-testid="routine-notice">

      <!-- Urgency badge -->
      <div class="mt-1 flex-shrink-0">
        <evnt-urgency-badge [urgencia]="evento.urgencia" />
      </div>

      <!-- Content -->
      <div class="min-w-0 flex-1">
        <p class="text-sm font-semibold text-ink">"{{ evento.commandLabel }}"</p>
        <p class="mt-0.5 flex items-center gap-1 text-xs text-ink-soft">
          <span>{{ evento.patientName }}</span>
          <span aria-hidden="true">·</span>
          <evnt-event-timestamp [recognizedAt]="evento.recognizedAt" />
        </p>
      </div>

      <!-- Actions -->
      <div class="flex shrink-0 items-center gap-1">
        <button
          type="button"
          (click)="onOpen()"
          class="rounded-lg px-3 py-1.5 text-xs font-medium text-brand transition-colors hover:bg-brand/10"
          [attr.aria-label]="'Ver evento: ' + evento.commandLabel">
          Ver
        </button>
        <button
          type="button"
          (click)="onDismiss()"
          class="rounded-lg px-2 py-1.5 text-xs text-ink-soft transition-colors hover:bg-surface-2"
          [attr.aria-label]="'Dispensar notificação: ' + evento.commandLabel">
          <span class="material-symbols-outlined text-[16px]" aria-hidden="true">close</span>
        </button>
      </div>

    </div>
  `,
})
export class RoutineNoticeComponent {
  /** Routine event to display. */
  @Input() evento: EventoVm = PLACEHOLDER_EVENTO;

  /** Emits the eventoId when the user dismisses the notice (optimistic, client-only). */
  readonly dismiss = output<string>();

  /** Emits the eventoId when the user clicks "Ver" to navigate to the detail page. */
  readonly open = output<string>();

  protected onDismiss(): void {
    this.dismiss.emit(this.evento.eventoId);
  }

  protected onOpen(): void {
    this.open.emit(this.evento.eventoId);
  }
}
