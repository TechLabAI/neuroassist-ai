import {
  Component,
  ChangeDetectionStrategy,
  Input,
  output,
} from '@angular/core';
import { Feedback } from '../../evnt.types';

// =============================================================================
// FeedbackVerdictControl — SPEC-02 §2, FE-EVNT-017
// Presentational: two action buttons for the caregiver to register event verdict.
// Emits (verdict) → EventDetailPageComponent.onVerdict() → facade.confirm().
// CONCUR-01: both buttons are disabled while [disabled]="true" (confirm in flight).
// Visibility is controlled by the parent (EventDetailPage) via canConfirm computed.
// Zero DI — all state via @Input(), actions via output().
//
// NOTE: @Input() used instead of input() signal API for ts-jest consistency (P3 convention).
// =============================================================================

@Component({
  selector: 'evnt-feedback-verdict-control',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex gap-3" role="group" aria-label="Registrar veredicto do evento">
      <button
        type="button"
        (click)="onVerdict('real')"
        [disabled]="disabled"
        class="rounded-lg bg-brand px-4 py-2 text-sm font-semibold text-brand-ink transition-colors hover:bg-brand-strong disabled:cursor-not-allowed disabled:opacity-50"
        aria-label="Marcar como evento real">
        Real
      </button>
      <button
        type="button"
        (click)="onVerdict('falso_positivo')"
        [disabled]="disabled"
        class="rounded-lg border border-line px-4 py-2 text-sm font-semibold text-ink transition-colors hover:bg-surface-2 disabled:cursor-not-allowed disabled:opacity-50"
        aria-label="Marcar como falso positivo">
        Falso positivo
      </button>
    </div>
  `,
})
export class FeedbackVerdictControl {
  /** Current feedback status — informational; visibility is decided by the parent. */
  @Input() feedback: Feedback = 'nao_avaliado';

  /** Disables both buttons while a confirmation POST is in flight (CONCUR-01). */
  @Input() disabled = false;

  /** Emits the selected verdict ('real' or 'falso_positivo') when a button is clicked. */
  readonly verdict = output<'real' | 'falso_positivo'>();

  protected onVerdict(v: 'real' | 'falso_positivo'): void {
    this.verdict.emit(v);
  }
}
